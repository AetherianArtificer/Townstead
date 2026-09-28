package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.story.goal.Goal;
import com.aetherianartificer.townstead.story.goal.Goals;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Loaded stories, from {@code data/<ns>/story/<id>/}. Every {@code .ink} file in a folder is
 * compiled together on reload; {@code story.json} beside them says who tells it. Problems are
 * kept per story so {@code /townstead story errors} can list them as file and line.
 */
public final class Stories {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Stories");
    private static final String FOLDER = "story";
    private static final String PERSONA_FOLDER = "persona";
    private static final String GOAL_FOLDER = "goal";
    /** Key under which the loader hands the goal library to {@code apply}. */
    private static final ResourceLocation GOAL_LIBRARY = ResourceLocation.tryParse("townstead:__goal_library__");
    private static final String REWARD_FOLDER = "reward";
    private static final ResourceLocation REWARD_LIBRARY = ResourceLocation.tryParse("townstead:__reward_library__");
    /** Translations sit beside the English file ({@code story.fr_fr.ink}) and are not compiled in. */
    private static final Pattern LOCALIZED = Pattern.compile(".*\\.[a-z]{2,3}_[a-z]{2,3}\\.ink$");

    private static volatile Map<ResourceLocation, StoryDefinition> stories = Map.of();
    private static volatile Map<ResourceLocation, List<String>> problems = Map.of();

    private Stories() {}

    public static Map<ResourceLocation, StoryDefinition> all() { return stories; }

    public static @Nullable StoryDefinition byId(ResourceLocation id) { return stories.get(id); }

    /** Errors and warnings from the last reload, by story. */
    public static Map<ResourceLocation, List<String>> problems() { return problems; }

    /** The story this villager tells the player, if any. Highest priority wins, then id order. */
    public static @Nullable StoryDefinition forVillager(VillagerEntityMCA villager, Player player) {
        StoryDefinition best = null;
        for (StoryDefinition story : stories.values()) {
            if (!story.attach().matches(villager, player)) continue;
            if (best == null || story.priority() > best.priority()) best = story;
        }
        return best;
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, Map<String, String>>> {
        @Override
        protected Map<ResourceLocation, Map<String, String>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, Map<String, String>> folders = new TreeMap<>();
            readStoryFolders(manager, FOLDER, "", folders);
            readStoryFolders(manager, PERSONA_FOLDER, "persona/", folders);
            folders.put(GOAL_LIBRARY, readJsonFolder(manager, GOAL_FOLDER));
            folders.put(REWARD_LIBRARY, readJsonFolder(manager, REWARD_FOLDER));
            return folders;
        }

        @Override
        protected void apply(Map<ResourceLocation, Map<String, String>> folders, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, StoryDefinition> next = new LinkedHashMap<>();
            Map<ResourceLocation, List<String>> nextProblems = new LinkedHashMap<>();
            Map<ResourceLocation, com.aetherianartificer.townstead.persona.PersonaDefinition> nextPersonas = new LinkedHashMap<>();
            loadGoalLibrary(folders.getOrDefault(GOAL_LIBRARY, Map.of()), nextProblems);
            loadRewardLibrary(folders.getOrDefault(REWARD_LIBRARY, Map.of()), nextProblems);
            for (Map.Entry<ResourceLocation, Map<String, String>> folder : folders.entrySet()) {
                if (folder.getKey().equals(GOAL_LIBRARY) || folder.getKey().equals(REWARD_LIBRARY)) continue;
                List<String> issues = new ArrayList<>();
                ResourceLocation persona = folder.getKey().getPath().startsWith("persona/")
                        ? ResourceLocation.tryParse(folder.getKey().getNamespace() + ":" + folder.getKey().getPath().substring(8))
                        : null;
                StoryDefinition story = build(folder.getKey(), folder.getValue(), issues, persona, nextPersonas);
                if (story != null) next.put(story.id(), story);
                if (!issues.isEmpty()) {
                    nextProblems.put(folder.getKey(), List.copyOf(issues));
                    for (String issue : issues) LOGGER.warn("Story {}: {}", folder.getKey(), issue);
                }
            }
            stories = Map.copyOf(next);
            problems = Map.copyOf(nextProblems);
            com.aetherianartificer.townstead.persona.Personas.set(nextPersonas);
            if (!nextPersonas.isEmpty()) LOGGER.info("Loaded {} Personas", nextPersonas.size());
            LOGGER.info("Loaded {} stories", next.size());
            StoryService.onReload();
        }
    }

    /** Every story-shaped folder under {@code data/<ns>/<folder>/<id>/}, keyed {@code ns:<prefix><id>}. */
    private static void readStoryFolders(ResourceManager manager, String folder, String prefix,
                                         Map<ResourceLocation, Map<String, String>> out) {
        Map<ResourceLocation, Resource> found = manager.listResources(folder,
                path -> path.getPath().endsWith(".ink") || path.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            String path = entry.getKey().getPath().substring(folder.length() + 1);
            int slash = path.indexOf('/');
            if (slash <= 0) continue;
            String relative = path.substring(slash + 1);
            if (LOCALIZED.matcher(relative).matches()) continue;
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey().getNamespace() + ":" + prefix + path.substring(0, slash));
            if (id == null) continue;
            try (Reader reader = entry.getValue().openAsReader(); BufferedReader buffered = new BufferedReader(reader)) {
                StringBuilder text = new StringBuilder();
                buffered.lines().forEach(line -> text.append(line).append('\n'));
                out.computeIfAbsent(id, ignored -> new TreeMap<>()).put(relative, text.toString());
            } catch (Exception e) {
                LOGGER.warn("Failed to read {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /** Every {@code .json} under {@code data/<ns>/<folder>/}, by id. */
    private static Map<String, String> readJsonFolder(ResourceManager manager, String folder) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(folder,
                path -> path.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":" + path.substring(folder.length() + 1, path.length() - 5);
            try (Reader reader = entry.getValue().openAsReader(); BufferedReader buffered = new BufferedReader(reader)) {
                StringBuilder text = new StringBuilder();
                buffered.lines().forEach(line -> text.append(line).append('\n'));
                out.put(id, text.toString());
            } catch (Exception e) {
                LOGGER.warn("Failed to read {}: {}", entry.getKey(), e.getMessage());
            }
        }
        return out;
    }

    /** Shared rewards from {@code data/<ns>/reward/}. Rewards without parameters are checked now. */
    private static void loadRewardLibrary(Map<String, String> files, Map<ResourceLocation, List<String>> problems) {
        Map<ResourceLocation, JsonObject> library = new LinkedHashMap<>();
        Map<ResourceLocation, List<String>> found = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(file.getKey());
            if (id == null) continue;
            try {
                JsonElement parsed = JsonParser.parseString(file.getValue());
                if (!parsed.isJsonObject()) throw new IllegalArgumentException("the root must be an object");
                library.put(id, parsed.getAsJsonObject());
            } catch (Exception e) {
                found.put(id, List.of("error: reward file: " + e.getMessage()));
            }
        }
        com.aetherianartificer.townstead.story.reward.Rewards.setLibrary(library);
        for (Map.Entry<ResourceLocation, JsonObject> reward : library.entrySet()) {
            if (reward.getValue().has("params")) continue;
            var parsed = com.aetherianartificer.townstead.story.reward.Rewards.resolve(
                    reward.getKey().toString(), reward.getKey().getNamespace(), Map.of());
            if (parsed.reward() == null) found.put(reward.getKey(), List.of("error: reward: " + parsed.error()));
        }
        found.forEach((id, issues) -> issues.forEach(issue -> LOGGER.warn("Reward {}: {}", id, issue)));
        problems.putAll(found);
    }

    /** Shared goals from {@code data/<ns>/goal/}. Goals without parameters are checked now. */
    private static void loadGoalLibrary(Map<String, String> files, Map<ResourceLocation, List<String>> problems) {
        Map<ResourceLocation, JsonObject> library = new LinkedHashMap<>();
        Map<ResourceLocation, List<String>> found = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : files.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(file.getKey());
            if (id == null) continue;
            try {
                JsonElement parsed = JsonParser.parseString(file.getValue());
                if (!parsed.isJsonObject()) throw new IllegalArgumentException("the root must be an object");
                library.put(id, parsed.getAsJsonObject());
            } catch (Exception e) {
                found.put(id, List.of("error: goal file: " + e.getMessage()));
            }
        }
        Goals.setLibrary(library);
        for (Map.Entry<ResourceLocation, JsonObject> goal : library.entrySet()) {
            if (goal.getValue().has("params")) continue;
            Goals.Parsed parsed = Goals.resolve(goal.getKey().toString(), goal.getKey().getNamespace(), Map.of());
            if (parsed.goal() == null) found.put(goal.getKey(), List.of("error: goal: " + parsed.error()));
        }
        found.forEach((id, issues) -> issues.forEach(issue -> LOGGER.warn("Goal {}: {}", id, issue)));
        problems.putAll(found);
    }

    static @Nullable StoryDefinition build(ResourceLocation id, Map<String, String> files, List<String> issues) {
        return build(id, files, issues, null, new LinkedHashMap<>());
    }

    /**
     * Builds one story. For a Persona folder, {@code persona} is its id: {@code persona.json}
     * takes the place of {@code story.json}, the story attaches to that Persona's villagers, and
     * the Persona itself is parsed into {@code personas}.
     */
    static @Nullable StoryDefinition build(ResourceLocation id, Map<String, String> files, List<String> issues,
                                           @Nullable ResourceLocation persona,
                                           Map<ResourceLocation, com.aetherianartificer.townstead.persona.PersonaDefinition> personas) {
        String jsonFile = persona == null ? "story.json" : "persona.json";
        Map<String, String> ink = new LinkedHashMap<>();
        files.entrySet().stream()
                .filter(e -> e.getKey().endsWith(".ink"))
                .sorted(Comparator.comparing((Map.Entry<String, String> e) -> !e.getKey().equals("story.ink"))
                        .thenComparing(Map.Entry::getKey))
                .forEach(e -> ink.put(e.getKey(), e.getValue()));
        if (ink.isEmpty()) {
            issues.add("error: no .ink files");
            return null;
        }
        StoryCompiler.Result compiled = StoryCompiler.compile(ink);
        compiled.warnings().forEach(w -> issues.add("warning: " + w));
        if (!compiled.ok()) {
            compiled.errors().forEach(e -> issues.add("error: " + e));
            return null;
        }
        int errorsBefore = countErrors(issues);
        StoryCompiler.Knot greet = compiled.knots().get("greet");
        if (greet == null) issues.add("error: no '=== greet ===' knot; it is where every conversation starts");

        JsonObject json = null;
        String rawJson = files.get(jsonFile);
        if (rawJson != null) {
            try {
                JsonElement parsed = JsonParser.parseString(rawJson);
                if (parsed.isJsonObject()) json = parsed.getAsJsonObject();
                else issues.add("error: " + jsonFile + ": the root must be an object");
            } catch (Exception e) {
                issues.add("error: " + jsonFile + ": " + e.getMessage());
            }
        }
        Map<String, JsonObject> goals = new LinkedHashMap<>();
        if (json != null && json.has("goals") && json.get("goals").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("goals").entrySet()) {
                if (e.getValue().isJsonObject()) goals.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject());
                else issues.add("error: story.json: goal '" + e.getKey() + "' must be an object");
            }
        }

        Map<String, JsonObject> rewards = new LinkedHashMap<>();
        if (json != null && json.has("rewards") && json.get("rewards").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("rewards").entrySet()) {
                if (e.getValue().isJsonObject()) rewards.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsJsonObject());
                else issues.add("error: story.json: reward '" + e.getKey() + "' must be an object");
            }
        }

        Map<String, StoryDefinition.Quest> quests = new LinkedHashMap<>();
        Set<String> titles = new HashSet<>();
        for (StoryCompiler.Knot knot : compiled.knots().values()) {
            Map<String, List<String>> tags = tagMap(knot.tags());
            List<String> questTag = tags.get("quest");
            if (questTag == null) continue;
            String title = questTag.get(0);
            if (!titles.add(title)) issues.add("warning: two quests are titled '" + title + "'");
            List<Goal> questGoals = new ArrayList<>();
            for (String reference : tags.getOrDefault("goal", List.of())) {
                Goals.Parsed parsed = Goals.resolve(reference, id.getNamespace(), goals);
                if (parsed.goal() == null) issues.add("error: quest '" + knot.name() + "' goal '" + reference + "': " + parsed.error());
                else questGoals.add(parsed.goal());
            }
            List<com.aetherianartificer.townstead.story.reward.Reward> questRewards = new ArrayList<>();
            for (String reference : tags.getOrDefault("reward", List.of())) {
                var parsed = com.aetherianartificer.townstead.story.reward.Rewards.resolve(reference, id.getNamespace(), rewards);
                if (parsed.reward() == null) issues.add("error: quest '" + knot.name() + "' reward '" + reference + "': " + parsed.error());
                else questRewards.add(parsed.reward());
            }
            Goal skipIf = null;
            List<String> skip = tags.get("skip if");
            if (skip != null) {
                Goals.Parsed parsed = Goals.resolve(skip.get(0), id.getNamespace(), goals);
                if (parsed.goal() == null) issues.add("error: quest '" + knot.name() + "' skip if: " + parsed.error());
                else if (parsed.goal().isCounter()) issues.add("error: quest '" + knot.name() + "' skip if: '" + skip.get(0) + "' counts events, so it is never already true");
                else skipIf = parsed.goal();
            }
            quests.put(knot.name(), new StoryDefinition.Quest(knot.name(), title,
                    first(tags, "about", ""), List.copyOf(questGoals), skipIf,
                    knot.stitches().contains("done"), knot.stitches().contains("skipped"),
                    knot.stitches().contains("waiting"), first(tags, "label", null), List.copyOf(questRewards)));
        }

        StoryAttach attach;
        com.aetherianartificer.townstead.persona.PersonaDefinition personaDefinition = null;
        if (persona != null) {
            attach = StoryAttach.persona(persona);
            personaDefinition = com.aetherianartificer.townstead.persona.Personas.parse(persona,
                    json == null ? new JsonObject() : json, goals, issues);
        } else {
            attach = json == null ? StoryAttach.NONE : attach(json, issues);
            if (attach.isEmpty()) issues.add("warning: attaches to no villager; add \"attach\" to story.json");
        }
        Map<String, Condition> conditions = new LinkedHashMap<>();
        Map<String, Action> actions = new LinkedHashMap<>();
        StoryDefinition.Bind bind = StoryDefinition.Bind.VILLAGER;
        int priority = 0;
        if (json != null) {
            if (json.has("conditions") && json.get("conditions").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("conditions").entrySet()) {
                    Condition condition = Conditions.parse(e.getValue());
                    if (condition == null) issues.add("error: story.json: condition '" + e.getKey() + "' is not a known Pheno condition");
                    else conditions.put(e.getKey(), condition);
                }
            }
            if (json.has("actions") && json.get("actions").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("actions").entrySet()) {
                    Action action = Actions.parse(e.getValue());
                    if (action == null) issues.add("error: story.json: action '" + e.getKey() + "' is not a known Pheno action");
                    else actions.put(e.getKey(), action);
                }
            }
            String bindName = json.has("bind") ? json.get("bind").getAsString() : "villager";
            try {
                bind = StoryDefinition.Bind.valueOf(bindName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                issues.add("error: story.json: bind must be \"villager\" or \"player\", not \"" + bindName + "\"");
            }
            if (json.has("priority")) priority = json.get("priority").getAsInt();
        }
        if (countErrors(issues) > errorsBefore) return null;
        if (persona != null) {
            if (personaDefinition == null) return null;
            personas.put(persona, personaDefinition);
        }
        String label = greet == null ? "" : first(tagMap(greet.tags()), "label", "");
        return new StoryDefinition(id, compiled.json(), sha1(compiled.json()), attach, bind, priority, label,
                Map.copyOf(quests), Map.copyOf(conditions), Map.copyOf(actions), Map.copyOf(goals));
    }

    private static StoryAttach attach(JsonObject json, List<String> issues) {
        if (!json.has("attach") || !json.get("attach").isJsonObject()) return StoryAttach.NONE;
        JsonObject attach = json.getAsJsonObject("attach");
        Set<String> professions = new HashSet<>();
        for (String raw : strings(attach.get("profession"))) {
            ResourceLocation key = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
            if (key == null) issues.add("error: story.json: '" + raw + "' is not a profession id");
            else professions.add(key.toString());
        }
        Condition when = null;
        if (attach.has("when")) {
            when = Conditions.parse(attach.get("when"));
            if (when == null) issues.add("error: story.json: attach.when is not a known Pheno condition");
        }
        return new StoryAttach(Set.copyOf(professions), Set.copyOf(strings(attach.get("root"))),
                Set.copyOf(strings(attach.get("culture"))), Set.copyOf(strings(attach.get("villager"))), when, null);
    }

    private static List<String> strings(@Nullable JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null) return out;
        if (element.isJsonArray()) {
            for (JsonElement e : (JsonArray) element) if (e.isJsonPrimitive()) out.add(e.getAsString());
        } else if (element.isJsonPrimitive()) {
            out.add(element.getAsString());
        }
        return out;
    }

    /** Ink tags like {@code goal: have 3 farmers}, grouped by the text before the first colon. */
    static Map<String, List<String>> tagMap(List<String> tags) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String tag : tags) {
            int colon = tag.indexOf(':');
            if (colon <= 0) continue;
            out.computeIfAbsent(tag.substring(0, colon).trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(tag.substring(colon + 1).trim());
        }
        return out;
    }

    private static String first(Map<String, List<String>> tags, String key, String fallback) {
        List<String> values = tags.get(key);
        return values == null || values.isEmpty() ? fallback : values.get(0);
    }

    private static int countErrors(List<String> issues) {
        return (int) issues.stream().filter(i -> i.startsWith("error:")).count();
    }

    private static String sha1(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
