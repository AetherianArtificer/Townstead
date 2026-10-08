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
                    yield null;
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
