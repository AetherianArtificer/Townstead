package com.aetherianartificer.townstead.story;

import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plays the midwife's three visits against the founder's side of the thread, scripted: the bread
 * reaches the founder, the wolf is wounded, the midpoint plays once the book is delivered, and the
 * founder comes when brought. A run fails when a visit never plays or she never calls the player over
 * for a visit that is ready.
 */
class MidwifeArcTest {
    private static final Path MIDWIFE = Path.of("src/main/resources/data/townstead/persona/midwife");

    private static final List<String> STYLES = List.of("plain", "leave_meeting", "ask_night_early", "never_carry",
            "cure", "castout", "unsure_calm", "unsure_hungry", "never_bring");

    @Test
    void everyWayThroughTheThreeVisits() throws Exception {
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

    private static final class World {
        final Story story;
        final String style;
        final Set<String> checks = new HashSet<>();
        final Set<String> acts = new HashSet<>();
        final List<String> trail = new ArrayList<>();
        final List<String> heard = new ArrayList<>();
        final List<String> uncalled = new ArrayList<>();
        int day;
        int breadDay = -1;
        int bookDay = -1;
        int bringDay = -1;
        boolean leftMeeting;
        boolean founderHere;

        World(String json, String style) throws Exception {
            this.style = style;
            story = new Story(json);
            for (String name : StoryCompiler.EXTERNALS.keySet()) {
                story.bindExternalFunction(name, args -> external(name, args), false);
            }
            var vars = story.getVariablesState();
            vars.set("given_name", "Agnes");
            vars.set("family_name", "Roth");
            if (style.equals("unsure_hungry")) checks.add("founder_hungry");
        }

        Object var(String name) {
            return story.getVariablesState().get(name);
        }

        boolean flag(String name) {
            return Boolean.TRUE.equals(var(name));
        }

        private Object external(String name, Object[] args) {
            String a = args.length > 0 && args[0] != null ? String.valueOf(args[0]) : "";
            return switch (name) {
                case "check" -> checks.contains(a);
                case "act" -> {
                    acts.add(a);
                    yield true;
                }
                case "act_on" -> {
                    acts.add("on:" + a);
                    yield true;
                }
                case "here" -> a.equals("dhampir_founder") && founderHere;
                case "count", "rel" -> 0;
                case "demeanor" -> "neutral";
                case "mod", "can_build", "contract_accept" -> false;
                case "contract_ready", "contract_active", "contract_turn_in" -> 0;
                default -> name.equals("building") || name.equals("who") || name.equals("persona_name")
                        || name.equals("roll") || name.equals("career_path") || name.equals("most_harvested") ? "" : null;
            };
        }

        /** The founder's side, as their story would set it. */
        private void newDay() throws Exception {
            day++;
            story.getVariablesState().set("today", day);
            story.getVariablesState().set("chance", 1 + day * 37 % 100);
            // The player gives them the bread a day after carrying it.
            if (breadDay >= 0 && day == breadDay + 1) checks.add("bread_delivered");
            if (day == 10) checks.add("wounded_seen");
            if (style.equals("cure") && day == 12) checks.add("asked_cure");
            // The founder reads the book, and the midpoint follows a few days on.
            if (bookDay >= 0 && day == Math.max(bookDay + 3, 16)) checks.add("after_midpoint");
            founderHere = bringDay >= 0 && day > bringDay + 1 && !style.equals("never_bring");
        }

        /** Whether a visit of hers is ready to play today. */
        private boolean visitReady() {
            if (flag("asked_bring") && !flag("confessed") && founderHere) return true;
            if (checks.contains("after_midpoint") && flag("told_night") && !flag("asked_bring")) return true;
            if (checks.contains("bread_delivered") && !flag("told_birth")) return true;
            return flag("told_birth") && checks.contains("wounded_seen") && !flag("told_night");
        }

        String missing() {
            List<String> miss = new ArrayList<>();
            if (!flag("bread_given") || !acts.contains("give_bread")) miss.add("bread never given");
            if (!flag("told_birth")) miss.add("visit 1 (the birth) never played");
            if (!style.equals("never_carry") && !acts.contains("heard_birth")) miss.add("heard_birth not set");
            if (!flag("told_night") || !acts.contains("give_book")) miss.add("visit 2 (the night, the book) never played");
            if (!flag("asked_bring") || !acts.contains("asked_bring")) miss.add("she never asked to see the founder");
            if (flag("asked_bring") && heard.stream().noneMatch(t -> t.contains("Have you asked them") || t.contains("Not yet? That's all right")))
                miss.add("no reminder while the founder is not brought");
            if (style.equals("cure") && !visited("talk.cure")) miss.add("cure never told");
            if (style.equals("never_bring")) {
                if (flag("confessed")) miss.add("confessed without the founder");
            } else {
                if (!flag("confessed")) miss.add("visit 3 (the confession) never played");
                boolean castOut = style.equals("castout") || style.equals("unsure_hungry");
                if (castOut && !acts.contains("cast_out")) miss.add("not cast out");
                if (!castOut && !acts.contains("forgiven")) miss.add("not forgiven");
                if (!acts.contains("on:founder_home")) miss.add("the founder's trip never ended");
            }
            if (!uncalled.isEmpty()) miss.add("no call-over for a ready visit on " + uncalled);
            return String.join("; ", miss);
        }

        boolean visited(String path) {
            try {
                return story.getState().visitCountAtPathString(path) > 0;
            } catch (Exception e) {
                return false;
            }
        }

        void play() throws Exception {
            for (int i = 0; i < 30; i++) {
                newDay();
                Object menu = story.evaluateFunction("menu");
                if (menu == null || String.valueOf(menu).isBlank()) throw new IllegalStateException("menu() gave nothing");
                Object greeting = story.evaluateFunction("greeting");
                if (greeting == null || String.valueOf(greeting).isBlank()) throw new IllegalStateException("greeting() gave nothing");
                if (visitReady() && String.valueOf(story.evaluateFunction("calling")).isBlank()) uncalled.add("day " + day);
                boolean hadBook = acts.contains("give_book");
                boolean hadBread = acts.contains("give_bread");
                converse("greet");
                if (!hadBread && acts.contains("give_bread")) breadDay = day;
                if (!hadBook && acts.contains("give_book")) bookDay = day;
                if (flag("asked_bring") && bringDay < 0) bringDay = day;
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
                // Closes the dialogue in the middle of the first meeting.
                if (style.equals("leave_meeting") && !leftMeeting && !picks.isEmpty()) {
                    leftMeeting = true;
                    break;
                }
                int pick = pick(choices, chosen);
                chosen.add(choices.get(pick).getText());
                picks.add(choices.get(pick).getText());
                story.chooseChoiceIndex(pick);
            }
            trail.add("day " + day + ": " + said.toString().strip().split("\n", 2)[0] + "  " + picks);
            heard.add(said.toString());
        }

        private int pick(List<Choice> all, Set<String> chosen) {
            String want = switch (style) {
                case "castout" -> "She left her to die.";
                case "unsure_calm", "unsure_hungry" -> "It isn't my place to say.";
                case "never_carry" -> "Some things are better left.";
                default -> "";
            };
            for (int i = 0; i < all.size(); i++) if (all.get(i).getText().equals(want)) return i;
            boolean early = style.equals("ask_night_early");
            for (int i = 0; i < all.size(); i++) {
                String t = all.get(i).getText();
                if (early && t.startsWith("Where were you")) return i;
            }
            for (int i = 0; i < all.size(); i++) {
                String t = all.get(i).getText();
                if (!early && t.startsWith("Where were you")) continue;
                if (!chosen.contains(t) && !t.equals("I should go.") && !t.equals("I'll let you rest.")) return i;
            }
            return all.size() - 1;
        }

        String log() {
            List<String> flags = new ArrayList<>();
            for (String name : List.of("met", "bread_given", "told_birth", "asked_night", "told_night", "asked_bring", "confessed", "forgiven")) {
                flags.add(name + "=" + var(name));
            }
            int from = Math.max(0, trail.size() - 12);
            return "day=" + day + " checks=" + checks + " acts=" + acts + "\nflags: " + String.join(", ", flags)
                    + "\nlast:\n" + String.join("\n", trail.subList(from, trail.size()));
        }
    }

    private static String compile() throws IOException {
        Path dir = MIDWIFE;
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(MIDWIFE))) {
                dir = root.resolve(MIDWIFE);
                break;
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put("persona.ink", Files.readString(dir.resolve("persona.ink"), StandardCharsets.UTF_8));
        StoryCompiler.Result result = StoryCompiler.compile(files);
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        return result.json();
    }
}
