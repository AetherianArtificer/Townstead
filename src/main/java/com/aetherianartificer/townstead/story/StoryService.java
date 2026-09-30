package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.story.goal.Goal;
import com.aetherianartificer.townstead.story.goal.Goals;
import com.aetherianartificer.townstead.story.goal.GoalContext;
import com.aetherianartificer.townstead.story.net.StoryC2SPayload;
import com.aetherianartificer.townstead.story.net.StoryQuestSyncS2CPayload;
import com.aetherianartificer.townstead.story.net.StoryS2CPayload;
import com.aetherianartificer.townstead.switchboard.Systems;
import com.bladecoder.ink.runtime.Story;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of stories: which villager offers which story, the open conversations, and each
 * player's quests. Counter goals advance on Townstead events; state goals are re-read on a slow
 * timer and after events that can change them. Everything runs on the server thread.
 */
public final class StoryService {
    private StoryService() {}

    private static final double TALK_RANGE_SQ = 10.0 * 10.0;
    private static final int REFRESH_TICKS = 100;

    private static final Map<UUID, PlayerStories> PLAYERS = new HashMap<>();
    private static final Map<UUID, StorySession> SESSIONS = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static boolean subscribed;
    private static final Set<Class<?>> SUBSCRIBED = new HashSet<>();
    private static boolean recheckSoon;
    private static @Nullable MinecraftServer server;
    private static int ticks;

    /** Called once at mod construction. Event subscriptions follow the loaded goals, see {@link #onReload}. */
    public static void init() {
        subscribed = true;
    }

    /** Listens for every event a loaded goal counts. A subscription stays once made; it is cheap. */
    /**
     * Every event goal in a story's own goals, by name. These count for each player from the
     * moment they meet the villager, quest or not, so Ink can ask {@code count("collapsed")}.
     */
    private static volatile Map<ResourceLocation, Map<String, Goal>> WATCHED = Map.of();

    private static void subscribeGoalEvents() {
        Map<ResourceLocation, Map<String, Goal>> watched = new HashMap<>();
        for (StoryDefinition story : Stories.all().values()) {
            for (String name : story.goals().keySet()) {
                Goal goal = Goals.resolve(name, story.id().getNamespace(), story.goals()).goal();
                if (goal == null || !goal.isCounter()) continue;
                watched.computeIfAbsent(story.id(), k -> new HashMap<>()).put(name, goal);
                if (SUBSCRIBED.add(goal.event())) subscribe(goal.event(), StoryService::onCountedEvent);
            }
        }
        WATCHED = Map.copyOf(watched);
        for (StoryDefinition story : Stories.all().values()) {
            for (StoryDefinition.Quest quest : story.quests().values()) {
                for (Goal goal : quest.goals()) {
                    Class<?> type = goal.event();
                    if (type != null && SUBSCRIBED.add(type)) subscribe(type, StoryService::onCountedEvent);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void subscribe(Class<?> type, java.util.function.Consumer<TownsteadEvent> listener) {
        TownsteadApiV1.get().events().subscribe((Class<TownsteadEvent>) type, listener);
    }

    static void onReload() {
        if (subscribed) subscribeGoalEvents();
        for (StorySession session : new ArrayList<>(SESSIONS.values())) {
            session.finish();
            send(session.player, new StoryS2CPayload(session.villager.getId(), StoryS2CPayload.END, "", List.of(), false));
            save(session.player);
        }
        SESSIONS.clear();
        recheckSoon = true;
    }

    // ---- player state ----

    static PlayerStories stories(ServerPlayer player) {
        return PLAYERS.computeIfAbsent(player.getUUID(), id -> PlayerStories.load(player));
    }

    private static void save(ServerPlayer player) {
        stories(player).save(player);
    }

    public static void onLogin(ServerPlayer player) {
        PLAYERS.remove(player.getUUID());
        sync(player);
    }

    public static void onLogout(ServerPlayer player) {
        StorySession session = SESSIONS.remove(player.getUUID());
        if (session != null) session.finish();
        if (PLAYERS.containsKey(player.getUUID())) save(player);
        PLAYERS.remove(player.getUUID());
        DIRTY.remove(player.getUUID());
    }

    /** Drops a player's state for one story, or all of them. Returns whether anything was removed. */
    public static boolean reset(ServerPlayer player, @Nullable ResourceLocation story) {
        StorySession session = SESSIONS.remove(player.getUUID());
        if (session != null) session.finish();
        PlayerStories stories = stories(player);
        boolean removed;
        if (story == null) {
            removed = !stories.entries().isEmpty();
            stories.entries().clear();
        } else {
            removed = stories.remove(story);
        }
        save(player);
        sync(player);
        return removed;
    }

    // ---- network ----

    public static void handle(ServerPlayer player, StoryC2SPayload payload) {
        if (payload.action() == StoryC2SPayload.CLOSE) {
            closeSession(player);
            return;
        }
        if (payload.action() == StoryC2SPayload.OFFER) {
            VillagerEntityMCA opened = villager(player, payload.villagerId());
            if (opened != null) com.aetherianartificer.townstead.persona.PersonaService.knows(player, opened);
        }
        if (!Systems.on(Systems.STORIES)) {
            if (payload.action() == StoryC2SPayload.OFFER) offer(player, payload.villagerId(), null);
            return;
        }
        VillagerEntityMCA villager = villager(player, payload.villagerId());
        switch (payload.action()) {
            case StoryC2SPayload.OFFER -> offer(player, payload.villagerId(),
                    villager == null ? null : storyFor(player, villager));
            case StoryC2SPayload.TALK -> {
                if (villager != null) talk(player, villager);
            }
            case StoryC2SPayload.NEXT -> {
                StorySession session = SESSIONS.get(player.getUUID());
                if (session != null && session.villager == villager) session.next();
            }
            case StoryC2SPayload.CHOOSE -> {
                StorySession session = SESSIONS.get(player.getUUID());
                if (session != null && session.villager == villager) session.choose(payload.index());
            }
            default -> {}
        }
    }

    private static @Nullable VillagerEntityMCA villager(ServerPlayer player, int entityId) {
        if (player.serverLevel().getEntity(entityId) instanceof VillagerEntityMCA villager
                && villager.isAlive() && villager.distanceToSqr(player) <= TALK_RANGE_SQ) {
            return villager;
        }
        return null;
    }

    private static void offer(ServerPlayer player, int villagerId, @Nullable StoryDefinition story) {
        String label = "";
        if (story != null) {
            VillagerEntityMCA villager = villager(player, villagerId);
            label = villager == null ? story.label() : labelFor(player, villager, story);
        }
        VillagerEntityMCA target = villager(player, villagerId);
        boolean persona = target != null
                && com.aetherianartificer.townstead.persona.PersonaInstances.get(player.server).isPersona(target.getUUID());
        send(player, new StoryS2CPayload(villagerId, persona ? StoryS2CPayload.OFFER_PERSONA : StoryS2CPayload.OFFER,
                label, List.of(), story != null));
    }

    /** The villager's story for this player, unless another villager is already telling it to them. */
    private static @Nullable StoryDefinition storyFor(ServerPlayer player, VillagerEntityMCA villager) {
        StoryDefinition story = Stories.forVillager(villager, player);
        if (story == null) return null;
        PlayerStories.Entry entry = stories(player).get(PlayerStories.key(story, villager.getUUID()));
        return entry == null || entry.villager.equals(villager.getUUID()) ? story : null;
    }

    private static String labelFor(ServerPlayer player, VillagerEntityMCA villager, StoryDefinition story) {
        PlayerStories.Entry entry = stories(player).get(PlayerStories.key(story, villager.getUUID()));
        if ((entry == null || entry.villager.equals(villager.getUUID())) && SESSIONS.get(player.getUUID()) == null) {
            String name = StorySession.displayName(villager);
            PlayerStories.Entry forMenu = entry != null ? entry
                    : new PlayerStories.Entry(story.id(), villager.getUUID(), name);
            try {
                refresh(player.server, player.getUUID(), forMenu, story, villager);
                String fromInk = StorySession.open(player, villager, story, forMenu).menuLabel();
                if (fromInk != null) return fromInk;
            } catch (Exception e) {
                com.aetherianartificer.townstead.Townstead.LOGGER.warn("Story {} menu failed: {}", story.id(), e.getMessage());
            }
        }
        if (entry != null && entry.villager.equals(villager.getUUID())) {
            for (PlayerStories.QuestRecord record : entry.quests.values()) {
                StoryDefinition.Quest quest = story.quests().get(record.knot);
                if (quest != null && quest.label() != null && record.state != PlayerStories.QuestState.COMPLETE) {
                    return quest.label();
                }
            }
        }
        return story.label();
    }

    private static void talk(ServerPlayer player, VillagerEntityMCA villager) {
        talk(player, villager, null);
    }

    /**
     * Opens the villager's story at a knot, as when a Persona answers a gift. False when stories
     * are off, the villager has no story, or it could not start.
     */
    public static boolean play(ServerPlayer player, VillagerEntityMCA villager, String knot) {
        return play(player, villager, knot, Map.of());
    }

    /** As {@link #play(ServerPlayer, VillagerEntityMCA, String)}, setting Ink variables the story declares first. */
    public static boolean play(ServerPlayer player, VillagerEntityMCA villager, String knot, Map<String, Object> variables) {
        if (!Systems.on(Systems.STORIES) || storyFor(player, villager) == null) return false;
        return talk(player, villager, knot, variables);
    }

    private static boolean talk(ServerPlayer player, VillagerEntityMCA villager, @Nullable String knot) {
        return talk(player, villager, knot, Map.of());
    }

    private static boolean talk(ServerPlayer player, VillagerEntityMCA villager, @Nullable String knot, Map<String, Object> variables) {
        closeSession(player);
        StoryDefinition story = storyFor(player, villager);
        if (story == null) {
            send(player, new StoryS2CPayload(villager.getId(), StoryS2CPayload.END, "", List.of(), false));
            return false;
        }
        String name = StorySession.displayName(villager);
        String key = PlayerStories.key(story, villager.getUUID());
        PlayerStories.Entry entry = stories(player).getOrCreate(key, story, villager.getUUID(), name);
        entry.villagerName = name;
        entry.givenName = com.aetherianartificer.townstead.naming.VillagerNames.parts(villager).given();
        StorySession session;
        try {
            session = StorySession.open(player, villager, story, entry);
        } catch (Exception e) {
            com.aetherianartificer.townstead.Townstead.LOGGER.warn("Story {} could not start: {}", story.id(), e.getMessage());
            send(player, new StoryS2CPayload(villager.getId(), StoryS2CPayload.END, "", List.of(), false));
            return false;
        }
        refresh(player.server, player.getUUID(), entry, story, session.villager);
        variables.forEach(session::setIfDeclared);
        SESSIONS.put(player.getUUID(), session);
        if (knot == null && entry.interrupted && !entry.ink.isEmpty() && story.hash().equals(entry.hash)) {
            session.resume();
        } else {
            entry.interrupted = false;
            entry.resumeLines.clear();
            session.start(knot != null ? knot : entryPath(entry, story));
        }
        DIRTY.add(player.getUUID());
        return true;
    }

    /**
     * Where a conversation starts: a quest ready to hand back first, then an open quest's waiting
     * lines, otherwise the greeting.
     */
    private static String entryPath(PlayerStories.Entry entry, StoryDefinition story) {
        for (PlayerStories.QuestRecord record : entry.quests.values()) {
            StoryDefinition.Quest quest = story.quests().get(record.knot);
            if (quest == null || record.state != PlayerStories.QuestState.READY) continue;
            record.state = PlayerStories.QuestState.COMPLETE;
            if (record.skipped && quest.hasSkipped()) return quest.knot() + ".skipped";
            if (quest.hasDone()) return quest.knot() + ".done";
        }
        for (PlayerStories.QuestRecord record : entry.quests.values()) {
            StoryDefinition.Quest quest = story.quests().get(record.knot);
            if (quest != null && record.state == PlayerStories.QuestState.ACTIVE && quest.hasWaiting()) {
                return quest.knot() + ".waiting";
            }
        }
        return "greet";
    }

    private static void closeSession(ServerPlayer player) {
        StorySession session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        session.interrupt();
        session.finish();
        save(player);
        sync(player);
    }

    static void sessionEnded(StorySession session) {
        if (SESSIONS.get(session.player.getUUID()) == session) SESSIONS.remove(session.player.getUUID());
        payOut(session.player);
        save(session.player);
        sync(session.player);
    }

    /**
     * Starts every quest whose knot the story has now entered, and completes a ready quest once the
     * story has told its {@code done} or {@code skipped} lines.
     */
    static void startEnteredQuests(StorySession session, Story story) throws Exception {
        for (StoryDefinition.Quest quest : session.definition.quests().values()) {
            PlayerStories.QuestRecord existing = session.entry.quests.get(quest.knot());
            if (existing != null) {
                if (existing.state == PlayerStories.QuestState.READY && session.toldHandBack(quest, story)) {
                    existing.state = PlayerStories.QuestState.COMPLETE;
                    DIRTY.add(session.player.getUUID());
                }
                continue;
            }
            if (story.getState().visitCountAtPathString(quest.knot()) <= 0) continue;
            PlayerStories.QuestRecord record = session.entry.quest(quest.knot(), quest.goals().size());
            GoalContext ctx = session.goalContext();
            if (quest.skipIf() != null) {
                long value = quest.skipIf().read(ctx);
                if (value != Goal.UNKNOWN && value >= quest.skipIf().total()) {
                    record.state = PlayerStories.QuestState.READY;
                    record.skipped = true;
                }
            }
            if (record.state == PlayerStories.QuestState.ACTIVE) settle(record, quest, ctx);
            DIRTY.add(session.player.getUUID());
        }
    }

    // ---- goals ----

    public static void tick(MinecraftServer server) {
        StoryService.server = server;
        if (++ticks % 20 == 0 && !DIRTY.isEmpty()) {
            for (UUID id : new ArrayList<>(DIRTY)) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    payOut(player);
                    save(player);
                    sync(player);
                }
            }
            DIRTY.clear();
        }
        if (!recheckSoon && ticks % REFRESH_TICKS != 0) return;
        recheckSoon = false;
        if (!Systems.on(Systems.STORIES)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerStories stories = PLAYERS.get(player.getUUID());
            if (stories == null) continue;
            boolean changed = false;
            for (PlayerStories.Entry entry : stories.entries()) {
                StoryDefinition story = Stories.byId(entry.story);
                if (story != null && hasOpenQuest(entry)) changed |= refresh(server, player.getUUID(), entry, story, find(server, entry.villager));
            }
            if (changed) DIRTY.add(player.getUUID());
        }
    }

    private static void onCountedEvent(TownsteadEvent event) {
        MinecraftServer server = StoryService.server;
        if (server == null || !Systems.on(Systems.STORIES)) return;
        recheckSoon = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerStories stories = PLAYERS.get(player.getUUID());
            if (stories == null) continue;
            boolean changed = false;
            for (PlayerStories.Entry entry : stories.entries()) {
                StoryDefinition story = Stories.byId(entry.story);
                if (story == null) continue;
                for (PlayerStories.QuestRecord record : entry.quests.values()) {
                    if (record.state != PlayerStories.QuestState.ACTIVE) continue;
                    StoryDefinition.Quest quest = story.quests().get(record.knot);
                    if (quest == null) continue;
                    GoalContext ctx = null;
                    for (int i = 0; i < quest.goals().size() && i < record.values.length; i++) {
                        Goal goal = quest.goals().get(i);
                        if (goal.seasonal() != null) {
                            if (ctx == null) ctx = context(server, player.getUUID(), entry);
                            if (goal.seasonEvent(event, ctx)) {
                                rollSeason(server, record, i, goal);
                                record.seasons.computeIfAbsent(i, k -> new PlayerStories.SeasonState()).events++;
                                changed = true;
                            }
                            continue;
                        }
                        if (!goal.isCounter() || goal.event() == null || !goal.event().isInstance(event)) continue;
                        if (ctx == null) ctx = context(server, player.getUUID(), entry);
                        long add = goal.increment(event, ctx);
                        if (add <= 0 || record.values[i] >= goal.total()) continue;
                        if (goal.isDistinct()) {
                            String value = goal.distinctValue(event);
                            if (value == null || !record.distinct.computeIfAbsent(i, k -> new java.util.LinkedHashSet<>()).add(value)) continue;
                            add = 1;
                        }
                        record.values[i] = Math.min(goal.total(), record.values[i] + add);
                        changed = true;
                    }
                    if (changed && ctx != null) settle(record, quest, ctx);
                }
                Map<String, Goal> watched = WATCHED.get(entry.story);
                if (watched == null) continue;
                GoalContext ctx = null;
                for (Map.Entry<String, Goal> goal : watched.entrySet()) {
                    if (!goal.getValue().event().isInstance(event)) continue;
                    if (ctx == null) ctx = context(server, player.getUUID(), entry);
                    long add = goal.getValue().increment(event, ctx);
                    if (add <= 0) continue;
                    entry.seen.merge(goal.getKey(), add, Long::sum);
                    changed = true;
                }
            }
            if (changed) DIRTY.add(player.getUUID());
        }
    }

    /** Re-reads the state goals of every open quest in one story. Returns whether anything moved. */
    private static boolean refresh(MinecraftServer server, UUID player, PlayerStories.Entry entry,
                                   StoryDefinition story, @Nullable VillagerEntityMCA speaker) {
        boolean changed = false;
        GoalContext ctx = new GoalContext(server, player, entry.villager, entry.villagerName, speaker);
        for (PlayerStories.QuestRecord record : entry.quests.values()) {
            if (record.state != PlayerStories.QuestState.ACTIVE) continue;
            StoryDefinition.Quest quest = story.quests().get(record.knot);
            if (quest == null) continue;
            for (int i = 0; i < quest.goals().size() && i < record.values.length; i++) {
                Goal goal = quest.goals().get(i);
                if (goal.seasonal() != null) {
                    changed |= checkSeason(server, record, i, goal, ctx);
                    continue;
                }
                if (goal.isCounter()) continue;
                long value = goal.read(ctx);
                if (value == Goal.UNKNOWN || value == record.values[i]) continue;
                record.values[i] = value;
                changed = true;
            }
            changed |= settle(record, quest, ctx);
        }
        return changed;
    }

    /**
     * The season a seasonal goal is in now: a seasons mod's season and year, or a block of
     * {@code season_days} world days without one.
     */
    private static String seasonKey(MinecraftServer server, Goal.Seasonal seasonal) {
        var today = com.aetherianartificer.townstead.calendar.TownsteadCalendar.today(server);
        if (today != null && today.season() != null) return today.year() + ":" + today.season();
        return "d" + (com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(server) / seasonal.seasonDays());
    }

    /** Closes the season a goal was judging when a new one has begun, counting it when it was good. */
    private static boolean rollSeason(MinecraftServer server, PlayerStories.QuestRecord record, int index, Goal goal) {
        Goal.Seasonal seasonal = goal.seasonal();
        String key = seasonKey(server, seasonal);
        PlayerStories.SeasonState state = record.seasons.computeIfAbsent(index, k -> new PlayerStories.SeasonState());
        if (key.equals(state.key)) return false;
        boolean counted = false;
        if (!state.key.isEmpty() && state.days > 0 && state.good >= Math.ceil(state.days * seasonal.share())
                && state.events >= seasonal.perSeason() && record.values[index] < goal.total()) {
            record.values[index]++;
            counted = true;
        }
        state.key = key;
        state.days = 0;
        state.good = 0;
        state.events = 0;
        return counted;
    }

    /** Once a day, checks a seasonal goal's condition for the season in progress. */
    private static boolean checkSeason(MinecraftServer server, PlayerStories.QuestRecord record, int index, Goal goal, GoalContext ctx) {
        boolean changed = rollSeason(server, record, index, goal);
        PlayerStories.SeasonState state = record.seasons.get(index);
        long today = com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(server);
        if (state.lastDay == today || ctx.speaker() == null) return changed;
        state.lastDay = today;
        state.days++;
        if (goal.holds(ctx)) state.good++;
        return true;
    }

    /** Marks a quest ready once every goal is met. A quest with nothing to say back completes. */
    private static boolean settle(PlayerStories.QuestRecord record, StoryDefinition.Quest quest, GoalContext ctx) {
        if (record.state != PlayerStories.QuestState.ACTIVE) return false;
        for (int i = 0; i < quest.goals().size(); i++) {
            if (i >= record.values.length || record.values[i] < quest.goals().get(i).total()) return false;
        }
        record.state = quest.hasDone() ? PlayerStories.QuestState.READY : PlayerStories.QuestState.COMPLETE;
        return true;
    }

    private static boolean hasOpenQuest(PlayerStories.Entry entry) {
        for (PlayerStories.QuestRecord record : entry.quests.values()) {
            if (record.state == PlayerStories.QuestState.ACTIVE) return true;
        }
        return false;
    }

    private static GoalContext context(MinecraftServer server, UUID player, PlayerStories.Entry entry) {
        return new GoalContext(server, player, entry.villager, entry.villagerName, find(server, entry.villager));
    }

    private static @Nullable VillagerEntityMCA find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof VillagerEntityMCA villager && villager.isAlive()) return villager;
        }
        return null;
    }

    /** Hands over the rewards of every completed quest not yet paid, once each. */
    private static void payOut(ServerPlayer player) {
        if (!Systems.on(Systems.STORIES)) return;
        for (PlayerStories.Entry entry : stories(player).entries()) {
            StoryDefinition story = Stories.byId(entry.story);
            if (story == null) continue;
            for (PlayerStories.QuestRecord record : entry.quests.values()) {
                if (record.state != PlayerStories.QuestState.COMPLETE || record.rewarded) continue;
                StoryDefinition.Quest quest = story.quests().get(record.knot);
                if (quest == null || quest.rewards().isEmpty()) continue;
                record.rewarded = true;
                VillagerEntityMCA teller = find(player.server, entry.villager);
                for (var reward : quest.rewards()) {
                    try {
                        reward.give(player, teller);
                    } catch (RuntimeException e) {
                        com.aetherianartificer.townstead.Townstead.LOGGER.warn("Story {} reward for quest {} failed: {}",
                                story.id(), quest.knot(), e.getMessage());
                    }
                }
                DIRTY.add(player.getUUID());
            }
        }
    }

    // ---- quest ledger ----

    public static void sync(ServerPlayer player) {
        List<StoryQuestSyncS2CPayload.Quest> quests = new ArrayList<>();
        if (Systems.on(Systems.STORIES)) {
            for (PlayerStories.Entry entry : stories(player).entries()) {
                StoryDefinition story = Stories.byId(entry.story);
                if (story == null) continue;
                for (PlayerStories.QuestRecord record : entry.quests.values()) {
                    StoryDefinition.Quest quest = story.quests().get(record.knot);
                    if (quest == null) continue;
                    List<StoryQuestSyncS2CPayload.Objective> objectives = new ArrayList<>();
                    boolean complete = record.state == PlayerStories.QuestState.COMPLETE;
                    for (int i = 0; i < quest.goals().size(); i++) {
                        Goal goal = quest.goals().get(i);
                        long current = i < record.values.length ? record.values[i] : 0L;
                        objectives.add(new StoryQuestSyncS2CPayload.Objective(
                                withMarker(goal.label(entry.givenName.isEmpty() ? entry.villagerName : entry.givenName,
                                        player.getGameProfile().getName()), goal.marker(), player),
                                Math.min(current, goal.total()), goal.total(),
                                complete || record.skipped || current >= goal.total()));
                    }
                    List<StoryQuestSyncS2CPayload.Reward> rewards = new ArrayList<>();
                    String teller = entry.givenName.isEmpty() ? entry.villagerName : entry.givenName;
                    for (var reward : quest.rewards()) {
                        for (var preview : reward.preview(teller, player.getGameProfile().getName())) {
                            rewards.add(new StoryQuestSyncS2CPayload.Reward(preview.text(), preview.itemId(), preview.count()));
                        }
                    }
                    quests.add(new StoryQuestSyncS2CPayload.Quest(entry.story + "/" + record.knot + "@" + entry.villager,
                            quest.title(), quest.about(), entry.villagerName, (byte) record.state.ordinal(),
                            quest.hasDone() || (record.skipped && quest.hasSkipped()), List.copyOf(objectives),
                            List.copyOf(rewards)));
                }
            }
        }
        if (Systems.on(Systems.STORIES)) com.aetherianartificer.townstead.contract.Contracts.ledger(player, quests);
        send(player, new StoryQuestSyncS2CPayload(List.copyOf(quests)));
    }

    /** Fills {@code {x}} and {@code {z}} from the goal's marker, once the player has one. */
    private static String withMarker(String text, @Nullable String marker, ServerPlayer player) {
        if (marker == null) return text;
        var pos = com.aetherianartificer.townstead.pheno.marker.PlayerMarkers.get(player, marker);
        return text.replace("{x}", pos == null ? "?" : Integer.toString(pos.pos().getX()))
                .replace("{z}", pos == null ? "?" : Integer.toString(pos.pos().getZ()));
    }

    static void send(ServerPlayer player, Object payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                (net.minecraft.network.protocol.common.custom.CustomPacketPayload) payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
