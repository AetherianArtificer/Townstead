package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.chronicle.knowledge.GossipTicker;
import com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache;
import com.aetherianartificer.townstead.dialogue.contextual.*;
import com.aetherianartificer.townstead.dialogue.conversation.generative.*;
import com.aetherianartificer.townstead.hangout.*;
import com.aetherianartificer.townstead.expression.ExpressionService;
import com.aetherianartificer.townstead.performance.*;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.reaction.ReactionLockTracker;
import com.aetherianartificer.townstead.reaction.trigger.event.DialogueStateTracker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.social.RelationshipLedger;
import com.aetherianartificer.townstead.social.RelationshipQualities;
import com.aetherianartificer.townstead.social.RelationshipService;

/**
 * Server-owned social simulation. Idle proximity and hangouts start encounters; each encounter opens,
 * talks through topics with expansions and pauses, and closes. Topics with authored lines play as set
 * pieces; the rest are composed from parts.
 */
public final class ConversationEngine {
    private static final String CHANNEL = "conversation";
    private static final ResourceLocation TALK = ResourceLocation.tryParse("townstead:conversation_talk");
    private static final ResourceLocation LISTEN = ResourceLocation.tryParse("townstead:conversation_listen");
    private static final Map<ServerLevel, Runtime> LEVELS = new IdentityHashMap<>();
    private static final DialogueSelector LINES = new DialogueSelector();
    /** Used when no encounter data is loaded: one topic, no greeting, no goodbye. */
    private static final ConversationEncounter FALLBACK = new ConversationEncounter(ResourceLocation.tryParse("townstead:fallback"),
            ConversationEncounter.Context.IDLE, List.of(new ConversationEncounter.Sequence(List.of(), 1, 0, -1)), List.of(), 1, 1, false,
            new ConversationEncounter.Chattiness(0, 0, 0, Map.of()), 0, ConversationEncounter.Pause.NONE, null,
            List.of(new ConversationEncounter.Sequence(List.of(), 1, 0, -1)));

    static final class Runtime {
        final Map<UUID, Live> members = new LinkedHashMap<>();
        final Map<UUID, Long> cooldowns = new HashMap<>();
        final Deque<Utterance> audible = new ArrayDeque<>();
        final Map<Integer, ConversationRuntime.VillageRecency> recency = new HashMap<>();
        final Map<String, Long> greeted = new HashMap<>();
    }
    private record Utterance(Vec3 position, long until) {}
    static final class Live {
        final UUID a, b;
        final boolean preview, hangout;
        /** A dry run keeps its own clock so hangout departures still arrive. */
        boolean simulated;
        long clock;
        final Random random;
        final List<PerformanceHandle> handles = new ArrayList<>();
        final long deadline;
        EncounterRun run;
        long nextAt;
        String reason = "";
        Live(UUID a, UUID b, boolean preview, boolean hangout, Random random, long now) {
            this.a = a; this.b = b; this.preview = preview; this.hangout = hangout; this.random = random;
            nextAt = now;
            deadline = now + (hangout ? 24000 : 3600);
        }
        UUID of(EncounterRun.Side side) { return side == EncounterRun.Side.A ? a : b; }
        void stopGestures() { handles.forEach(PerformanceHandle::stop); handles.clear(); }
    }
    private ConversationEngine() {}

    public static boolean active(UUID villager) {
        return LEVELS.values().stream().anyMatch(runtime -> runtime.members.containsKey(villager));
    }
    public static void clear() {
        LEVELS.values().forEach(runtime -> new HashSet<>(runtime.members.values()).forEach(Live::stopGestures));
        LEVELS.clear(); LINES.clear();
    }
    public static void forget(VillagerEntityMCA actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        Runtime runtime = LEVELS.get(level); if (runtime == null) return;
        Live live = runtime.members.get(actor.getUUID());
        if (live != null) { live.reason = "participant_removed"; end(level, runtime, live); }
    }

    /** Discovery is staggered and deliberately does not take ownership of navigation or work. */
    public static void consider(VillagerEntityMCA actor) {
        if (!Switchboard.get(TownsteadConfig.ENABLE_CONVERSATIONS)
                || !(actor.level() instanceof ServerLevel level) || ConversationTopics.all().isEmpty()) return;
        long now = level.getGameTime();
        if (Math.floorMod(now + actor.getUUID().hashCode(), 200) != 0) return;
        MobSightings.observe(level, actor);
        if (!available(actor)) return;
        Runtime runtime = LEVELS.computeIfAbsent(level, ignored -> new Runtime());
        if (runtime.members.containsKey(actor.getUUID()) || runtime.cooldowns.getOrDefault(actor.getUUID(), 0L) > now) return;
        if (actor.getRandom().nextDouble() >= Switchboard.get(TownsteadConfig.IDLE_CONVERSATION_CHANCE)) return;
        List<VillagerEntityMCA> nearby = level.getEntitiesOfClass(VillagerEntityMCA.class,
                actor.getBoundingBox().inflate(6), other -> other != actor && available(other)
                        && !runtime.members.containsKey(other.getUUID()) && actor.distanceToSqr(other) <= 36
                        && actor.hasLineOfSight(other));
        // Familiar companions are attractive, but an unfamiliar neighbor retains a chance.
        ConversationMemory memory = ConversationSavedData.get(level.getServer()).memory();
        ChronicleSavedData social = socialData(level.getServer());
        long today = TownsteadCalendar.worldDay(level.getServer());
        Random random = new Random(actor.getRandom().nextLong());
        VillagerEntityMCA other = choose(nearby, candidate -> {
            ConversationMemory.View history = memory.view(actor.getUUID(), candidate.getUUID(), now);
            if (history.lastAt() != Long.MIN_VALUE && now - history.lastAt() < 1200) return 0;
            if (runtime.cooldowns.getOrDefault(candidate.getUUID(), 0L) > now) return 0;
            return Math.max(0.2, 1 + social.relationships().value(actor.getUUID(), candidate.getUUID(),
                    RelationshipQualities.AFFECTION, today) / 20D);
        }, random);
        if (other != null) request(actor, other, null, false);
    }

    /** Shared entry point for idle AI, Pheno, hangouts and operator previews. */
    public static boolean request(VillagerEntityMCA actor, VillagerEntityMCA other,
                                  ResourceLocation requestedTopic, boolean preview) {
        if (!preview && !Switchboard.get(TownsteadConfig.ENABLE_CONVERSATIONS)) return false;
        if (!(actor.level() instanceof ServerLevel level) || other.level() != level || actor == other
                || !available(actor) || !available(other) || actor.distanceToSqr(other) > 36
                || !actor.hasLineOfSight(other) || !other.hasLineOfSight(actor)) return false;
        Runtime runtime = LEVELS.computeIfAbsent(level, ignored -> new Runtime());
        long now = level.getGameTime();
        if (runtime.members.containsKey(actor.getUUID()) || runtime.members.containsKey(other.getUUID())) return false;
        ConversationMemory.View history = ConversationSavedData.get(level.getServer()).memory().view(actor.getUUID(), other.getUUID(), now);
        if (!preview && (runtime.cooldowns.getOrDefault(actor.getUUID(), 0L) > now
                || runtime.cooldowns.getOrDefault(other.getUUID(), 0L) > now
                || history.lastAt() != Long.MIN_VALUE && now - history.lastAt() < 1200)) return false;
        ConversationTopic forced = null;
        if (requestedTopic != null) {
            forced = ConversationTopics.all().get(requestedTopic);
            if (forced == null) return false;
        }
        if (ConversationTopics.all().isEmpty()) return false;
        if (!preview) {
            ChronicleSavedData social = socialData(level.getServer());
            long today = TownsteadCalendar.worldDay(level.getServer());
            com.aetherianartificer.townstead.social.InitialImpressions.apply(social, actor, other, today);
            com.aetherianartificer.townstead.social.InitialImpressions.apply(social, other, actor, today);
        }
        boolean hangout = sharedVisit(actor, other);
        GenerativeDialogue.Data data = GenerativeDialogue.data();
        ConversationEncounter encounter = data.encounter(hangout ? ConversationEncounter.Context.HANGOUT
                : ConversationEncounter.Context.IDLE);
        if (encounter == null) encounter = data.encounter(ConversationEncounter.Context.IDLE);
        if (encounter == null || !data.ready()) encounter = FALLBACK;
        Live live = new Live(actor.getUUID(), other.getUUID(), preview, hangout, new Random(actor.getRandom().nextLong()), now);
        live.run = new EncounterRun(encounter, new Driver(level, runtime, live, encounter), forced);
        if (hangout) live.run.state().put(ConversationState.VENUE, venueKind(actor));
        runtime.members.put(actor.getUUID(), live); runtime.members.put(other.getUUID(), live);
        return true;
    }

    public static void tick(MinecraftServer server) {
        for (var entry : new ArrayList<>(LEVELS.entrySet())) {
            ServerLevel level = entry.getKey(); if (level.getServer() != server) continue;
            Runtime runtime = entry.getValue(); long now = level.getGameTime();
            runtime.cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
            runtime.audible.removeIf(e -> e.until() <= now);
            if (now % 10 != 0) continue;
            for (Live live : new LinkedHashSet<>(runtime.members.values())) {
                VillagerEntityMCA a = find(level, live.a), b = find(level, live.b);
                boolean valid = (live.preview || Switchboard.get(TownsteadConfig.ENABLE_CONVERSATIONS))
                        && a != null && b != null && available(a) && available(b)
                        && a.distanceToSqr(b) <= 49 && a.hasLineOfSight(b) && b.hasLineOfSight(a);
                if (!valid) { live.reason = "participant_unavailable"; end(level, runtime, live); continue; }
                if (now >= live.deadline) { live.reason = "timeout"; end(level, runtime, live); continue; }
                if (now < live.nextAt) { face(a, b, live); continue; }
                step(level, runtime, live, a, b, now);
            }
        }
    }

    private static void face(VillagerEntityMCA a, VillagerEntityMCA b, Live live) {
        a.getLookControl().setLookAt(b, 25F, 25F);
        b.getLookControl().setLookAt(a, 25F, 25F);
    }

    private static void step(ServerLevel level, Runtime runtime, Live live, VillagerEntityMCA a, VillagerEntityMCA b, long now) {
        EncounterRun.Step step = live.run.next();
        if (step instanceof EncounterRun.End end) { live.reason = end.reason(); end(level, runtime, live); return; }
        if (step instanceof EncounterRun.Pause pause) {
            live.run.spoke(ConversationTopic.SAY);
            live.nextAt = now + pause.ticks();
            return;
        }
        EncounterRun.Speak speak = (EncounterRun.Speak) step;
        VillagerEntityMCA speaker = speak.speaker() == EncounterRun.Side.A ? a : b;
        VillagerEntityMCA listener = speaker == a ? b : a;
        if (!live.preview && !watched(speaker)) { unheard(level, runtime, live, speak, speaker, listener, a, b, now); return; }
        face(a, b, live);
        if (runtime.audible.stream().anyMatch(u -> u.position().distanceToSqr(speaker.position()) < 144)) return;
        GenerativeDialogue.Data data = GenerativeDialogue.data();
        ConversationTopic.Turn turn = speak.turn();
        Said said = utter(level, runtime, live, speak, speaker, listener, LINES, now);
        if (said == null) { live.run.failed(); return; }
        ConversationRuntime.Spoken rendered = said.rendered();
        ResourceLocation spokenMove = said.move();
        ConversationRuntime.send(speaker, rendered);
        int duration = ConversationRuntime.duration(rendered.english(), turn == null ? -1 : turn.duration());
        runtime.audible.addLast(new Utterance(speaker.position(), now + Math.max(40, duration - 10)));
        live.stopGestures();
        ConversationMove def = data.moves().get(spokenMove);
        ConversationTopic.Cue gesture = turn != null && !turn.gesture().isEmpty() ? turn.gesture()
                : def != null && !def.gesture().isEmpty() ? def.gesture() : new ConversationTopic.Cue(TALK, null);
        ConversationTopic.Cue listening = turn != null && !turn.listener().isEmpty() ? turn.listener()
                : def != null && !def.listener().isEmpty() ? def.listener() : new ConversationTopic.Cue(LISTEN, null);
        present(level, live, speaker, listener, gesture);
        present(level, live, listener, speaker, listening);
        if (def != null && def.role() == ConversationMove.Role.OPENING) runtime.greeted.put(pairKey(live), TownsteadCalendar.worldDay(level.getServer()));
        if (!live.preview && def != null && def.spreads() && speak.binding() != null && speak.binding().subject() != null
                && speak.binding().subject().source() instanceof KnownStoriesCache.Entry story) {
            GossipTicker.tellStory(level, speaker, listener, story);
        }
        live.run.spoke(spokenMove);
        live.nextAt = now + duration;
        for (EncounterRun.Completion completion : live.run.drainCompletions()) {
            if (completion.turn().outcome() == null || live.preview) continue;
            VillagerEntityMCA initiator = completion.initiator() == EncounterRun.Side.A ? a : b;
            applyOutcome(level, initiator, initiator == a ? b : a, completion.topic().id(), completion.turn().outcome());
        }
        if (Switchboard.get(TownsteadConfig.DEBUG_LOGGING)) Townstead.LOGGER.info("[Conversation] {} ({}): {}",
                speaker.getName().getString(), spokenMove, rendered.english());
    }

    /** Whether a player is close enough to see or hear this conversation. */
    private static boolean watched(VillagerEntityMCA speaker) {
        return !speaker.level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                speaker.getBoundingBox().inflate(WATCH_RANGE)).isEmpty();
    }

    static final double WATCH_RANGE = 48;

    /**
     * A line nobody can see or hear: no text and no gestures, but the conversation still moves on and
     * its effects still apply, including gossip and relationship outcomes.
     */
    private static void unheard(ServerLevel level, Runtime runtime, Live live, EncounterRun.Speak speak,
                                VillagerEntityMCA speaker, VillagerEntityMCA listener, VillagerEntityMCA a, VillagerEntityMCA b, long now) {
        ResourceLocation move = speak.moves().get(0);
        ConversationMove def = GenerativeDialogue.data().moves().get(move);
        live.stopGestures();
        if (def != null && def.role() == ConversationMove.Role.OPENING) runtime.greeted.put(pairKey(live), TownsteadCalendar.worldDay(level.getServer()));
        if (def != null && def.spreads() && speak.binding() != null && speak.binding().subject() != null
                && speak.binding().subject().source() instanceof KnownStoriesCache.Entry story) {
            GossipTicker.tellStory(level, speaker, listener, story);
        }
        // With no words spoken, no question or pair stays open.
        live.run.state().put(ConversationState.PENDING, null);
        live.run.state().clearExpectation();
        live.run.spoke(move);
        live.nextAt = now + UNHEARD_LINE_TICKS;
        for (EncounterRun.Completion completion : live.run.drainCompletions()) {
            if (completion.turn().outcome() == null) continue;
            VillagerEntityMCA initiator = completion.initiator() == EncounterRun.Side.A ? a : b;
            applyOutcome(level, initiator, initiator == a ? b : a, completion.topic().id(), completion.turn().outcome());
        }
    }

    static final int UNHEARD_LINE_TICKS = 60;

    /** One spoken line, before anything reaches the world. */
    record Said(ResourceLocation move, ConversationRuntime.Spoken rendered, LineComposer.Line line) {}

    /** Builds the line for a step: an authored set piece, or a composed line. Null when no move produces one. */
    static Said utter(ServerLevel level, Runtime runtime, Live live, EncounterRun.Speak speak,
                            VillagerEntityMCA speaker, VillagerEntityMCA listener, DialogueSelector selector, long now) {
        GenerativeDialogue.Data data = GenerativeDialogue.data();
        ConversationTopic.Turn turn = speak.turn();
        for (ResourceLocation move : speak.moves()) {
            if (turn != null && !turn.lines().isEmpty() && move.equals(turn.move())) {
                String poolId = speak.binding() == null ? "set_piece/" + turn.id() : speak.binding().topic().id() + "/" + turn.id();
                List<DialoguePalette.Line> lines = new ArrayList<>();
                for (String key : turn.lines()) lines.add(new DialoguePalette.Line(key, key, 1, Set.of(), Set.of(), Set.of()));
                DialoguePalette palette = new DialoguePalette(speak.binding() == null ? ConversationTopic.SAY : speak.binding().topic().id(),
                        poolId, 0, 16, lines);
                var selected = selector.select(speaker.getUUID(), new DialogueRequest(poolId, Set.of(), Set.of(), Set.of()),
                        List.of(palette), now, live.random);
                if (selected.isPresent()) return new Said(move, ConversationRuntime.renderKey(selected.get().translation()), null);
                continue;
            }
            LineComposer.Line line = compose(level, runtime, live, speak, move, speaker, listener, data);
            if (line != null) {
                LineComposer.Subject subject = speak.binding() == null ? null : speak.binding().subject();
                return new Said(move, ConversationRuntime.render(line, subject, speaker, listener), line);
            }
        }
        return null;
    }

    private static LineComposer.Line compose(ServerLevel level, Runtime runtime, Live live, EncounterRun.Speak speak,
                                             ResourceLocation move, VillagerEntityMCA speaker, VillagerEntityMCA listener,
                                             GenerativeDialogue.Data data) {
        EncounterRun.Binding binding = speak.binding();
        LineComposer.Subject subject = binding == null ? null : binding.subject();
        String register = speak.turn() != null && !speak.turn().register().isEmpty() ? speak.turn().register()
                : binding != null && binding.subjectKind() != null && data.subjects().containsKey(binding.subjectKind())
                ? data.subjects().get(binding.subjectKind()).register()
                : binding != null ? binding.topic().register() : "ambient";
        DialogueRequest request = context(speaker, listener);
        LineComposer.Speaker facts = ConversationRuntime.speaker(level, speaker, data, register, request.context());
        EncounterRun.Side initiatorSide = live.run.topicInitiator();
        UUID initiator = initiatorSide == null ? speaker.getUUID() : live.of(initiatorSide);
        ConversationEncounter encounter = data.encounter(live.hangout ? ConversationEncounter.Context.HANGOUT : ConversationEncounter.Context.IDLE);
        ResourceLocation bridge = speak.bridge() && encounter != null ? encounter.bridgePool() : null;
        ConversationRuntime.Presence presence = new ConversationRuntime.Presence(level, speaker, data);
        LineComposer.World world = new LineComposer.World() {
            @Override public boolean requirement(String requirement) { return presence.test(requirement); }
            @Override public double gate(ConversationTopic.Gate gate) { return ConversationEngine.gate(gate, speaker, listener); }
            @Override public boolean filtered(Set<String> contentTags) { return false; }
        };
        LineComposer.Request req = new LineComposer.Request(move, facts, listener.getUUID(), initiator, subject, register,
                speak.finalLine(), bridge, live.run.state(), live.run.balance(speak.speaker()), live.random);
        return LineComposer.compose(data, req, world, recency(level, runtime, speaker));
    }

    private static ConversationRuntime.VillageRecency recency(ServerLevel level, Runtime runtime, VillagerEntityMCA speaker) {
        int village = VillageManager.get(level).findNearestVillage(speaker.blockPosition(), Village.MERGE_MARGIN)
                .map(Village::getId).orElse(-1);
        return runtime.recency.computeIfAbsent(village, ignored -> new ConversationRuntime.VillageRecency());
    }

    /** The world half of an encounter: topic binding, reply weights, and whether someone is leaving. */
    static final class Driver implements EncounterRun.Driver {
        private final ServerLevel level;
        private final Runtime runtime;
        private final Live live;
        private final ConversationEncounter encounter;
        Driver(ServerLevel level, Runtime runtime, Live live, ConversationEncounter encounter) {
            this.level = level; this.runtime = runtime; this.live = live; this.encounter = encounter;
        }
        private VillagerEntityMCA villager(EncounterRun.Side side) { return find(level, live.of(side)); }

        @Override public EncounterRun.Binding bindTopic(EncounterRun.Side side, Set<ResourceLocation> usedTopics, ResourceLocation previousKind) {
            VillagerEntityMCA speaker = villager(side), listener = villager(side.other());
            if (speaker == null || listener == null) return null;
            GenerativeDialogue.Data data = GenerativeDialogue.data();
            ConversationMemory.View history = ConversationSavedData.get(level.getServer()).memory()
                    .view(speaker.getUUID(), listener.getUUID(), level.getGameTime());
            ConversationRuntime.Presence presence = new ConversationRuntime.Presence(level, speaker, data);
            List<EncounterRun.Binding> options = new ArrayList<>();
            Map<EncounterRun.Binding, Double> weights = new HashMap<>();
            for (ConversationTopic topic : ConversationTopics.all().values()) {
                if (usedTopics.contains(topic.id())) continue;
                if (!topic.requires().stream().allMatch(presence::test)) continue;
                double weight = topic.weight() * gate(topic.gate(), speaker, listener);
                ConversationTopic.Turn opener = topic.turns().get(topic.start());
                if (weight <= 0 || gate(opener.gate(), speaker, listener) <= 0) continue;
                weight *= humourAfterGrief(topic, usedTopics, speaker.getInfectionProgress());
                long repeats = history.topics().stream().filter(topic.id().toString()::equals).count();
                // Stable individual interests emerge without assigning every member of a Root the same tastes.
                weight *= (0.65 + Math.floorMod(Objects.hash(speaker.getUUID(), topic.id()), 101) / 100D) / (1 + repeats * 3);
                if (topic.subjects().isEmpty()) {
                    EncounterRun.Binding binding = new EncounterRun.Binding(topic, null, topic.id());
                    options.add(binding); weights.put(binding, weight);
                    continue;
                }
                List<LineComposer.Subject> instances = new ArrayList<>();
                ResourceLocation kind = null;
                for (SubjectDefinition subject : data.subjects().values()) {
                    if (topic.subjects().stream().noneMatch(f -> data.subjectMatches(subject.id(), f))) continue;
                    if (!subject.requires().stream().allMatch(presence::test)) continue;
                    if (gate(subject.gate(), speaker, listener) <= 0) continue;
                    List<LineComposer.Subject> found = SubjectSources.instances(new SubjectSources.Query(level, speaker, listener, subject));
                    if (!found.isEmpty()) { instances.addAll(found); kind = subject.id(); }
                }
                if (instances.isEmpty()) continue;
                // Goals: the speaker raises what matters to them now, fresh news, a need, a danger, a friend.
                LineComposer.Subject subject = choose(instances, s -> Math.max(0.1, 1 + GOAL_WEIGHT * s.urgency()), live.random);
                if (subject == null) continue;
                EncounterRun.Binding binding = new EncounterRun.Binding(topic, subject, kind);
                options.add(binding); weights.put(binding, weight * Math.max(0.1, 1 + GOAL_WEIGHT * subject.urgency()));
            }
            if (options.isEmpty()) return null;
            return choose(options, weights::get, live.random);
        }

        @Override public double replyWeight(ConversationTopic.Turn turn, EncounterRun.Side side) {
            VillagerEntityMCA speaker = villager(side), listener = villager(side.other());
            return speaker == null || listener == null ? 0 : gate(turn.gate(), speaker, listener);
        }

        @Override public double chattiness() {
            VillagerEntityMCA a = villager(EncounterRun.Side.A), b = villager(EncounterRun.Side.B);
            if (a == null || b == null) return 0;
            int meetings = ConversationSavedData.get(level.getServer()).memory().view(a.getUUID(), b.getUUID(), level.getGameTime()).meetings();
            return encounter.chattiness().of(meetings, List.of(personality(a), personality(b)));
        }

        @Override public double interest(EncounterRun.Side side) {
            VillagerEntityMCA self = villager(side), other = villager(side.other());
            if (self == null || other == null) return 0;
            int meetings = ConversationSavedData.get(level.getServer()).memory().view(self.getUUID(), other.getUUID(), level.getGameTime()).meetings();
            double affection = socialData(level.getServer()).relationships().value(self.getUUID(), other.getUUID(),
                    RelationshipQualities.AFFECTION, TownsteadCalendar.worldDay(level.getServer()));
            double value = encounter.chattiness().of(meetings, List.of(personality(self)));
            value += Math.max(-0.2, Math.min(0.2, affection / 60));
            if (self.getVillagerBrain().getMoodValue() < 0) value -= 0.1;
            return value + infectionInterest(personality(self), self.getInfectionProgress(), other.getInfectionProgress(), affection);
        }

        @Override public String mood() {
            VillagerEntityMCA a = villager(EncounterRun.Side.A), b = villager(EncounterRun.Side.B);
            if (a == null || b == null) return null;
            DialogueRequest facts = context(a, b);
            if (facts.relationship().contains("strained")) return "tense";
            if (feverish(a.getInfectionProgress()) || feverish(b.getInfectionProgress()))
                return facts.relationship().contains("friend") ? null : "tense";
            if (facts.relationship().contains("friend")) return "warm";
            return null;
        }

        @Override public boolean greetedToday() {
            Long day = runtime.greeted.get(pairKey(live));
            return day != null && day == TownsteadCalendar.worldDay(level.getServer());
        }

        @Override public EncounterRun.Side leaving() {
            if (!live.hangout) return null;
            long now = live.simulated ? live.clock : level.getGameTime();
            for (EncounterRun.Side side : EncounterRun.Side.values()) {
                HangoutVisit visit = HangoutEngine.visit(live.of(side));
                if (visit == null || visit.phase() != HangoutVisit.Phase.PRESENT || visit.deadline() - now < 400) return side;
            }
            return null;
        }

        @Override public ConversationMove move(ResourceLocation id) { return GenerativeDialogue.data().moves().get(id); }
        @Override public Random random() { return live.random; }
    }

    static final double GOAL_WEIGHT = 3;

    private static final Set<String> FEARFUL = Set.of("anxious", "shy", "introverted", "sensitive", "crabby", "grumpy");

    /** After grave news, or with a fever, a joke is rarely the next thing to say. */
    static double humourAfterGrief(ConversationTopic topic, Set<ResourceLocation> usedTopics, float infection) {
        if (!"humor".equals(topic.register())) return 1;
        boolean grave = usedTopics.stream().map(id -> ConversationTopics.all().get(id))
                .anyMatch(t -> t != null && "grief".equals(t.register()));
        return grave ? 0.1 : feverish(infection) ? 0.2 : 1;
    }

    public static boolean feverish(float infection) { return infection >= net.conczin.mca.entity.Infectable.FEVER_THRESHOLD; }

    /**
     * A fever makes talk harder: the sick side has less to give, fearful people keep it short, and close friends stay.
     */
    public static double infectionInterest(String personality, float own, float other, double affection) {
        double change = feverish(own) ? -0.15 : 0;
        if (feverish(other)) change += affection >= 30 ? 0.1 : FEARFUL.contains(personality) ? -0.25 : -0.05;
        return change;
    }

    private static String personality(VillagerEntityMCA villager) {
        String id = com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat.id(villager.getVillagerBrain().getPersonality());
        return id.startsWith("mca:") ? id.substring(4) : id;
    }

    static String pairKey(Live live) {
        return live.a.compareTo(live.b) < 0 ? live.a + "|" + live.b : live.b + "|" + live.a;
    }

    static boolean sharedVisit(VillagerEntityMCA a, VillagerEntityMCA b) {
        HangoutVisit first = HangoutEngine.visit(a.getUUID()), second = HangoutEngine.visit(b.getUUID());
        return first != null && second != null && first.phase() == HangoutVisit.Phase.PRESENT
                && second.phase() == HangoutVisit.Phase.PRESENT && first.venueAnchor().equals(second.venueAnchor());
    }

    /** A coarse venue kind that venue parts gate on. */
    static String venueKind(VillagerEntityMCA villager) {
        HangoutVisit visit = HangoutEngine.visit(villager.getUUID());
        if (visit == null) return "";
        String id = visit.venueDefinition().getPath();
        HangoutVenue venue = HangoutData.venues().get(visit.venueDefinition());
        Set<String> tags = new HashSet<>();
        if (venue != null) venue.tags().forEach(t -> tags.add(t.getPath()));
        if (id.contains("cafe") || id.contains("tea") || tags.contains("tea")) return "cafe";
        if (id.contains("tavern") || id.contains("bar") || id.contains("inn") || tags.contains("drink")) return "tavern";
        return "outdoors";
    }

    private static void present(ServerLevel level, Live live, VillagerEntityMCA actor, VillagerEntityMCA other, ConversationTopic.Cue cue) {
        if (cue.performance() != null) {
            PerformanceHandle handle = PerformanceProviders.play(level,
                    new PerformanceRequest(actor, cue.performance(), CHANNEL, 32, 45, PerformanceRequest.Fallback.NONE));
            if (handle != null) live.handles.add(handle);
        }
        if (cue.expression() != null) ExpressionService.emit(actor, cue.expression(), other);
    }
    private static double gate(ConversationTopic.Gate gate, VillagerEntityMCA actor, VillagerEntityMCA other) {
        if (actor == null || other == null) return 0;
        if (gate.isOpen()) return 1;
        return gate.weight(context(actor, other), condition -> condition.test(new ConditionContext(actor, other)),
                value -> value.get(com.aetherianartificer.townstead.pheno.selector.SelectorContext.of(new ConditionContext(actor, other))));
    }
    public static DialogueRequest context(VillagerEntityMCA actor, VillagerEntityMCA other) {
        Set<String> facts = new LinkedHashSet<>(), relationships = new LinkedHashSet<>();
        if (actor.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            ConversationMemory.View history = ConversationSavedData.get(level.getServer()).memory().view(actor.getUUID(), other.getUUID(), now);
            ChronicleSavedData social = socialData(level.getServer());
            double affection = social.relationships().value(actor.getUUID(), other.getUUID(), RelationshipQualities.AFFECTION,
                    TownsteadCalendar.worldDay(level.getServer()));
            relationships.add(history.meetings() == 0 ? "stranger" : "familiar");
            if (affection >= 12 && history.meetings() >= 4) relationships.add("friend");
            if (affection <= -8) relationships.add("strained");
            relationships.addAll(com.aetherianartificer.townstead.social.RelationshipDescriptors.tags(actor, other));
            if (!history.memory().isEmpty()) facts.add("memory:" + history.memory());
            social.memoriesFor(actor.getUUID()).stream()
                    .filter(memory -> other.getUUID().equals(memory.otherParty()) && memory.strength() > 0)
                    .map(memory -> "memory:" + memory.memoryKey()).forEach(facts::add);
            if (affection <= -4) facts.add("relationship:strained");
            String culture = ConversationRuntime.culture(level, actor), otherCulture = ConversationRuntime.culture(level, other);
            if (!culture.isEmpty()) facts.add("culture:" + culture);
            if (!otherCulture.isEmpty()) facts.add("other_culture:" + otherCulture);
            if (!culture.isEmpty() && culture.equals(otherCulture)) facts.add("culture:shared");
        }
        facts.add("stage:" + ConversationRuntime.stage(actor));
        facts.add("other_stage:" + ConversationRuntime.stage(other));
        int mood = actor.getVillagerBrain().getMoodValue(); facts.add(mood < 0 ? "mood:low" : "mood:content");
        if (com.aetherianartificer.townstead.profession.ProfessionIdentity.rawId(actor)
                .equals(com.aetherianartificer.townstead.profession.ProfessionIdentity.rawId(other))) facts.add("profession:shared");
        HangoutVisit visit = HangoutEngine.visit(actor.getUUID());
        if (visit != null && visit.phase() == HangoutVisit.Phase.PRESENT) {
            facts.add("hangout:present");
            facts.add("venue:" + visit.venueDefinition());
            HangoutVenue venue = HangoutData.venues().get(visit.venueDefinition());
            if (venue != null) venue.tags().forEach(tag -> facts.add("venue_tag:" + tag));
        }
        return DialogueDirector.describe(actor, "conversation", facts, relationships, other);
    }
    public static boolean available(VillagerEntityMCA actor) {
        if (!actor.isAlive() || actor.isRemoved() || actor.isSleeping() || actor.getVillagerBrain().isPanicking()
                || actor.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || DialogueStateTracker.activePartner(actor) != null
                || com.aetherianartificer.townstead.root.LifeStageProgression.isBabyStage(actor)
                || ReactionLockTracker.isLocked(actor, actor.level().getGameTime())) return false;
        if (actor.getLastHurtByMob() != null && actor.tickCount - actor.getLastHurtByMobTimestamp() < 200) return false;
        var needs = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(actor).needs();
        if (com.aetherianartificer.townstead.api.impl.v1.NeedScales.hungerEnabled() && needs.hunger() <= com.aetherianartificer.townstead.hunger.HungerData.EMERGENCY_THRESHOLD
                || com.aetherianartificer.townstead.api.impl.v1.NeedScales.thirstEnabled() && needs.thirst() <= com.aetherianartificer.townstead.thirst.ThirstData.EMERGENCY_THRESHOLD
                || com.aetherianartificer.townstead.api.impl.v1.NeedScales.fatigueEnabled() && needs.fatigue() >= com.aetherianartificer.townstead.fatigue.FatigueData.EXHAUSTED_THRESHOLD) return false;
        HangoutVisit visit = HangoutEngine.visit(actor.getUUID());
        if (visit != null) return visit.phase() == HangoutVisit.Phase.PRESENT;
        Activity activity = actor.getBrain().getSchedule().getActivityAt((int) (actor.level().getDayTime() % 24000L));
        return !actor.isPassenger() && actor.getNavigation().isDone() && (activity == Activity.IDLE || activity == Activity.MEET);
    }
    private static VillagerEntityMCA find(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof VillagerEntityMCA actor ? actor : null;
    }
    private static void end(ServerLevel level, Runtime runtime, Live live) {
        if (!runtime.members.remove(live.a, live)) return;
        runtime.members.remove(live.b, live); live.stopGestures();
        live.run.cancel();
        long now = level.getGameTime();
        if (!live.preview) {
            runtime.cooldowns.put(live.a, now + 400); runtime.cooldowns.put(live.b, now + 400);
        }
        long today = TownsteadCalendar.worldDay(level.getServer());
        runtime.greeted.values().removeIf(day -> day < today);
        if (Switchboard.get(TownsteadConfig.DEBUG_LOGGING)) Townstead.LOGGER.info("[Conversation] {} -> {}: ended after {} lines ({})",
                live.a, live.b, live.run.lines(), live.reason);
    }

    private static void applyOutcome(ServerLevel level, VillagerEntityMCA a, VillagerEntityMCA b, ResourceLocation topic,
                                     ConversationTopic.Outcome outcome) {
        long now = level.getGameTime();
        ConversationSavedData data = ConversationSavedData.get(level.getServer());
        ConversationMemory.Completion completion = data.memory().completeDetailed(a.getUUID(), a.getDisplayName().getString(), b.getUUID(), b.getDisplayName().getString(),
                topic.toString(), outcome, now, false);
        data.setDirty();
        if (!completion.rewarded()) return;
        ChronicleSavedData social = socialData(level.getServer());
        long today = TownsteadCalendar.worldDay(level.getServer());
        applyConversationRelationship(social, a.getUUID(), b.getUUID(), outcome.initiatorOpinion(), completion.operationId(), topic, today);
        applyConversationRelationship(social, b.getUUID(), a.getUUID(), outcome.responderOpinion(), completion.operationId(), topic, today);
        applyConversationRelationships(social, a.getUUID(), b.getUUID(), outcome.initiatorRelationship(), completion.operationId(), topic, today);
        applyConversationRelationships(social, b.getUUID(), a.getUUID(), outcome.responderRelationship(), completion.operationId(), topic, today);
        social.addEpisodicMemory(a.getUUID(), completion.operationId() + ":memory:initiator",
                outcome.initiatorMemory(), b.getUUID(), today, topic.toString(),
                Map.of("topic", topic.toString(), "other_name", b.getDisplayName().getString()));
        social.addEpisodicMemory(b.getUUID(), completion.operationId() + ":memory:responder",
                outcome.responderMemory(), a.getUUID(), today, topic.toString(),
                Map.of("topic", topic.toString(), "other_name", a.getDisplayName().getString()));
        RelationshipService.recognizeFriendship(social, a.getUUID(), a.getDisplayName().getString(), b.getUUID(),
                b.getDisplayName().getString(), data.memory().view(a.getUUID(), b.getUUID(), now).meetings(), today);
        a.getVillagerBrain().modifyMoodValue(outcome.initiatorMood()); b.getVillagerBrain().modifyMoodValue(outcome.responderMood());
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.conversation(a, b, topic,
                outcome.memory(), outcome.initiatorMemory(), outcome.responderMemory());
        com.aetherianartificer.townstead.api.impl.v1.ApiEvents.conversationHeld(a, b, topic, outcome.memory());
    }

    private static ChronicleSavedData socialData(MinecraftServer server) {
        return RelationshipService.data(server);
    }

    private static void applyConversationRelationship(ChronicleSavedData data, UUID from, UUID toward, int amount,
                                                      String operation, ResourceLocation topic, long today) {
        if (amount == 0) return;
        var definition = RelationshipQualities.byId(RelationshipQualities.AFFECTION);
        data.applyRelationship(from, toward, new RelationshipLedger.Contribution(operation,
                RelationshipQualities.AFFECTION, amount, today, definition.defaultHalfLifeDays(), topic.toString()));
    }
    private static void applyConversationRelationships(ChronicleSavedData data, UUID from, UUID toward,
                                                       List<ConversationTopic.RelationshipChange> changes,
                                                       String operation, ResourceLocation topic, long today) {
        for (ConversationTopic.RelationshipChange change : changes) {
            data.applyRelationship(from, toward, new RelationshipLedger.Contribution(operation,
                    change.quality(), change.amount(), today,
                    change.halfLifeDays() < 0 ? RelationshipQualities.byId(change.quality()).defaultHalfLifeDays() : change.halfLifeDays(),
                    topic.toString()));
        }
    }

    static <T> T choose(List<T> options, java.util.function.ToDoubleFunction<T> weights, Random random) {
        double[] values = new double[options.size()]; double total = 0;
        for (int i = 0; i < values.length; i++) {
            double value = weights.applyAsDouble(options.get(i));
            values[i] = Double.isFinite(value) && value > 0 ? value : 0; total += values[i];
        }
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        for (int i = 0; i < options.size(); i++) { roll -= values[i]; if (roll < 0) return options.get(i); }
        return options.get(options.size() - 1);
    }

    public static String describe(VillagerEntityMCA actor, VillagerEntityMCA other) {
        Runtime runtime = actor.level() instanceof ServerLevel level ? LEVELS.get(level) : null;
        Live live = runtime == null ? null : runtime.members.get(actor.getUUID());
        String current = "none";
        if (live != null) {
            EncounterRun.Binding binding = live.run.binding();
            current = (live.hangout ? "hangout" : "idle") + " lines=" + live.run.lines()
                    + (binding == null ? "" : " topic=" + binding.topic().id()
                    + (binding.subject() == null ? "" : " subject=" + binding.subject().id() + "/" + binding.subject().variant()));
        }
        if (!(actor.level() instanceof ServerLevel level)) return current;
        var history = ConversationSavedData.get(level.getServer()).memory().view(actor.getUUID(), other.getUUID(), level.getGameTime());
        double affection = socialData(level.getServer()).relationships().value(actor.getUUID(), other.getUUID(),
                RelationshipQualities.AFFECTION, TownsteadCalendar.worldDay(level.getServer()));
        return "conversation=" + current + ", available=" + available(actor) + ", affection=" + affection
                + ", meetings=" + history.meetings() + ", memory=" + history.memory() + ", recent=" + history.topics();
    }
}
