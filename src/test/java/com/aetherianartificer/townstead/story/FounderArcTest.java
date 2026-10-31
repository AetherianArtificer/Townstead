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
 * Plays the Dhampir founder from the first meeting to the epilogue against a scripted world: each
 * act() gets the reaction the game would give, quests are handed back the way the engine does it,
 * and the time of day turns with every conversation. The run fails at the first point where no
 * conversation can move the story on, and says what was last ready.
 */
class FounderArcTest {
    private static final Path FOUNDER = Path.of("src/main/resources/data/townstead/persona/dhampir_founder");
    private static final List<String> QUESTS = List.of("altar", "recruit_job", "player_oath", "another_hunter", "scraps_job",
            "garlic", "collar_ask", "bone_ask", "werewolf_signs");

    @Test
    void theArcPlaysThroughToEndingA() throws Exception {
        World world = new World(compile(), false, true);
        world.play();
        assertTrue("A".equals(world.var("ending")), () -> "ended as " + world.var("ending") + "\n" + world.log());
        assertTrue(world.visited("epilogue"), () -> "no epilogue\n" + world.log());
        assertTrue(world.visited("collar_ask.done") && world.visited("bone_ask.done"), () -> "the wolf's quests never closed\n" + world.log());
        assertTrue(world.visited("contracts"), () -> "the lodge never offered work\n" + world.log());
    }

    /** Each side branch, played on the way to Ending A: it has to happen, and the arc has to finish anyway. */
    @Test
    void everySideBranchPlaysAndTheArcStillEnds() throws Exception {
        Map<String, java.util.function.Predicate<World>> branches = new LinkedHashMap<>();
        branches.put("garlic", w -> w.visited("garlic.done"));
        branches.put("volunteers", w -> w.visited("volunteers"));
        branches.put("resident", w -> w.visited("neighbor_back") && "Vera".equals(String.valueOf(w.var("stake_target"))));
        branches.put("romance", w -> Boolean.TRUE.equals(w.var("romanced")));
        branches.put("wolfdown", w -> Boolean.TRUE.equals(w.var("done_wounded")));
        branches.put("cure", w -> Boolean.TRUE.equals(w.var("done_cure")));
        branches.put("forgive", w -> Boolean.TRUE.equals(w.var("done_confession_after")));
        branches.put("castout", w -> Boolean.TRUE.equals(w.var("done_confession_after")));
        branches.put("turnback", w -> w.visited("grave_road") && Boolean.TRUE.equals(w.var("done_grave")));
        branches.put("werewolf", w -> w.visited("werewolf_signs.done") && w.visited("werewolf_wolf") && w.visited("werewolf_kill"));
        List<String> problems = new ArrayList<>();
        World plain = new World(compile(), false, true);
        plain.play();
        for (var branch : branches.entrySet()) {
            if (branch.getValue().test(plain)) problems.add(branch.getKey() + ": also happens without the branch, so this proves nothing");
        }
        for (var branch : branches.entrySet()) {
            World world = new World(compile(), false, true, branch.getKey());
            world.play();
            if (!branch.getValue().test(world)) problems.add(branch.getKey() + ": the branch never played\n" + world.log());
            else if (!"A".equals(world.var("ending"))) problems.add(branch.getKey() + ": ended as " + world.var("ending") + "\n" + world.log());
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n\n", problems));
    }

    @Test
    void theArcPlaysThroughToEndingB() throws Exception {
        World world = new World(compile(), true, true);
        world.play();
        assertTrue("B".equals(world.var("ending")), () -> "ended as " + world.var("ending") + "\n" + world.log());
        assertTrue(world.visited("epilogue"), () -> "no epilogue\n" + world.log());
    }

    @Test
    void aTownWhereNobodyCanTurnStillReachesTheName() throws Exception {
        World world = new World(compile(), false, false);
        world.play();
        assertTrue(Boolean.TRUE.equals(world.var("no_one_turns")), () -> "someone was bitten\n" + world.log());
        assertTrue("A".equals(world.var("ending")), () -> "ended as " + world.var("ending") + "\n" + world.log());
    }

    /** The game around the founder, reduced to the answers the story asks for. */
    private static final class World {
        final Story story;
        final boolean fall;
        /** Whether anyone in town could be bitten and turn. */
        final boolean turnable;
        final Set<String> checks = new HashSet<>();
        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, String> who = new HashMap<>();
        final Set<String> is = new HashSet<>();
        final Set<String> questsDone = new HashSet<>();
        /** Reactions that land on a later day: day -> runnable. */
        final Map<Integer, List<Runnable>> later = new HashMap<>();
        final List<String> trail = new ArrayList<>();
        double trust;
        int day;
        int phase;

        /** Side branches this run takes. */
        final Set<String> opts;
        boolean turnedBack;

        World(String json, boolean fall, boolean turnable, String... opts) throws Exception {
            this.fall = fall;
            this.turnable = turnable;
            this.opts = Set.of(opts);
            story = new Story(json);
            for (String name : StoryCompiler.EXTERNALS.keySet()) {
                story.bindExternalFunction(name, args -> external(name, args), false);
            }
            if (fall) checks.add("pull_predator");
            if (this.opts.contains("garlic")) checks.add("second_kill");
            if (this.opts.contains("volunteers")) who.put("parent_volunteer", "Pip");
            if (this.opts.contains("resident")) {
                who.put("resident_vampire", "Vera");
                is.addAll(Set.of("Vera|", "Vera|neighbor_clean", "Vera|never_bit"));
            }
            if (this.opts.contains("romance")) checks.addAll(Set.of("hearts_close", "hearts_dear", "hearts_love"));
            if (this.opts.contains("werewolf")) checks.add("werewolf_near");
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

        String log() {
            List<String> flags = new ArrayList<>();
            for (String name : story.getVariablesState()) {
                Object value = story.getVariablesState().get(name);
                if (name.startsWith("done_") || name.endsWith("_called") || name.endsWith("_offered") || name.equals("named")
                        || name.equals("founded") || name.equals("met") || name.equals("ending") || name.endsWith("_seen")
                        || name.endsWith("_job") || name.endsWith("walking") || name.equals("bread_known") || name.equals("staking")) {
                    flags.add(name + "=" + value);
                }
            }
            List<String> firsts = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String line : trail) {
                if (!line.startsWith("day ")) continue;
                String path = line.split(" ")[2].replace(":", "");
                if (seen.add(path)) firsts.add(line.split(" ")[1] + " " + path);
            }
            int from = Math.max(0, trail.size() - 12);
            return "trust=" + trust + " counts=" + counts + " checks=" + checks + "\nflags: " + String.join(", ", flags)
                    + "\nfirst visits: " + String.join(" | ", firsts) + "\nlast:\n" + String.join("\n", trail.subList(from, trail.size()));
        }

        private void at(int inDays, Runnable reaction) {
            later.computeIfAbsent(day + inDays, k -> new ArrayList<>()).add(reaction);
        }

        private Object external(String name, Object[] args) {
            String a = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            return switch (name) {
                case "check" -> checks.contains(a);
                case "count" -> counts.getOrDefault(a, 0);
                case "who" -> who.getOrDefault(a, "");
                case "here" -> true;
                case "is" -> {
                    String what = args.length > 1 && args[1] != null ? String.valueOf(args[1]) : "";
                    yield what.isEmpty() ? (who.containsValue(a) || is.contains(a + "|")) : is.contains(a + "|" + what);
                }
                case "rel" -> (int) Math.round(trust);
                case "trust" -> {
                    trust += ((Number) args[0]).doubleValue();
                    yield null;
                }
                case "act" -> act(a);
                case "act_on" -> true;
                case "mod" -> opts.contains("werewolf") && a.equals("werewolves");
                case "can_build", "contract_accept" -> false;
                case "contract_ready", "contract_active", "contract_turn_in" -> 0;
                case "contribute", "memory", "contract_skip" -> null;
                default -> "";
            };
        }

        private boolean act(String id) {
            trail.add("    act " + id);
            switch (id) {
                case "fetch_wolf", "adopt_wolf" -> {
                    checks.add("has_wolf");
                    checks.add("has_wolf_anywhere");
                    checks.add("wolf_close");
                }
                case "enlist_recruit" -> at(3, () -> {
                    counts.merge("founder_oaths", 1, Integer::sum);
                    checks.add("first_oath");
                    who.put("sworn_hunter", "Hale");
                });
                case "swear_player" -> checks.add("player_sworn");
                case "walk_with" -> at(0, () -> counts.merge("founder_kills", 1, Integer::sum));
                case "first_watch" -> {
                    checks.add("raid_near");
                    at(1, () -> checks.remove("raid_near"));
                }
                case "bite_one" -> {
                    if (!turnable) return false;
                    who.put("turning_villager", "Ana");
                    is.add("Ana|turning_villager");
                    is.add("Ana|");
                    at(2, () -> {
                        who.remove("turning_villager");
                        is.remove("Ana|turning_villager");
                        is.add("Ana|cured_villager");
                    });
                }
                case "thrall_arrives" -> {
                    checks.add("thrall_near");
                    who.put("visitor:grieving_thrall", "Tam");
                    at(1, () -> {
                        checks.remove("thrall_near");
                        who.remove("visitor:grieving_thrall");
                        checks.add("thrall_sent_away");
                    });
                }
                case "mark_slipped" -> at(1, () -> checks.add("midwife_bread"));
                case "bread_delivered" -> checks.remove("midwife_bread");
                // Turning back needs a talk on the road before the grave, so the first walk takes a day.
                case "grave_travel" -> at(opts.contains("turnback") && !turnedBack ? 1 : 0, () -> {
                    if (Boolean.TRUE.equals(var("grave_walking"))) checks.add("at_grave");
                });
                case "grave_home" -> checks.remove("at_grave");
                case "court_arrives" -> {
                    checks.add("raid_near");
                    at(1, () -> checks.remove("raid_near"));
                    if (opts.contains("wolfdown")) {
                        counts.merge("wolf_downs", 1, Integer::sum);
                        checks.add("wolf_down");
                        at(1, () -> checks.remove("wolf_down"));
                    }
                }
                case "carry_question" -> at(0, () -> checks.add("heard_neighbor"));
                case "asked_cure" -> {
                    if (opts.contains("cure")) at(2, () -> is.add("Fen|cured_villager"));
                }
                case "mark_midpoint" -> {
                    if (opts.contains("forgive")) at(1, () -> checks.add("midwife_forgiven"));
                    if (opts.contains("castout")) at(1, () -> checks.add("midwife_cast_out"));
                }
                case "mark_wounded_seen" -> at(1, () -> checks.add("carries_book"));
                case "take_book" -> checks.remove("carries_book");
                case "restore_oath" -> checks.add("oath_restored");
                case "shepherd_travel" -> at(0, () -> checks.add("at_shepherd"));
                case "shepherd_home" -> checks.remove("at_shepherd");
                case "court_travel" -> at(0, () -> checks.add("at_court"));
                case "court_home" -> checks.remove("at_court");
                case "settler_arrives" -> who.put("visitor:settler", "Sel");
                case "fledgling_arrives" -> who.put("visitor:fledgling", "Fen");
                case "fledgling_leave" -> who.remove("visitor:fledgling");
                case "drop_signet" -> checks.add("carries_signet");
                case "take_signet" -> checks.remove("carries_signet");
                default -> { }
            }
            return true;
        }

        /** Morning, evening, night, deep night; a new day after the last. */
        private void turn() throws Exception {
            phase = (phase + 1) % 4;
            if (phase == 0) {
                day++;
                for (Runnable r : later.getOrDefault(day, List.of())) r.run();
                later.remove(day);
            } else if (later.containsKey(day)) {
                for (Runnable r : later.remove(day)) r.run();
            }
            checks.removeAll(Set.of("evening", "night", "late_night"));
            switch (phase) {
                case 1 -> checks.add("evening");
                case 2 -> checks.addAll(Set.of("evening", "night", "late_night"));
                case 3 -> checks.addAll(Set.of("night", "late_night"));
                default -> { }
            }
            story.getVariablesState().set("today", day);
        }

        void play() throws Exception {
            for (int talk = 0; talk < 1500; talk++) {
                turn();
                if (Boolean.TRUE.equals(story.getState().visitCountAtPathString("epilogue") > 0)) return;
                if (checks.contains("midwife_bread")) converse("gift_bread");
                // A werewolf puts a vampire down near the player some time after W2.
                if (opts.contains("werewolf") && Boolean.TRUE.equals(var("done_w2"))) counts.putIfAbsent("werewolf_kills_seen", 1);
                converse(entry());
            }
        }

        /** Where the engine would start this conversation. */
        private String entry() throws Exception {
            for (String quest : QUESTS) {
                if (!visited(quest) || questsDone.contains(quest)) continue;
                if (goalMet(quest)) {
                    questsDone.add(quest);
                    if (quest.equals("altar") && Boolean.TRUE.equals(var("done_lodge"))) continue;
                    return quest + ".done";
                }
            }
            for (String quest : QUESTS) {
                if (visited(quest) && !questsDone.contains(quest) && !Boolean.TRUE.equals(story.evaluateFunction("scene_ready"))) {
                    return quest + ".waiting";
                }
            }
            return "greet";
        }

        private boolean goalMet(String quest) throws Exception {
            switch (quest) {
                case "altar" -> {
                    checks.add("altar_placed");
                    checks.add("lodge_built");
                    return true;
                }
                case "recruit_job" -> {
                    return counts.getOrDefault("founder_oaths", 0) >= 1;
                }
                case "player_oath" -> {
                    return checks.contains("player_sworn");
                }
                case "another_hunter" -> {
                    return visited("slip_after");
                }
                // The player has the things on them as soon as they are asked.
                case "garlic", "collar_ask", "bone_ask", "werewolf_signs" -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        private void converse(String start) throws Exception {
            String path = start.equals("greet") ? "greet>" + story.evaluateFunction("next_scene") : start;
            story.choosePathString(start);
            StringBuilder said = new StringBuilder();
            Set<String> chosen = new HashSet<>();
            List<String> picks = new ArrayList<>();
            for (int step = 0; step < 40; step++) {
                while (story.canContinue()) said.append(story.Continue());
                List<Choice> choices = new ArrayList<>(story.getCurrentChoices());
                if (choices.isEmpty()) break;
                int pick = pick(choices, chosen);
                chosen.add(choices.get(pick).getText());
                picks.add(choices.get(pick).getText());
                story.chooseChoiceIndex(pick);
            }
            String first = said.toString().split("\n", 2)[0];
            trail.add("day " + day + "/" + phase + " " + path + ": " + first + "  " + picks);
        }

        /** Never the same choice twice in one conversation; out once there is nothing new. */
        private int pick(List<Choice> all, Set<String> chosen) {
            List<Choice> fresh = all.stream().filter(c -> !chosen.contains(c.getText())).toList();
            if (fresh.isEmpty()) {
                for (int i = 0; i < all.size(); i++) if (all.get(i).getText().startsWith("I'll let you")) return i;
                return all.size() - 1;
            }
            return all.indexOf(fresh.get(pickFrom(fresh)));
        }

        /** The way forward: the story's marked choice, then the arc's own answers, never a delay. */
        private int pickFrom(List<Choice> choices) {
            List<String> wanted = fall
                    ? List.of("She killed your mother.", "Do it.", "I'll join you.", "She was wrong.")
                    : List.of("She couldn't help it. The bond did this to her.", "Come home. It's waiting for you.",
                            "Spare him.", "She wasn't.", "Welcome home.");
            List<String> forward = List.of("What were you reciting, that night?", "I have something of your mother's.",
                    "I've brought someone.", "I'll take the oath.", "I'm ready.", "Tell me about yourself.",
                    "I'll come.", "I'll ask.", "All right. A few nights.", "Stay.", "Stay. Build your lodge.",
                    "Where do you want it?", "Does garlic work?", "There's more, at the back.", "Let's go home.",
                    "Stay here. You'd be safe here.", "You could.", "There might be a way back.",
                    "Someone who's sat with a lot of sick people might know.", "It's their choice. Leave it.",
                    "Say it. They're not dangerous.", "Let's call it.", "Keep going.", "What will you do?");
            List<String> branch = new ArrayList<>();
            if (opts.contains("romance")) branch.addAll(List.of("I like your company.", "What would you say tonight?", "I'll stay."));
            if (opts.contains("turnback") && !turnedBack && Boolean.TRUE.equals(var("grave_walking"))) {
                for (int i = 0; i < choices.size(); i++) {
                    if (choices.get(i).getText().equals("Let's turn back.")) {
                        turnedBack = true;
                        return i;
                    }
                }
            }
            for (String want : branch) for (int i = 0; i < choices.size(); i++) if (choices.get(i).getText().equals(want)) return i;
            for (String want : wanted) for (int i = 0; i < choices.size(); i++) if (choices.get(i).getText().equals(want)) return i;
            for (int i = 0; i < choices.size(); i++) {
                Choice c = choices.get(i);
                if (c.getTags() != null && c.getTags().stream().anyMatch(t -> t.trim().equals(StorySession.ADVANCE_TAG))) return i;
            }
            boolean recruiting = counts.getOrDefault("founder_oaths", 0) < 2 && Boolean.TRUE.equals(var("done_count_talk"));
            for (String want : forward) {
                if (want.equals("I've brought someone.") && !recruiting && Boolean.TRUE.equals(var("done_first_oath"))) continue;
                for (int i = 0; i < choices.size(); i++) if (choices.get(i).getText().equals(want)) return i;
            }
            List<String> delay = List.of("Not now", "Not yet", "Another", "I'll let you", "Let's turn back", "I'll think",
                    "I should go", "Nothing for now", "Never mind", "Let me think");
            for (int i = 0; i < choices.size(); i++) {
                String t = choices.get(i).getText();
                if (delay.stream().noneMatch(t::startsWith)) return i;
            }
            return choices.size() - 1;
        }
    }

    private static String compile() throws IOException {
        Path dir = FOUNDER;
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(FOUNDER))) {
                dir = root.resolve(FOUNDER);
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
