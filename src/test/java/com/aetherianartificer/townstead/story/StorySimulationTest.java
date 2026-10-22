package com.aetherianartificer.townstead.story;

import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays a Persona's scenes the way a player would, with the world stubbed out: every check is
 * false unless listed, every count is zero, nobody is around. It walks the choices and makes sure
 * the way forward is always in reach and always marked ({@code # advance}), so a player never has
 * to exhaust every topic to find it.
 */
class StorySimulationTest {
    private static final Path FOUNDER = Path.of("src/main/resources/data/townstead/persona/dhampir_founder");

    @Test
    void firstMeetingAfterAKillReachesTheLodge() throws Exception {
        assertSceneLeadsTo(FOUNDER, "greet", Set.of("made_kill"), "found");
    }

    @Test
    void firstMeetingOnAColdTrailReachesTheLodge() throws Exception {
        assertSceneLeadsTo(FOUNDER, "greet", Set.of(), "found");
    }

    @Test
    void anOpenJobAloneDoesNotCallThePlayerOver() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.getVariablesState().set("met", 2);
        sim.story.getVariablesState().set("offered", true);
        sim.story.getVariablesState().set("founded", true);
        sim.story.getVariablesState().set("altar_started", true);
        sim.story.getVariablesState().set("quest_open", true);
        Object line = sim.story.evaluateFunction("calling");
        assertTrue(line == null || line.toString().isEmpty(), () -> "called out \"" + line + "\" for an open job");
    }

    @Test
    void aReadySceneGoesAheadOfAnOpenJob() throws Exception {
        // The lodge is open and the wolf is back: the wolf scene must play, not the lodge's waiting lines.
        Sim sim = new Sim(compile(FOUNDER), Set.of("has_wolf"));
        sim.story.getVariablesState().set("met", 2);
        sim.story.getVariablesState().set("offered", true);
        sim.story.getVariablesState().set("founded", true);
        sim.story.getVariablesState().set("altar_started", true);
        sim.story.getVariablesState().set("quest_open", true);
        assertTrue(Boolean.TRUE.equals(sim.story.evaluateFunction("scene_ready")), "the wolf scene was not ready");
    }

    @Test
    void anOpenJobOrEverydayTalkIsNotAScene() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.getVariablesState().set("met", 2);
        sim.story.getVariablesState().set("offered", true);
        sim.story.getVariablesState().set("founded", true);
        sim.story.getVariablesState().set("altar_started", true);
        sim.story.getVariablesState().set("wolf_seen", true);
        assertTrue(!Boolean.TRUE.equals(sim.story.evaluateFunction("scene_ready")), "everyday talk counted as a scene");
        sim.story.getVariablesState().set("quest_open", true);
        assertTrue(!Boolean.TRUE.equals(sim.story.evaluateFunction("scene_ready")), "the open job counted as a scene");
    }

    @Test
    void everyGoalAndActionTheScenesNameIsInPersonaJson() throws Exception {
        Path dir = locate(FOUNDER);
        com.google.gson.JsonObject persona = com.google.gson.JsonParser.parseString(
                Files.readString(dir.resolve("persona.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var goals = persona.getAsJsonObject("goals").keySet();
        var actions = persona.getAsJsonObject("actions").keySet();
        var goalTag = java.util.regex.Pattern.compile("(?m)^\\s*#\\s*(?:goal|skip if):\\s*(\\w+)");
        var act = java.util.regex.Pattern.compile("act\\(\"(\\w+)\"\\)");
        List<String> missing = new ArrayList<>();
        try (var scenes = Files.walk(dir)) {
            for (Path scene : scenes.filter(f -> f.toString().endsWith(".ink") && !f.toString().contains("inky_jump")).toList()) {
                String text = Files.readString(scene, StandardCharsets.UTF_8);
                var g = goalTag.matcher(text);
                while (g.find()) if (!goals.contains(g.group(1))) missing.add(scene.getFileName() + ": goal " + g.group(1));
                var a = act.matcher(text);
                while (a.find()) if (!actions.contains(a.group(1))) missing.add(scene.getFileName() + ": action " + a.group(1));
            }
        }
        assertTrue(missing.isEmpty(), () -> "not in persona.json:\n" + String.join("\n", missing));
    }

    @Test
    void theThrallSceneEndsOnTheDecisionEvenIfTheFounderNeverSawThem() throws Exception {
        Sim sent = new Sim(compile(FOUNDER), Set.of("thrall_sent_away", "thrall_near"));
        sent.story.getVariablesState().set("thrall_called", true);
        assertTrue(!Boolean.TRUE.equals(sent.story.evaluateFunction("thrall_cold_ready")), "the first look plays after the decision");
        assertTrue(Boolean.TRUE.equals(sent.story.evaluateFunction("thrall_after_ready")), "the decision did not end the scene");
        sent.story.choosePathString("thrall_after");
        sent.continueAll();
        assertTrue(sent.said.toString().startsWith("Good."), () -> "said: " + sent.said);

        Sim stayed = new Sim(compile(FOUNDER), Set.of("thrall_stayed"));
        stayed.story.getVariablesState().set("thrall_called", true);
        stayed.story.choosePathString("thrall_after");
        stayed.continueAll();
        assertTrue(stayed.said.toString().startsWith("You let them stay."), () -> "said: " + stayed.said);

        Sim waiting = new Sim(compile(FOUNDER), Set.of());
        waiting.story.getVariablesState().set("thrall_called", true);
        assertTrue(!Boolean.TRUE.equals(waiting.story.evaluateFunction("thrall_after_ready")), "ended before anyone decided");
    }

    @Test
    void aTownWhereNobodyCanTurnStillReachesTheThrallAndTheName() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.actWorks = false;
        sim.story.choosePathString("watch_after");
        sim.continueAll();
        assertTrue((Boolean) sim.story.getVariablesState().get("no_one_turns"), "no_one_turns was not set");
        assertTrue(Boolean.TRUE.equals(sim.story.evaluateFunction("thrall_arrival_ready")), "the thrall never comes");
    }

    @Test
    void theCountArmsTheSlipBeforeAnyChoice() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.choosePathString("the_count");
        sim.continueAll();
        assertTrue(sim.acts.contains("prime_slip"), "the slip is armed only at the end of the talk");
    }

    @Test
    void theLampLineNeedsTheSlipToHaveHappened() throws Exception {
        for (boolean slipped : new boolean[]{false, true}) {
            Sim sim = new Sim(compile(FOUNDER), Set.of());
            sim.story.getVariablesState().set("done_slip", slipped);
            sim.story.choosePathString("the_book.pages");
            sim.continueAll();
            boolean offered = sim.story.getCurrentChoices().stream().anyMatch(c -> c.getText().equals("Carry the lamp yourself."));
            assertTrue(offered == slipped, () -> "lamp choice offered=" + offered + " with done_slip=" + slipped);
        }
    }

    @Test
    void anotherRecruitCanBeBroughtAfterTheFirstOath() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.getVariablesState().set("done_first_oath", true);
        sim.story.choosePathString("dusk");
        sim.continueAll();
        sim.choose("I've brought someone.");
        assertTrue(sim.acts.contains("enlist_recruit"), "enlist_recruit was not run");
        assertTrue(sim.said.toString().contains("They train by the altar"), () -> "said: " + sim.said);
    }

    @Test
    void theCourtNoteWaitsForMorning() throws Exception {
        Sim night = new Sim(compile(FOUNDER), Set.of("night"));
        night.story.getVariablesState().set("court_came", true);
        assertTrue(!Boolean.TRUE.equals(night.story.evaluateFunction("court_note_ready")), "the note plays the same night");
        Sim morning = new Sim(compile(FOUNDER), Set.of());
        morning.story.getVariablesState().set("court_came", true);
        assertTrue(Boolean.TRUE.equals(morning.story.evaluateFunction("court_note_ready")), "the note never plays");
    }

    @Test
    void theCountTalkCallsThePlayerOverAfterTheName() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        for (String flag : List.of("offered", "founded", "wolf_seen", "altar_started", "done_lodge", "named", "done_counting")) {
            sim.story.getVariablesState().set(flag, true);
        }
        sim.story.getVariablesState().set("met", 2);
        assertTrue(Boolean.TRUE.equals(sim.story.evaluateFunction("count_talk_ready")), "the count talk is not a scene");
        assertTrue("What were you reciting, that night?".equals(String.valueOf(sim.story.evaluateFunction("menu"))),
                "the menu line is not the question");
    }

    @Test
    void theQuestionIsMarkedOnTheNeighborTheyNamed() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.choosePathString("neighbor_offer");
        sim.continueAll();
        sim.choose("I'll ask.");
        String named = String.valueOf(sim.story.getVariablesState().get("neighbor"));
        assertTrue(sim.acts.contains("asked_about@" + named), () -> "acts: " + sim.acts + ", neighbor: " + named);
    }

    @Test
    void theWolfIsOnlySpoiledIfSomebodyFedIt() throws Exception {
        for (int fed : new int[]{0, 2}) {
            Sim sim = new Sim(compile(FOUNDER), Set.of());
            sim.story.getVariablesState().set("meat_given", fed);
            sim.story.choosePathString("shepherd_arrive");
            sim.continueAll();
            boolean spoiled = sim.said.toString().contains("Somebody keeps spoiling it.");
            assertTrue(spoiled == (fed > 0), () -> "spoiled=" + spoiled + " with meat_given=" + fed);
        }
    }

    @Test
    void leavingTheCountTalkOpensTheAnotherHunterQuest() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.choosePathString("the_count");
        sim.continueAll();
        sim.choose("I'll leave it.");
        assertTrue(sim.story.getState().visitCountAtPathString("another_hunter") > 0, "the quest was never opened");
    }

    @Test
    void theFetchQuestsHandBackWithTheirOldLines() throws Exception {
        Map<String, String> handBacks = Map.of("collar_ask.done", "So I can hear it coming.",
                "bone_ask.done", "Give it here.", "werewolf_signs.done", "Good. That's good.");
        for (var entry : handBacks.entrySet()) {
            Sim sim = new Sim(compile(FOUNDER), Set.of());
            sim.story.choosePathString(entry.getKey());
            sim.continueAll();
            assertTrue(sim.said.toString().contains(entry.getValue()), () -> entry.getKey() + " said: " + sim.said);
        }
    }

    @Test
    void arrivalsCallThePlayerOver() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        for (String flag : List.of("offered", "founded", "wolf_seen", "altar_started", "done_lodge", "done_bitten")) {
            sim.story.getVariablesState().set(flag, true);
        }
        sim.story.getVariablesState().set("met", 2);
        assertTrue("What is it?".equals(String.valueOf(sim.story.evaluateFunction("menu"))), "the thrall arrival has no menu line");
        Object call = sim.story.evaluateFunction("calling");
        assertTrue(call != null && !call.toString().isEmpty(), "the founder does not call the player over");
    }

    @Test
    void theStolenKillEndsWithAChoice() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.choosePathString("werewolf_kill");
        sim.continueAll();
        assertTrue(sim.said.toString().startsWith("You saw that."), () -> "said: " + sim.said);
        sim.choose("It did your job for you.");
        assertTrue((Boolean) sim.story.getVariablesState().get("done_w3"), "done_w3 was not set");
        assertTrue(sim.acts.contains("hunger_small"), () -> "acts: " + sim.acts);
    }

    @Test
    void theLodgeHandBackOpensTheRecruitJob() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.choosePathString("altar.done");
        sim.continueAll();
        sim.choose("It's yours.");
        assertTrue(sim.story.getState().visitCountAtPathString("recruit_job") > 0, "the recruit quest was never opened");
    }

    @Test
    void bringingSomeoneFollowsWhetherTheyWereTakenOn() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        String start = sim.story.getState().toJson();
        sim.story.choosePathString("recruit_job.waiting");
        sim.continueAll();
        sim.choose("I've brought someone.");
        assertTrue(sim.acts.contains("enlist_recruit"), "enlist_recruit was not run");
        assertTrue(sim.said.toString().contains("They train by the altar"), () -> "said: " + sim.said);

        sim.story.getState().loadJson(start);
        sim.said.setLength(0);
        sim.actWorks = false;
        sim.story.choosePathString("recruit_job.waiting");
        sim.continueAll();
        sim.choose("I've brought someone.");
        assertTrue(sim.said.toString().contains("Not this one.") && !sim.said.toString().contains("They train"),
                () -> "a refused recruit was taken on: " + sim.said);
    }

    @Test
    void theRecruitHandBackIsTheFirstOath() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.choosePathString("recruit_job.done");
        sim.continueAll();
        assertTrue((Boolean) sim.story.getVariablesState().get("done_first_oath"), "scene 6 did not play");
    }

    @Test
    void theOathIsOfferedAfterTheFirstOathAndCanBePutOff() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.getVariablesState().set("done_first_oath", true);
        assertTrue(Boolean.TRUE.equals(sim.story.evaluateFunction("player_oath_offer_ready")), "the oath was not offered");
        String start = sim.story.getState().toJson();
        sim.story.choosePathString("player_oath_offer");
        sim.continueAll();
        sim.choose("Not yet.");
        assertTrue(!Boolean.TRUE.equals(sim.story.evaluateFunction("player_oath_offer_ready")), "offered again the same day");
        sim.story.getVariablesState().set("today", 1);
        assertTrue(Boolean.TRUE.equals(sim.story.evaluateFunction("player_oath_offer_ready")), "never offered again");

        sim.story.getState().loadJson(start);
        sim.story.choosePathString("player_oath_offer");
        sim.continueAll();
        sim.choose("I'll take the oath.");
        assertTrue(sim.story.getState().visitCountAtPathString("player_oath") > 0, "the oath quest was never opened");
        sim.story.choosePathString("player_oath.waiting");
        sim.continueAll();
        sim.choose("I'm ready.");
        assertTrue(sim.acts.contains("swear_player"), "swear_player was not run");
    }

    @Test
    void aTownWithNoOneToBeAboutGetsSomeoneBittenInTheWatch() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.choosePathString("watch_after");
        sim.continueAll();
        assertTrue(sim.acts.contains("bite_one"), "no one was bitten");
        assertTrue((Boolean) sim.story.getVariablesState().get("done_watch"), "the watch did not end");
    }

    @Test
    void offeringScrapsOpensTheJobAndGoesBackToTheTopics() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.getVariablesState().set("founded", true);
        sim.story.choosePathString("wolf");
        sim.continueAll();
        sim.choose("What do you need scraps for?");
        sim.choose("I'll find you some.");
        assertTrue(sim.story.getState().visitCountAtPathString("scraps_job") > 0, "the quest knot was never entered");
        assertTrue((Boolean) sim.story.getVariablesState().get("scraps_offered"), "scraps_offered was not set");
        assertTrue(sim.story.getCurrentChoices().stream().anyMatch(c -> c.getText().equals("I'll leave you to it.")),
                "the scene did not go back to its topics");
    }

    @Test
    void meatCountsForTheJobOnlyWhileItIsOpen() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.getVariablesState().set("wolf_seen", true);
        assertTrue(!sim.gives(), "meat counted before the job was offered");
        sim.story.getVariablesState().set("scraps_offered", true);
        assertTrue(sim.gives(), "meat did not count while the job was open");
        sim.story.getVariablesState().set("done_scraps_job", true);
        assertTrue(!sim.gives(), "meat counted after the job was handed back");
    }

    @Test
    void handingBackTheJobClosesIt() throws Exception {
        Sim sim = new Sim(compile(FOUNDER), Set.of());
        sim.story.getVariablesState().set("scraps_offered", true);
        sim.story.choosePathString("scraps_job.done");
        sim.continueAll();
        sim.choose("That's the last of it.");
        assertTrue((Boolean) sim.story.getVariablesState().get("done_scraps_job"), "done_scraps_job was not set");
        assertTrue(!sim.story.canContinue() && sim.story.getCurrentChoices().isEmpty(), "the hand-back did not end");
    }

    /**
     * From the start, following the marked choices reaches {@code act} within a few choices; and
     * from every choice the player can reach on the way, the marked way forward is on screen or one
     * choice away.
     */
    private static void assertSceneLeadsTo(Path dir, String knot, Set<String> trueChecks, String act) throws Exception {
        Sim sim = new Sim(compile(dir), trueChecks);
        sim.story.choosePathString(knot);
        String start = sim.story.getState().toJson();

        List<String> path = sim.followAdvance(start, act, 8);
        assertTrue(path != null, () -> "following the marked choices from " + knot + " never reached act(\"" + act + "\")");

        List<String> problems = new ArrayList<>();
        sim.explore(start, act, 6, new ArrayList<>(), problems);
        assertTrue(problems.isEmpty(), () -> "the way forward is not marked or not near:\n" + String.join("\n", problems));
    }

    private static Path locate(Path dir) {
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(dir))) return root.resolve(dir);
        }
        return dir;
    }

    private static String compile(Path dir) throws IOException {
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(dir))) {
                dir = root.resolve(dir);
                break;
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put("persona.ink", Files.readString(dir.resolve("persona.ink"), StandardCharsets.UTF_8));
        Path base = dir;
        try (var scenes = Files.walk(dir)) {
            for (Path scene : scenes.filter(f -> f.toString().endsWith(".ink")).sorted().toList()) {
                files.putIfAbsent(base.relativize(scene).toString().replace(java.io.File.separatorChar, '/'), Files.readString(scene, StandardCharsets.UTF_8));
            }
        }
        StoryCompiler.Result result = StoryCompiler.compile(files);
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        return result.json();
    }

    private static final class Sim {
        final Story story;
        final List<String> acts = new ArrayList<>();
        final StringBuilder said = new StringBuilder();
        boolean actWorks = true;

        Sim(String json, Set<String> trueChecks) throws Exception {
            story = new Story(json);
            for (String name : StoryCompiler.EXTERNALS.keySet()) {
                story.bindExternalFunction(name, args -> stub(name, args, trueChecks), false);
            }
        }

        private Object stub(String name, Object[] args, Set<String> trueChecks) {
            return switch (name) {
                case "check" -> trueChecks.contains(String.valueOf(args[0]));
                case "here", "is", "mod", "can_build", "contract_accept", "contract_skip" -> false;
                case "count", "rel", "contract_ready", "contract_active", "contract_turn_in" -> 0;
                case "act" -> {
                    acts.add(String.valueOf(args[0]));
                    yield actWorks;
                }
                case "act_on" -> {
                    acts.add(args[0] + "@" + args[1]);
                    yield actWorks;
                }
                case "trust", "contribute", "memory" -> null;
                default -> "";
            };
        }

        /** Runs on from {@code state}; true when {@code act} happened. */
        private boolean run(String state, int choice, String act) throws Exception {
            story.getState().loadJson(state);
            acts.clear();
            if (choice >= 0) story.chooseChoiceIndex(choice);
            while (story.canContinue()) story.Continue();
            return acts.contains(act);
        }

        void continueAll() throws Exception {
            while (story.canContinue()) said.append(story.Continue());
        }

        /** Picks the choice with this text and plays on. */
        void choose(String text) throws Exception {
            List<Choice> choices = choices();
            int index = -1;
            for (int i = 0; i < choices.size(); i++) if (choices.get(i).getText().equals(text)) index = i;
            assertTrue(index >= 0, () -> "no choice \"" + text + "\" in " + choices.stream().map(Choice::getText).toList());
            story.chooseChoiceIndex(index);
            continueAll();
        }

        /** Plays the raw-meat gift reply; true when it counted the meat for the job. */
        boolean gives() throws Exception {
            acts.clear();
            story.choosePathString("gift_meat");
            continueAll();
            return acts.contains("fed_wolf");
        }

        private List<Choice> choices() {
            return new ArrayList<>(story.getCurrentChoices());
        }

        private static boolean advance(Choice choice) {
            return choice.getTags() != null && choice.getTags().stream().anyMatch(t -> t.trim().equals(StorySession.ADVANCE_TAG));
        }

        /** Takes a marked choice when there is one, else the first; returns the path, or null. */
        List<String> followAdvance(String state, String act, int limit) throws Exception {
            List<String> path = new ArrayList<>();
            if (run(state, -1, act)) return path;
            for (int step = 0; step < limit; step++) {
                List<Choice> choices = choices();
                if (choices.isEmpty()) return null;
                int pick = 0;
                for (int i = 0; i < choices.size(); i++) if (advance(choices.get(i))) { pick = i; break; }
                path.add(choices.get(pick).getText());
                String now = story.getState().toJson();
                if (run(now, pick, act)) return path;
            }
            return null;
        }

        /** Every choice point before {@code act}: a marked choice on screen, or one on each next screen. */
        void explore(String state, String act, int depth, List<String> path, List<String> problems) throws Exception {
            if (run(state, -1, act) || depth == 0) return;
            List<Choice> choices = choices();
            if (choices.isEmpty()) return;
            String here = story.getState().toJson();
            boolean marked = choices.stream().anyMatch(Sim::advance);
            for (int i = 0; i < choices.size(); i++) {
                String text = choices.get(i).getText();
                if (run(here, i, act)) continue;
                List<Choice> next = choices();
                if (!marked && !next.isEmpty() && next.stream().noneMatch(Sim::advance)) {
                    problems.add("  " + String.join(" > ", path) + " > [" + text + "]: two screens with no marked way forward");
                }
                if (next.isEmpty()) continue;
                List<String> deeper = new ArrayList<>(path);
                deeper.add(text);
                explore(story.getState().toJson(), act, depth - 1, deeper, problems);
                story.getState().loadJson(here);
            }
        }
    }
}
