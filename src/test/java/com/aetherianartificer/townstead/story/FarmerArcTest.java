package com.aetherianartificer.townstead.story;

import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays the Farmer from the first meeting to the year of plenty against a scripted world, for
 * each modpack shape the story branches on (rich soil, nutrient soil, big crops, paddy rice,
 * seasons) and the player's ways of putting them off. Quests start, skip, wait and hand back the
 * way StoryService does it. A run fails when no conversation can move the story on.
 */
class FarmerArcTest {
    private static final Path FARMER = Path.of("src/main/resources/data/townstead/persona/farmer");
    private static final Map<String, List<String>> QUESTS = new LinkedHashMap<>();
    private static final Map<String, String> SKIP_IF = Map.of("plan", "field_plan", "store", "granary");
    /** Days a quest's goals take to land once it opens. */
    private static final Map<String, Integer> TAKES = new HashMap<>();

    static {
        QUESTS.put("plan", List.of("field_plan"));
        QUESTS.put("water", List.of("watered"));
        QUESTS.put("harvest", List.of("first_harvest"));
        QUESTS.put("store", List.of("granary"));
        QUESTS.put("kinds", List.of("five_kinds"));
        QUESTS.put("ground_soil", List.of("fed_soil"));
        QUESTS.put("ground_big", List.of("giant_crop"));
        QUESTS.put("ground_rich", List.of("rich_harvest"));
        QUESTS.put("ground_giant", List.of("giant"));
        QUESTS.put("rice", List.of("rice"));
        QUESTS.put("home", List.of("fed", "harvest_32"));
        QUESTS.put("frost", List.of("harvest_48", "fed"));
        QUESTS.put("year", List.of("good_seasons", "farm", "granary"));
        TAKES.putAll(Map.of("plan", 1, "water", 1, "harvest", 3, "store", 2, "kinds", 4, "rice", 3, "home", 3, "frost", 3, "year", 12));
        for (String g : List.of("ground_soil", "ground_big", "ground_rich", "ground_giant")) TAKES.put(g, 4);
    }

    private static final List<String> STYLES = List.of("plain", "plan_first", "granary_first", "hungry", "trader", "seasons",
            "rice", "rich_soil", "nutrient_soil", "big_crops", "old_town", "put_off", "leave_early", "path", "stern", "guarded", "jovial");

    @Test
    void everyShapeReachesTheYearOfPlenty() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String style : STYLES) {
            World w = new World(compile(), style);
            try {
                w.play();
            } catch (Exception e) {
                problems.add(style + ": runtime error " + e + "\n" + w.log());
                continue;
            }
            String miss = w.missing();
            if (!miss.isEmpty()) problems.add(style + ": " + miss + "\n" + w.log());
        }
        assertTrue(problems.isEmpty(), () -> problems.size() + " runs failed\n\n" + String.join("\n\n", problems));
    }

    /** Beats that need the world to line up on the day (a trader, hunger, autumn), here while the year is open. */
    @Test
    void beatsThatComeWhileAQuestIsOpenStillPlay() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String style : List.of("trader_brief", "hungry_brief", "autumn_brief")) {
            World w = new World(compile(), style);
            w.play();
            String knot = switch (style) {
                case "trader_brief" -> "trader_scene";
                case "hungry_brief" -> "home_offer";
                default -> "frost_offer";
            };
            if (!w.visited(knot)) problems.add(style + ": " + knot + " never played\n" + w.log());
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n\n", problems));
    }

    private static final class World {
        final Story story;
        final String style;
        final Set<String> checks = new HashSet<>();
        final Map<String, String> quests = new LinkedHashMap<>();
        final Set<String> skipped = new HashSet<>();
        final Map<String, Integer> openedOn = new HashMap<>();
        final List<String> trail = new ArrayList<>();
        final Set<String> putOff = new HashSet<>();
        double trust;
        int day;
        boolean leftEarly;

        World(String json, String style) throws Exception {
            this.style = style;
            story = new Story(json);
            for (String name : StoryCompiler.EXTERNALS.keySet()) {
                story.bindExternalFunction(name, args -> external(name, args), false);
            }
            var vars = story.getVariablesState();
            vars.set("origin", style.equals("old_town") ? "old_town" : "road");
            vars.set("villager_name", "Wren");
            vars.set("player_name", "Sam");
            checks.add("has_farm");
            if (style.equals("plan_first")) checks.add("field_plan");
            if (style.equals("granary_first")) {
                checks.add("granary");
                checks.add("has_granary");
            }
            if (style.equals("hungry")) checks.add("hungry");
            if (style.equals("trader")) checks.add("trader_near");
            if (style.equals("rice")) checks.add("paddy_rice");
            if (style.equals("rich_soil") || style.equals("nutrient_soil") || style.equals("big_crops")) checks.add(style);
        }

        Object var(String name) {
            return story.getVariablesState().get(name);
        }

        boolean visited(String knot) {
            try {
                return story.getState().visitCountAtPathString(knot) > 0;
            } catch (Exception e) {
                return false;
            }
        }

        boolean flag(String name) {
            return Boolean.TRUE.equals(var(name));
        }

        private Object external(String name, Object[] args) {
            String a = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            return switch (name) {
                case "check" -> checks.contains(a);
                case "count" -> 0;
                case "rel" -> (int) Math.round(trust);
                case "trust" -> {
                    trust += ((Number) args[0]).doubleValue();
                    yield null;
                }
                case "act" -> true;
                case "act_on", "here" -> true;
                case "demeanor" -> switch (style) {
                    case "stern", "guarded", "jovial" -> style;
                    default -> trust >= 20 ? "warm" : "neutral";
                };
                case "career_path" -> style.equals("path") && day >= 3 ? "tiller" : "";
                case "persona_name" -> "Rowan";
                case "roll" -> "mill";
                case "most_harvested" -> "wheat";
                case "mod", "can_build", "contract_accept" -> false;
                case "contract_ready", "contract_active", "contract_turn_in" -> 0;
                default -> name.equals("building") || name.equals("who") ? "" : null;
            };
        }

        private boolean goalMet(String quest, String goal) {
            if (checks.contains(goal)) return true;
            if (goal.equals("farm")) return true;
            Integer opened = openedOn.get(quest);
            if (goal.equals("fed")) return !checks.contains("hungry") || (opened != null && day - opened >= TAKES.get(quest));
            return opened != null && day - opened >= TAKES.get(quest);
        }

        private void newDay() throws Exception {
            day++;
            story.getVariablesState().set("today", day);
            switch (style) {
                case "trader_brief" -> {
                    if (day == 20) checks.add("trader_near");
                    if (day == 21) checks.remove("trader_near");
                }
                case "hungry_brief" -> {
                    if (day == 20) checks.add("hungry");
                    if (day == 22) checks.remove("hungry");
                }
                case "autumn_brief", "seasons" -> {
                    // Autumn for a few days of every 24, as a seasons mod would have it.
                    if (day % 24 == 16) checks.add("autumn");
                    if (day % 24 == 22) checks.remove("autumn");
                }
                default -> { }
            }
            for (var q : quests.entrySet()) {
                if (!q.getValue().equals("ACTIVE")) continue;
                if (QUESTS.get(q.getKey()).stream().allMatch(g -> goalMet(q.getKey(), g))) {
                    q.setValue("READY");
                    if (q.getKey().equals("store")) checks.add("has_granary");
                    if (q.getKey().equals("home")) checks.remove("hungry");
                }
            }
        }

        private void startEntered() {
            for (String quest : QUESTS.keySet()) {
                if (quests.containsKey(quest) || !visited(quest)) continue;
                openedOn.put(quest, day);
                String skip = SKIP_IF.get(quest);
                if (skip != null && checks.contains(skip)) {
                    quests.put(quest, "READY");
                    skipped.add(quest);
                } else {
                    quests.put(quest, "ACTIVE");
                }
            }
        }

        private String entry() throws Exception {
            for (var q : quests.entrySet()) {
                if (!q.getValue().equals("READY")) continue;
                q.setValue("COMPLETE");
                return q.getKey() + (skipped.contains(q.getKey()) ? ".skipped" : ".done");
            }
            if (story.hasFunction("scene_ready") && Boolean.TRUE.equals(story.evaluateFunction("scene_ready"))) return "greet";
            for (var q : quests.entrySet()) {
                if (q.getValue().equals("ACTIVE")) return q.getKey() + ".waiting";
            }
            return "greet";
        }

        String missing() {
            List<String> miss = new ArrayList<>();
            if (!flag("done_year")) miss.add("year of plenty never closed");
            if (style.equals("plan_first") && !skipped.contains("plan")) miss.add("plan was not skipped");
            if (style.equals("granary_first") && !skipped.contains("store")) miss.add("granary was not skipped");
            if (style.equals("hungry") && !flag("done_home")) miss.add("Bring it home never closed");
            if (style.equals("trader") && !flag("done_trader")) miss.add("trader scene never played");
            if (style.equals("seasons") && !flag("done_frost")) miss.add("Before the frost never closed");
            if (style.equals("rice") && !flag("done_rice")) miss.add("rice never closed");
            if (style.equals("path") && !flag("done_path")) miss.add("career path scene never played");
            String ground = switch (style) {
                case "rich_soil" -> "ground_rich";
                case "nutrient_soil" -> "ground_soil";
                case "big_crops" -> "ground_big";
                default -> "ground_giant";
            };
            if (!visited(ground)) miss.add(ground + " was not the experiment");
            return String.join("; ", miss);
        }

        void play() throws Exception {
            for (int talk = 0; talk < 400; talk++) {
                if (talk % 2 == 0) newDay();
                story.getVariablesState().set("quest_ready", quests.containsValue("READY"));
                story.getVariablesState().set("quest_open", quests.containsValue("ACTIVE"));
                Object menu = story.evaluateFunction("menu");
                if (menu == null || String.valueOf(menu).isBlank()) throw new IllegalStateException("menu() gave nothing");
                converse(entry());
                startEntered();
                if (flag("done_year")) {
                    for (int i = 0; i < 4; i++) {
                        newDay();
                        converse(entry());
                    }
                    return;
                }
            }
        }

        private void converse(String start) throws Exception {
            story.choosePathString(start);
            StringBuilder said = new StringBuilder();
            List<String> picks = new ArrayList<>();
            Set<String> chosen = new HashSet<>();
            for (int step = 0; step < 40; step++) {
                while (story.canContinue()) said.append(story.Continue());
                List<Choice> choices = new ArrayList<>(story.getCurrentChoices());
                if (choices.isEmpty()) break;
                int pick = pick(choices, chosen, start);
                chosen.add(choices.get(pick).getText());
                picks.add(choices.get(pick).getText());
                story.chooseChoiceIndex(pick);
            }
            trail.add("day " + day + " " + start + ": " + said.toString().split("\n", 2)[0] + "  " + picks);
        }

        private int pick(List<Choice> all, Set<String> chosen, String start) {
            List<String> delay = List.of("Not now", "Not yet", "I'm a bit busy", "See you later", "Bye", "Keep it for now",
                    "I should get back", "We'll see");
            for (int i = 0; i < all.size(); i++) {
                String t = all.get(i).getText();
                if (style.equals("leave_early") && !leftEarly && t.startsWith("I'm a bit busy")) {
                    leftEarly = true;
                    return i;
                }
                if (style.equals("put_off") && t.equals("Not now.") && putOff.add(start + "|" + day / 3)) return i;
            }
            for (int i = 0; i < all.size(); i++) {
                String t = all.get(i).getText();
                if (!chosen.contains(t) && delay.stream().noneMatch(t::startsWith)) return i;
            }
            for (int i = 0; i < all.size(); i++) if (all.get(i).getText().startsWith("I should get back")) return i;
            return all.size() - 1;
        }

        String log() {
            List<String> flags = new ArrayList<>();
            for (String name : story.getVariablesState()) {
                if (name.startsWith("done_") || name.equals("met") || name.equals("quest_day")) flags.add(name + "=" + var(name));
            }
            int from = Math.max(0, trail.size() - 10);
            return "day=" + day + " trust=" + trust + " quests=" + quests + " checks=" + checks + "\nflags: " + String.join(", ", flags)
                    + "\nlast:\n" + String.join("\n", trail.subList(from, trail.size()));
        }
    }

    private static String compile() throws IOException {
        Path dir = FARMER;
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(FARMER))) {
                dir = root.resolve(FARMER);
                break;
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put("persona.ink", Files.readString(dir.resolve("persona.ink"), StandardCharsets.UTF_8));
        Path base = dir;
        try (var scenes = Files.walk(dir)) {
            for (Path scene : scenes.filter(f -> f.toString().endsWith(".ink")).sorted().toList()) {
                files.putIfAbsent(base.relativize(scene).toString().replace(java.io.File.separatorChar, '/'),
                        Files.readString(scene, StandardCharsets.UTF_8));
            }
        }
        StoryCompiler.Result result = StoryCompiler.compile(files);
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        return result.json();
    }
}
