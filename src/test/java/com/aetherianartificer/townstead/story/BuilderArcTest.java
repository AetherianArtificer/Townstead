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
 * Plays the Village Builder against a scripted world, for every hometown, a new and a discovered
 * village, and the player's ways of putting them off. Quests start, skip, wait and hand back the
 * way StoryService does it, and goals land a few days after a quest opens. A run fails when no
 * conversation can move Act 1 on, and says what was last open.
 */
class BuilderArcTest {
    private static final Path BUILDER = Path.of("src/main/resources/data/townstead/persona/village_builder");
    private static final List<String> HOMETOWNS = List.of("mine", "mill", "harbor", "roads", "forest", "well");
    /** Quest knot -> goal names (persona.json), and the "skip if" goal when it has one. */
    private static final Map<String, List<String>> QUESTS = new LinkedHashMap<>();
    private static final Map<String, String> SKIP_IF = Map.of("rest", "rest_days", "pot", "fed", "work", "three_working", "water", "watered");

    static {
        QUESTS.put("push", List.of("upgrade_one"));
        QUESTS.put("rest", List.of("rest_days"));
        QUESTS.put("beds", List.of("newcomer", "spare_bed"));
        QUESTS.put("pot", List.of("fed"));
        QUESTS.put("work", List.of("three_working"));
        QUESTS.put("water", List.of("watered"));
        QUESTS.put("carry", List.of("own_rest_day", "shorter_days"));
    }

    @Test
    void everyHometownFinishesActOne() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String home : HOMETOWNS) {
            for (boolean discovered : List.of(false, true)) {
                for (boolean thirst : home.equals("well") ? List.of(true) : List.of(false, true)) {
                    for (String style : List.of("plain", "leave_early", "put_off", "met_already", "collapse", "farm", "stern", "guarded", "jovial")) {
                        World w = new World(compile(), home, discovered, thirst, style);
                        String label = home + (discovered ? " discovered" : " new") + (thirst ? " +thirst" : "") + " " + style;
                        try {
                            w.play();
                        } catch (Exception e) {
                            problems.add(label + ": runtime error " + e + "\n" + w.log());
                            continue;
                        }
                        String miss = w.missing();
                        if (!miss.isEmpty()) problems.add(label + ": " + miss + "\n" + w.log());
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> problems.size() + " runs stalled\n\n" + String.join("\n\n", problems));
    }

    /** A village nobody moves into: the beds quest can never close, and it holds the rest back. */
    @Test
    void noNewcomerHoldsOnlyTheBedsQuest() throws Exception {
        World w = new World(compile(), "harbor", false, false, "no_newcomer");
        w.play();
        boolean others = w.flag("done_pot") && w.flag("done_work") && w.flag("wrote_farmer");
        assertTrue(others && "ACTIVE".equals(w.quests.get("beds")), () -> "harbor with no newcomer\n" + w.log());
    }

    /** The well rolls only with thirst on; turning thirst off later must not strand its first worry. */
    @Test
    void wellWithThirstTurnedOffLater() throws Exception {
        World w = new World(compile(), "well", false, true, "thirst_off_later");
        w.play();
        String miss = w.missing();
        assertTrue(miss.isEmpty(), () -> "well, thirst off later: " + miss + "\n" + w.log());
    }

    private static final class World {
        final Story story;
        final String home;
        final boolean discovered;
        boolean thirst;
        final String style;
        final Set<String> checks = new HashSet<>();
        final Map<String, Integer> counts = new HashMap<>();
        /** knot -> ACTIVE / READY / COMPLETE, and whether it was skipped. */
        final Map<String, String> quests = new LinkedHashMap<>();
        final Set<String> skipped = new HashSet<>();
        final Map<String, Integer> openedOn = new HashMap<>();
        final List<String> trail = new ArrayList<>();
        final Set<String> putOff = new HashSet<>();
        double trust;
        int day;
        boolean leftEarly;

        World(String json, String home, boolean discovered, boolean thirst, String style) throws Exception {
            this.home = home;
            this.discovered = discovered;
            this.thirst = thirst;
            this.style = style;
            story = new Story(json);
            for (String name : StoryCompiler.EXTERNALS.keySet()) {
                story.bindExternalFunction(name, args -> external(name, args), false);
            }
            var vars = story.getVariablesState();
            vars.set("hometown", home);
            vars.set("villager_name", "Rowan");
            vars.set("player_name", "Sam");
            vars.set("village_name", discovered ? "Elmford" : "");
            if (discovered) checks.add("discovered");
            if (thirst) checks.add("thirst_on");
            checks.add("hungry");
            checks.add("no_spare_beds");
            counts.put("idle_adults", 2);
            if (style.equals("farm")) checks.add("has_farm");
            if (style.equals("met_already")) {
                // Everything the village could already have before they arrive.
                checks.addAll(Set.of("rest_days", "fed", "three_working", "watered"));
                checks.remove("hungry");
                counts.put("idle_adults", 0);
            }
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

        String demeanor() {
            return switch (style) {
                case "stern", "guarded", "jovial" -> style;
                default -> trust >= 20 ? "warm" : "neutral";
            };
        }

        private Object external(String name, Object[] args) {
            String a = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            return switch (name) {
                case "check" -> checks.contains(a);
                case "count" -> counts.getOrDefault(a, 0);
                case "rel" -> (int) Math.round(trust);
                case "trust" -> {
                    trust += ((Number) args[0]).doubleValue();
                    yield null;
                }
                case "act" -> {
                    trail.add("    act " + a);
                    if (a.equals("introduce_farmer")) checks.add("farmer_written");
                    yield true;
                }
                case "act_on", "here" -> true;
                case "demeanor" -> demeanor();
                case "building" -> a.equals("raised") ? "house" : "";
                case "roll" -> "road";
                case "mod", "can_build" -> false;
                case "contract_accept" -> false;
                case "contract_ready", "contract_active", "contract_turn_in" -> 0;
                case "contribute", "memory", "contract_skip" -> null;
                default -> "";
            };
        }

        /** A goal's world state: met once the quest has been open long enough. */
        private boolean goalMet(String quest, String goal) {
            if (checks.contains(goal)) return true;
            Integer opened = openedOn.get(quest);
            if (opened == null) return false;
            int age = day - opened;
            return switch (goal) {
                case "upgrade_one" -> age >= 2;
                case "rest_days" -> age >= 1;
                case "newcomer" -> !style.equals("no_newcomer") && age >= 3;
                case "spare_bed" -> age >= 2;
                case "fed" -> age >= 2;
                case "three_working" -> age >= 2;
                case "watered" -> !thirst || age >= 2;
                case "own_rest_day", "shorter_days" -> age >= 1;
                default -> false;
            };
        }

        /** What a day does: goals land, the world follows, quests settle. */
        private void newDay() throws Exception {
            day++;
            story.getVariablesState().set("today", day);
            if (style.equals("collapse") && day == 4) counts.put("collapsed", 1);
            if (style.equals("thirst_off_later") && day == 2) {
                thirst = false;
                checks.remove("thirst_on");
            }
            if (style.equals("farm") || day >= 6) checks.add("has_farm");
            for (var q : quests.entrySet()) {
                if (!q.getValue().equals("ACTIVE")) continue;
                if (QUESTS.get(q.getKey()).stream().allMatch(g -> goalMet(q.getKey(), g))) {
                    q.setValue("READY");
                    reflect(q.getKey());
                }
            }
        }

        /** The checks that follow a goal being met. */
        private void reflect(String quest) {
            switch (quest) {
                case "pot" -> checks.remove("hungry");
                case "work" -> counts.put("idle_adults", 0);
                case "beds" -> {
                    counts.merge("newcomer", 1, Integer::sum);
                    checks.remove("no_spare_beds");
                    checks.add("spare_bed");
                }
                default -> { }
            }
        }

        /** StoryService.startEnteredQuests: knots entered for the first time become quests. */
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

        /** StoryService.entryPath. */
        private String entry() throws Exception {
            for (var q : quests.entrySet()) {
                if (!q.getValue().equals("READY")) continue;
                q.setValue("COMPLETE");
                return q.getKey() + (skipped.contains(q.getKey()) ? ".skipped" : ".done");
            }
            if (Boolean.TRUE.equals(story.evaluateFunction("scene_ready"))) return "greet";
            for (var q : quests.entrySet()) {
                if (q.getValue().equals("ACTIVE")) return q.getKey() + ".waiting";
            }
            return "greet";
        }

        boolean actOneDone() throws Exception {
            boolean all = Boolean.TRUE.equals(story.evaluateFunction("done_all"));
            boolean farmer = flag("wrote_farmer");
            boolean carry = !style.equals("collapse") || flag("done_carry");
            return all && farmer && carry;
        }

        String missing() throws Exception {
            List<String> miss = new ArrayList<>();
            if (!Boolean.TRUE.equals(story.evaluateFunction("done_all"))) miss.add("Act 1 lessons not all done");
            if (!flag("wrote_farmer")) miss.add("farmer never written to");
            if (style.equals("collapse") && !flag("done_carry")) miss.add("carry never closed");
            if (discovered && !flag("done_push")) miss.add("push never closed");
            if (style.equals("met_already") && skipped.isEmpty() && !home.equals("harbor") && !home.equals("forest")) {
                miss.add("nothing was skipped though every goal was met");
            }
            return String.join("; ", miss);
        }

        void play() throws Exception {
            for (int talk = 0; talk < 300; talk++) {
                if (talk % 2 == 0) newDay();
                // The host functions the menu and nameplate ask for every time.
                story.getVariablesState().set("quest_ready", quests.containsValue("READY"));
                story.getVariablesState().set("quest_open", quests.containsValue("ACTIVE"));
                story.getVariablesState().set("chance", (talk * 37) % 100 + 1);
                Object menu = story.evaluateFunction("menu");
                story.evaluateFunction("greeting");
                story.evaluateFunction("calling");
                if (menu == null || String.valueOf(menu).isBlank()) throw new IllegalStateException("menu() gave nothing");
                converse(entry());
                startEntered();
                if (actOneDone()) {
                    // A few more talks after Act 1, to show the everyday talk holds up.
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
            String first = said.toString().split("\n", 2)[0];
            trail.add("day " + day + " " + start + ": " + first + "  " + picks);
        }

        private int pick(List<Choice> all, Set<String> chosen, String start) {
            List<String> delay = List.of("Not now", "Not yet", "I can't stop right now", "Let me think", "See you later", "Bye");
            // leave_early: walk away from the first meeting once. put_off: say "Not now" to each offer once.
            for (int i = 0; i < all.size(); i++) {
                String t = all.get(i).getText();
                if (style.equals("leave_early") && !leftEarly && t.startsWith("I can't stop right now")) {
                    leftEarly = true;
                    return i;
                }
                if (style.equals("put_off") && t.equals("Not now.") && putOff.add(start + "|" + day / 3)) return i;
            }
            for (int i = 0; i < all.size(); i++) {
                String t = all.get(i).getText();
                if (!chosen.contains(t) && delay.stream().noneMatch(t::startsWith)) return i;
            }
            return all.size() - 1;
        }

        String log() {
            List<String> flags = new ArrayList<>();
            for (String name : story.getVariablesState()) {
                if (name.startsWith("done_") || name.equals("met") || name.equals("wrote_farmer") || name.equals("lesson_day")) {
                    flags.add(name + "=" + var(name));
                }
            }
            int from = Math.max(0, trail.size() - 10);
            return "day=" + day + " trust=" + trust + " quests=" + quests + " checks=" + checks + "\nflags: " + String.join(", ", flags)
                    + "\nlast:\n" + String.join("\n", trail.subList(from, trail.size()));
        }
    }

    private static String compile() throws IOException {
        Path dir = BUILDER;
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(BUILDER))) {
                dir = root.resolve(BUILDER);
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
