package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.naming.VillagerNames;
import com.aetherianartificer.townstead.performance.PerformanceMappings;
import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.social.RelationshipService;
import com.aetherianartificer.townstead.story.goal.Goal;
import com.aetherianartificer.townstead.story.goal.GoalContext;
import com.aetherianartificer.townstead.story.goal.Goals;
import com.aetherianartificer.townstead.story.net.StoryS2CPayload;
import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One open conversation between a player and a villager about a story. It runs its own copy of
 * the compiled Ink, sends one line at a time, and saves the Ink state back to the player when the
 * conversation ends.
 */
final class StorySession {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Stories");
    private static final int STEP_LIMIT = 500;
    /** Tags that describe a knot for the host rather than stage a line. */
    /** The choice tag that marks the way forward; the screen lists those choices first, with a marker. */
    static final String ADVANCE_TAG = "advance";
    private static final Set<String> METADATA = Set.of("quest", "goal", "skip if", "about", "label", "signal", "who", "overheard");
    /** Splits who said a saved resume line from its text. */
    private static final char WHO_SEPARATOR = '';

    private record Line(String text, List<String> tags) {}

    final ServerPlayer player;
    final VillagerEntityMCA villager;
    final StoryDefinition definition;
    final PlayerStories.Entry entry;
    private final Story story;
    private final Map<String, Goal> checkCache = new HashMap<>();
    private final StoryCast cast = new StoryCast(this);
    /** Who said the line on screen: the villager the player is talking to, or someone in the cast. */
    private VillagerEntityMCA speaker;
    /** Visit counts of each quest's hand-back stitches when the conversation opened. */
    private final Map<String, Integer> handBackVisits = new HashMap<>();
    private @Nullable Line buffered;
    /** The last line the player was shown, kept so a conversation left mid-way can pick up again. */
    private @Nullable Line shown;
    /** Lines to show before the story moves on, when a conversation picks up where it was left. */
    private final java.util.ArrayDeque<Line> replay = new java.util.ArrayDeque<>();
    private boolean finished;

    private StorySession(ServerPlayer player, VillagerEntityMCA villager, StoryDefinition definition,
                         PlayerStories.Entry entry, Story story) {
        this.player = player;
        this.villager = villager;
        this.definition = definition;
        this.entry = entry;
        this.story = story;
        this.speaker = villager;
    }

    static StorySession open(ServerPlayer player, VillagerEntityMCA villager, StoryDefinition definition,
                             PlayerStories.Entry entry) throws Exception {
        Story story = new Story(definition.compiledJson());
        story.setAllowExternalFunctionFallbacks(false);
        if (!entry.ink.isEmpty()) {
            try {
                story.getState().loadJson(entry.ink);
            } catch (Exception e) {
                // A datapack update changed the story under the save. Quests live outside Ink, so
                // starting the Ink side fresh only loses choices remembered in Ink variables.
                LOGGER.warn("Story {}: saved state for {} no longer fits; starting it fresh ({})",
                        definition.id(), player.getGameProfile().getName(), e.getMessage());
                story.resetState();
            }
        }
        StorySession session = new StorySession(player, villager, definition, entry, story);
        for (StoryDefinition.Quest quest : definition.quests().values()) {
            session.handBackVisits.put(quest.knot(), handBackVisits(quest, story));
        }
        session.bindExternals();
        session.setHostVariables();
        return session;
    }

    GoalContext goalContext() {
        return new GoalContext(player.server, player.getUUID(), villager.getUUID(), entry.villagerName, villager);
    }

    void start(String path) {
        try {
            story.choosePathString(path);
        } catch (Exception e) {
            fail(e);
            return;
        }
        step();
    }

    void next() {
        if (!finished) step();
    }

    /**
     * Picks up a conversation the player left mid-way: the story's own {@code resumed()} line if it
     * has one, the last line they saw, the line they had not seen yet, then on from there.
     */
    void resume() {
        try {
            if (story.hasFunction("resumed")) {
                Object bridge = story.evaluateFunction("resumed");
                if (bridge != null && !bridge.toString().isBlank()) replay.add(new Line(bridge.toString().trim(), List.of()));
            }
        } catch (Exception e) {
            LOGGER.warn("Story {}: resumed() failed: {}", definition.id(), e.getMessage());
        }
        for (String line : entry.resumeLines) {
            int split = line.indexOf(WHO_SEPARATOR);
            replay.add(split < 0 ? new Line(line, List.of())
                    : new Line(line.substring(split + 1), List.of("who: " + line.substring(0, split))));
        }
        entry.interrupted = false;
        entry.resumeLines.clear();
        step();
    }

    /** Whether the player would lose something by leaving now: more lines, or choices waiting. */
    boolean midway() {
        return !finished && (buffered != null || !replay.isEmpty() || story.canContinue() || !story.getCurrentChoices().isEmpty());
    }

    /** Saves the state of a conversation the player is leaving mid-way, so they can return to it. */
    void interrupt() {
        if (!midway()) return;
        entry.interrupted = true;
        entry.resumeLines.clear();
        if (shown != null && !shown.text().isEmpty()) entry.resumeLines.add(saved(shown));
        for (Line line : replay) entry.resumeLines.add(saved(line));
        if (buffered != null) entry.resumeLines.add(saved(buffered));
        finish();
    }

    void choose(int index) {
        if (finished || buffered != null) return;
        try {
            if (index < 0 || index >= story.getCurrentChoices().size()) return;
            story.chooseChoiceIndex(index);
        } catch (Exception e) {
            fail(e);
            return;
        }
        step();
    }

    private static String saved(Line line) {
        String who = who(line.tags());
        return who.isEmpty() ? line.text() : who + WHO_SEPARATOR + line.text();
    }

    /** A line said in chat, for a scene the player overhears. */
    record Spoken(String text, VillagerEntityMCA by) {}

    /** Moves the story to a knot without saying anything yet, for a scene played in chat. */
    boolean begin(String path) {
        try {
            story.choosePathString(path);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Story {}: could not start {}: {}", definition.id(), path, e.getMessage());
            return false;
        }
    }

    /** Whether the player has already been through this knot. */
    boolean visited(String knot) {
        try {
            return story.getState().visitCountAtPathString(knot) > 0;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * The next line of a scene played in chat and who says it, or null at the end. Chat has no
     * choices, so a choice ends the scene too.
     */
    @Nullable Spoken nextSpoken() {
        if (finished) return null;
        try {
            Line current = nextLine();
            VillagerEntityMCA by = current == null ? null : speakerOf(current);
            while (current != null && by == null) {
                current = nextLine();
                by = current == null ? null : speakerOf(current);
            }
            if (current == null) return null;
            speaker = by;
            stage(current.tags(), by);
            shown = current;
            return new Spoken(current.text(), by);
        } catch (Exception e) {
            LOGGER.warn("Story {}: overheard scene stopped: {}", definition.id(), e.getMessage());
            return null;
        }
    }

    /** Ends without saving, for a scene that turned out to have nothing to say. */
    void discard() {
        finished = true;
        cast.release();
    }

    /** Keeps everyone in a scene played in chat where they stand, facing whoever is talking. */
    void holdInPlace() {
        if (!finished) cast.holdInPlace(speaker);
    }

    /** Keeps the rest of the cast in the scene, facing whoever is talking. Called every tick. */
    void holdCast() {
        if (!finished) cast.hold(speaker);
    }

    /** Saves the Ink state. Safe to call more than once. */
    void finish() {
        if (finished) return;
        finished = true;
        cast.release();
        try {
            entry.ink = story.getState().toJson();
            entry.hash = definition.hash();
        } catch (Exception e) {
            LOGGER.warn("Story {}: could not save state: {}", definition.id(), e.getMessage());
        }
    }

    boolean finished() { return finished; }

    /**
     * The menu entry the story's own {@code menu()} function gives right now, or null when it has
     * none or gives nothing. Reads the story without moving it on.
     */
    /** The story's own {@code greeting()} line, said in place of MCA's greeting; null when it has none. */
    @Nullable String greetingLine() {
        try {
            if (!story.hasFunction("greeting")) return null;
            Object line = story.evaluateFunction("greeting");
            String text = line == null ? "" : line.toString().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            LOGGER.warn("Story {}: greeting() failed: {}", definition.id(), e.getMessage());
            return null;
        }
    }

    @Nullable String menuLabel() {
        try {
            if (!story.hasFunction("menu")) return null;
            Object label = story.evaluateFunction("menu");
            String text = label == null ? "" : label.toString().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            LOGGER.warn("Story {}: menu() failed: {}", definition.id(), e.getMessage());
            return null;
        }
    }

    /** Whether this conversation has told the quest's done or skipped lines. */
    boolean toldHandBack(StoryDefinition.Quest quest, Story story) throws Exception {
        return handBackVisits(quest, story) > handBackVisits.getOrDefault(quest.knot(), 0);
    }

    private static int handBackVisits(StoryDefinition.Quest quest, Story story) throws Exception {
        int visits = 0;
        if (quest.hasDone()) visits += story.getState().visitCountAtPathString(quest.knot() + ".done");
        if (quest.hasSkipped()) visits += story.getState().visitCountAtPathString(quest.knot() + ".skipped");
        return visits;
    }

    private void step() {
        try {
            Line current = nextLine();
            VillagerEntityMCA by = current == null ? null : speakerOf(current);
            while (current != null && by == null) {
                // Nobody near fits the line's speaker. Scenes check here() first; this covers the rest.
                LOGGER.warn("Story {}: nobody near to say a line for '{}'; skipping it", definition.id(), who(current.tags()));
                current = nextLine();
                by = current == null ? null : speakerOf(current);
            }
            if (current == null) {
                List<String> choices = choiceTexts();
                if (choices.isEmpty()) {
                    end();
                } else {
                    send(StoryS2CPayload.LINE, "", choices, false, speaker);
                }
                return;
            }
            speaker = by;
            stage(current.tags(), by);
            shown = current;
            if (replay.isEmpty() && buffered == null) buffered = pull();
            boolean more = !replay.isEmpty() || buffered != null;
            List<String> choices = more ? List.of() : choiceTexts();
            send(StoryS2CPayload.LINE, current.text(), choices, more, by);
            if (!more && choices.isEmpty()) {
                finish();
                StoryService.sessionEnded(this);
            }
        } catch (Exception e) {
            fail(e);
        }
    }

    private @Nullable Line nextLine() throws Exception {
        if (!replay.isEmpty()) return replay.poll();
        if (buffered != null) {
            Line line = buffered;
            buffered = null;
            return line;
        }
        return pull();
    }

    private @Nullable VillagerEntityMCA speakerOf(Line line) {
        return cast.find(who(line.tags()));
    }

    /** The {@code # who:} value of a line, or empty for the villager the player is talking to. */
    private static String who(List<String> tags) {
        for (String tag : tags) {
            int colon = tag.indexOf(':');
            if (colon > 0 && tag.substring(0, colon).trim().equalsIgnoreCase("who")) return tag.substring(colon + 1).trim();
        }
        return "";
    }

    /** Runs the story to its next line of speech, starting quests as their knots are entered. */
    private @Nullable Line pull() throws Exception {
        int guard = 0;
        while (story.canContinue() && guard++ < STEP_LIMIT) {
            String text = story.Continue().trim();
            List<String> tags = new ArrayList<>(story.getCurrentTags());
            StoryService.startEnteredQuests(this, story);
            if (!text.isEmpty()) return new Line(text, tags);
            VillagerEntityMCA by = cast.find(who(tags));
            stage(tags, by == null ? villager : by);
        }
        return null;
    }

    /** A bit for each current choice tagged {@code # advance}: the one that moves the story on. */
    private int questChoices() {
        int mask = 0;
        List<Choice> choices = story.getCurrentChoices();
        for (int i = 0; i < choices.size() && i < 32; i++) {
            List<String> tags = choices.get(i).getTags();
            if (tags != null && tags.stream().anyMatch(t -> t.trim().equalsIgnoreCase(ADVANCE_TAG))) mask |= 1 << i;
        }
        return mask;
    }

    private List<String> choiceTexts() {
        List<String> out = new ArrayList<>();
        for (Choice choice : story.getCurrentChoices()) out.add(choice.getText().trim());
        return out;
    }

    private void stage(List<String> tags, VillagerEntityMCA by) {
        for (String tag : tags) {
            int colon = tag.indexOf(':');
            if (colon <= 0) continue;
            String key = tag.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            if (METADATA.contains(key)) continue;
            if (key.equals("emote")) emote(tag.substring(colon + 1).trim(), by);
        }
    }

    private void emote(String raw, VillagerEntityMCA by) {
        if (!(by.level() instanceof ServerLevel level) || raw.isEmpty()) return;
        List<String> candidates = raw.contains(":") ? List.of(raw)
                : List.of("townstead:reaction_" + raw, "townstead:" + raw);
        for (String candidate : candidates) {
            ResourceLocation id = ResourceLocation.tryParse(candidate);
            if (id == null || PerformanceMappings.targets(id).isEmpty()) continue;
            PerformanceProviders.play(level, new PerformanceRequest(by, id, "story", 40, 45,
                    PerformanceRequest.Fallback.NONE));
            return;
        }
    }

    private void end() {
        entry.interrupted = false;
        entry.resumeLines.clear();
        finish();
        send(StoryS2CPayload.END, "", List.of(), false);
        StoryService.sessionEnded(this);
    }

    private void fail(Exception e) {
        LOGGER.warn("Story {} stopped for {}: {}", definition.id(), player.getGameProfile().getName(), e.getMessage());
        finished = true;
        send(StoryS2CPayload.END, "", List.of(), false);
        StoryService.sessionEnded(this);
    }

    private void send(byte kind, String text, List<String> choices, boolean more) {
        send(kind, text, choices, more, speaker);
    }

    private void send(byte kind, String text, List<String> choices, boolean more, VillagerEntityMCA by) {
        StoryService.send(player, new StoryS2CPayload(villager.getId(), kind, text, choices, more, by.getId(),
                com.aetherianartificer.townstead.persona.PersonaService.dialogueTheme(by), choices.isEmpty() ? 0 : questChoices()));
    }

    // ---- host facts and helpers ----

    private void setHostVariables() {
        String villageName = goalContext().village().map(Village::getName).orElse("");
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        setIfDeclared("player_name", player.getGameProfile().getName());
        setIfDeclared("villager_name", entry.villagerName);
        setIfDeclared("village_name", villageName);
        setIfDeclared("profession", profession == null ? "" : profession.getPath());
        setIfDeclared("today", (int) Math.min(Integer.MAX_VALUE, TownsteadCalendar.worldDay(player.server)));
        setIfDeclared("quest_ready", entry.quests.values().stream().anyMatch(q -> q.state == PlayerStories.QuestState.READY));
        setIfDeclared("quest_open", entry.quests.values().stream().anyMatch(q -> q.state == PlayerStories.QuestState.ACTIVE));
        setIfDeclared("interrupted", entry.interrupted);
        // A fresh 1 to 100 each time the conversation opens: Ink's RANDOM repeats itself from a saved state.
        setIfDeclared("chance", player.getRandom().nextInt(100) + 1);
        var instance = com.aetherianartificer.townstead.persona.PersonaInstances.get(player.server).of(villager.getUUID());
        var persona = instance == null ? null : com.aetherianartificer.townstead.persona.Personas.byId(instance.persona());
        if (persona == null) return;
        for (var rolled : com.aetherianartificer.townstead.persona.PersonaService.rolls(persona, player).entrySet()) {
            setIfDeclared(rolled.getKey(), rolled.getValue().value());
            rolled.getValue().vars().forEach(this::setIfDeclared);
        }
        com.aetherianartificer.townstead.persona.PersonaService.identity(persona, player).vars().forEach(this::setIfDeclared);
    }

    void setIfDeclared(String name, Object value) {
        try {
            if (story.getVariablesState().get(name) != null) story.getVariablesState().set(name, value);
        } catch (Exception e) {
            LOGGER.debug("Story {}: could not set {}: {}", definition.id(), name, e.getMessage());
        }
    }

    private void bindExternals() throws Exception {
        bindRead("check", args -> check(string(args, 0)));
        bindRead("count", args -> {
            Goal goal = goal(string(args, 0));
            if (goal != null && goal.isCounter()) return (int) Math.min(Integer.MAX_VALUE, entry.seen.getOrDefault(string(args, 0), 0L));
            long value = goal == null ? Goal.UNKNOWN : goal.read(goalContext());
            return (int) Math.max(0L, value);
        });
        bindRead("who", args -> who(string(args, 0)));
        bindRead("here", args -> cast.find(string(args, 0)) != null);
        bindRead("career_path", args -> careerPath());
        bindRead("is", args -> is(string(args, 0), string(args, 1)));
        bindRead("building", args -> StoryWorld.building(string(args, 0), goalContext()));
        bindRead("roll", args -> {
            // Rolls it now when the Persona has not rolled it yet, so one Persona can talk about
            // another before they arrive.
            ResourceLocation id = ResourceLocation.tryParse(string(args, 0));
            var persona = id == null ? null : com.aetherianartificer.townstead.persona.Personas.byId(id);
            if (persona == null) return "";
            var option = com.aetherianartificer.townstead.persona.PersonaService.rolls(persona, player).get(string(args, 1));
            return option == null ? "" : option.value();
        });
        bindRead("most_harvested", args -> goalContext().villageId(true)
                .map(v -> com.aetherianartificer.townstead.village.HarvestTally.get(player.server).most(v))
                .map(StoryText::item).orElse(""));
        bindRead("persona_name", args -> {
            ResourceLocation persona = ResourceLocation.tryParse(string(args, 0));
            if (persona == null) return "";
            var village = goalContext().villageId(true);
            return com.aetherianartificer.townstead.persona.PersonaService.nameOf(player.server, persona,
                    village.map(v -> v.dimension()).orElse(null), village.map(v -> v.villageId()).orElse(-1));
        });
        bindRead("rel", args -> (int) Math.round(rel(string(args, 0))));
        story.bindExternalFunction("trust", args -> {
            contribute("trust", number(args, 0), "trust");
            return null;
        }, false);
        story.bindExternalFunction("contribute", args -> {
            contribute(string(args, 0), number(args, 1), string(args, 2));
            return null;
        }, false);
        story.bindExternalFunction("memory", args -> {
            memory(string(args, 0));
            return null;
        }, false);
        story.bindExternalFunction("act", args -> {
            act(string(args, 0));
            return null;
        }, false);
        bindRead("mod", args -> com.aetherianartificer.townstead.compat.ModCompat.isLoaded(string(args, 0)));
        bindRead("can_build", args -> com.aetherianartificer.townstead.pheno.condition.types.CanBuildConditionType.possible(string(args, 0).toLowerCase(java.util.Locale.ROOT)));
        bindRead("demeanor", args -> Demeanor.of(definition.demeanor(), this::rel));
        bindRead("contract_offer", args -> pool(args) == null ? ""
                : com.aetherianartificer.townstead.contract.Contracts.title(player,
                        com.aetherianartificer.townstead.contract.Contracts.offer(player, villager, pool(args))));
        bindRead("contract_about", args -> pool(args) == null ? ""
                : com.aetherianartificer.townstead.contract.Contracts.about(player,
                        com.aetherianartificer.townstead.contract.Contracts.offer(player, villager, pool(args))));
        bindRead("contract_skip", args -> {
            if (pool(args) != null) com.aetherianartificer.townstead.contract.Contracts.skip(player, pool(args));
            return true;
        });
        bindRead("contract_accept", args -> pool(args) != null
                && com.aetherianartificer.townstead.contract.Contracts.accept(player, villager, pool(args)));
        bindRead("contract_ready", args -> pool(args) == null ? 0
                : com.aetherianartificer.townstead.contract.Contracts.ready(player, pool(args)));
        bindRead("contract_active", args -> pool(args) == null ? 0
                : com.aetherianartificer.townstead.contract.Contracts.active(player, pool(args)));
        bindRead("contract_turn_in", args -> pool(args) == null ? 0
                : com.aetherianartificer.townstead.contract.Contracts.turnIn(player, villager, pool(args)));
    }

    /**
     * Binds a helper that only reads the world, so Ink can call it inside choice text and string
     * building, as in {@code [How do you know {builder()}?]}. blade-ink 1.3.2 has that check the
     * wrong way round: it refuses functions bound lookahead-safe (the default) while building a
     * string. Binding them as not lookahead-safe lets string building call them, and makes the
     * runtime wait on them during lookahead, which is correct for anything that reads the world.
     */
    private void bindRead(String name, com.bladecoder.ink.runtime.Story.ExternalFunction<?> function) throws Exception {
        story.bindExternalFunction(name, function, false);
    }

    private boolean check(String what) {
        Condition condition = definition.conditions().get(what);
        if (condition != null) return condition.test(new ConditionContext(villager, player));
        Goal goal = goal(what);
        if (goal == null || goal.isCounter()) return false;
        long value = goal.read(goalContext());
        return value != Goal.UNKNOWN && value >= goal.total();
    }

    /** A goal named in Ink, from story.json or the goal library. */
    private @Nullable Goal goal(String reference) {
        return checkCache.computeIfAbsent(reference, key -> {
            Goals.Parsed parsed = Goals.resolve(key, definition.id().getNamespace(), definition.goals());
            if (parsed.goal() == null) LOGGER.warn("Story {}: '{}': {}", definition.id(), key, parsed.error());
            return parsed.goal();
        });
    }

    /**
     * {@code is(name, "condition")}: whether the resident of the teller's village with that name
     * matches a condition the story names. With an empty condition, whether they are still there.
     */
    private boolean is(String name, String what) {
        VillagerEntityMCA resident = StoryWorld.named(name, goalContext());
        if (resident == null) return false;
        if (what.isBlank()) return true;
        Condition condition = definition.conditions().get(what);
        return condition != null && condition.test(new ConditionContext(resident, player));
    }

    private String who(String role) {
        if (role.equalsIgnoreCase("player")) return player.getGameProfile().getName();
        if (role.equalsIgnoreCase("me")) return entry.villagerName;
        // A condition the story names: the first resident it holds for.
        Condition condition = definition.conditions().get(role);
        if (condition != null) return StoryWorld.whoMatching(condition, player, goalContext());
        return StoryWorld.who(role, goalContext());
    }

    /**
     * {@code career_path()}: the career path the teller has learned the most skills in, such as
     * {@code "tiller"}, or empty before their first path skill. Path skills are named
     * {@code <ns>:<profession>/<path>/<skill>}.
     */
    private String careerPath() {
        Map<String, Integer> counts = new HashMap<>();
        for (ResourceLocation skill : com.aetherianartificer.townstead.profession.skill.LearnedSkills.learned(villager)) {
            String[] parts = skill.getPath().split("/");
            if (parts.length == 3) counts.merge(parts[1], 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private static String quality(String raw) {
        return raw.contains(":") ? raw : "townstead:" + raw;
    }

    private double rel(String quality) {
        long day = TownsteadCalendar.worldDay(player.server);
        return RelationshipService.data(player.server).relationships()
                .value(villager.getUUID(), player.getUUID(), quality(quality), day);
    }

    private void contribute(String quality, float amount, String reason) {
        long day = TownsteadCalendar.worldDay(player.server);
        RelationshipService.apply(player.server, villager.getUUID(), player.getUUID(), operationId(),
                quality(quality), amount, day, -1, "townstead:story/" + definition.id() + "/" + reason);
    }

    private void memory(String id) {
        long day = TownsteadCalendar.worldDay(player.server);
        RelationshipService.data(player.server).addEpisodicMemory(villager.getUUID(), operationId(),
                quality(id), player.getUUID(), day, "townstead:story/" + definition.id(), Map.of());
    }

    private void act(String name) {
        Action action = definition.actions().get(name);
        if (action == null) {
            LOGGER.warn("Story {}: act(\"{}\"): no such action in story.json", definition.id(), name);
            return;
        }
        action.run(new ActionContext(villager, player));
    }

    private String operationId() {
        entry.operations++;
        return "story:" + definition.id() + ":" + villager.getUUID() + ":" + player.getUUID() + ":" + entry.operations;
    }

    private static @Nullable ResourceLocation pool(Object[] args) {
        return ResourceLocation.tryParse(string(args, 0));
    }

    private static String string(Object[] args, int index) {
        return args.length > index && args[index] != null ? String.valueOf(args[index]) : "";
    }

    private static float number(Object[] args, int index) {
        if (args.length <= index) return 0f;
        Object value = args[index];
        if (value instanceof Number n) return n.floatValue();
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    static String displayName(VillagerEntityMCA villager) {
        return VillagerNames.display(villager).getString();
    }
}
