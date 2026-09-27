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
    private static final Set<String> METADATA = Set.of("quest", "goal", "skip if", "about", "label", "signal");

    private record Line(String text, List<String> tags) {}

    final ServerPlayer player;
    final VillagerEntityMCA villager;
    final StoryDefinition definition;
    final PlayerStories.Entry entry;
    private final Story story;
    private final Map<String, Goal> checkCache = new HashMap<>();
    /** Visit counts of each quest's hand-back stitches when the conversation opened. */
    private final Map<String, Integer> handBackVisits = new HashMap<>();
    private @Nullable Line buffered;
    private boolean finished;

    private StorySession(ServerPlayer player, VillagerEntityMCA villager, StoryDefinition definition,
                         PlayerStories.Entry entry, Story story) {
        this.player = player;
        this.villager = villager;
        this.definition = definition;
        this.entry = entry;
        this.story = story;
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

    /** Saves the Ink state. Safe to call more than once. */
    void finish() {
        if (finished) return;
        finished = true;
        try {
            entry.ink = story.getState().toJson();
            entry.hash = definition.hash();
        } catch (Exception e) {
            LOGGER.warn("Story {}: could not save state: {}", definition.id(), e.getMessage());
        }
    }

    boolean finished() { return finished; }

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
            Line current = buffered != null ? buffered : pull();
            buffered = null;
            if (current == null) {
                List<String> choices = choiceTexts();
                if (choices.isEmpty()) {
                    end();
                } else {
                    send(StoryS2CPayload.LINE, "", choices, false);
                }
                return;
            }
            stage(current.tags());
            buffered = pull();
            boolean more = buffered != null;
            List<String> choices = more ? List.of() : choiceTexts();
            send(StoryS2CPayload.LINE, current.text(), choices, more);
            if (!more && choices.isEmpty()) {
                finish();
                StoryService.sessionEnded(this);
            }
        } catch (Exception e) {
            fail(e);
        }
    }

    /** Runs the story to its next line of speech, starting quests as their knots are entered. */
    private @Nullable Line pull() throws Exception {
        int guard = 0;
        while (story.canContinue() && guard++ < STEP_LIMIT) {
            String text = story.Continue().trim();
            List<String> tags = new ArrayList<>(story.getCurrentTags());
            StoryService.startEnteredQuests(this, story);
            if (!text.isEmpty()) return new Line(text, tags);
            stage(tags);
        }
        return null;
    }

    private List<String> choiceTexts() {
        List<String> out = new ArrayList<>();
        for (Choice choice : story.getCurrentChoices()) out.add(choice.getText().trim());
        return out;
    }

    private void stage(List<String> tags) {
        for (String tag : tags) {
            int colon = tag.indexOf(':');
            if (colon <= 0) continue;
            String key = tag.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            if (METADATA.contains(key)) continue;
            if (key.equals("emote")) emote(tag.substring(colon + 1).trim());
        }
    }

    private void emote(String raw) {
        if (!(villager.level() instanceof ServerLevel level) || raw.isEmpty()) return;
        List<String> candidates = raw.contains(":") ? List.of(raw)
                : List.of("townstead:reaction_" + raw, "townstead:" + raw);
        for (String candidate : candidates) {
            ResourceLocation id = ResourceLocation.tryParse(candidate);
            if (id == null || PerformanceMappings.targets(id).isEmpty()) continue;
            PerformanceProviders.play(level, new PerformanceRequest(villager, id, "story", 40, 45,
                    PerformanceRequest.Fallback.NONE));
            return;
        }
    }

    private void end() {
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
        StoryService.send(player, new StoryS2CPayload(villager.getId(), kind, text, choices, more));
    }

    // ---- host facts and helpers ----

    private void setHostVariables() {
        String villageName = goalContext().village().map(Village::getName).orElse("");
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        setIfDeclared("player_name", player.getGameProfile().getName());
        setIfDeclared("villager_name", entry.villagerName);
        setIfDeclared("village_name", villageName);
        setIfDeclared("profession", profession == null ? "" : profession.getPath());
    }

    private void setIfDeclared(String name, Object value) {
        try {
            if (story.getVariablesState().get(name) != null) story.getVariablesState().set(name, value);
        } catch (Exception e) {
            LOGGER.debug("Story {}: could not set {}: {}", definition.id(), name, e.getMessage());
        }
    }

    private void bindExternals() throws Exception {
        story.bindExternalFunction("check", args -> check(string(args, 0)));
        story.bindExternalFunction("count", args -> {
            Goal goal = goal(string(args, 0));
            long value = goal == null ? Goal.UNKNOWN : goal.read(goalContext());
            return (int) Math.max(0L, value);
        });
        story.bindExternalFunction("who", args -> who(string(args, 0)));
        story.bindExternalFunction("rel", args -> (int) Math.round(rel(string(args, 0))));
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

    private String who(String role) {
        if (role.equalsIgnoreCase("player")) return player.getGameProfile().getName();
        if (role.equalsIgnoreCase("me")) return entry.villagerName;
        return StoryWorld.who(role, goalContext());
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
