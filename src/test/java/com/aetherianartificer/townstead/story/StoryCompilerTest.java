package com.aetherianartificer.townstead.story;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryCompilerTest {

    @Test
    void shippedDhampirFounderPersonaCompiles() throws IOException {
        Path dir = Path.of("src/main/resources/data/townstead/persona/dhampir_founder");
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(dir))) {
                dir = root.resolve(dir);
                break;
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put("persona.ink", Files.readString(dir.resolve("persona.ink"), StandardCharsets.UTF_8));
        files.put("meeting.ink", Files.readString(dir.resolve("meeting.ink"), StandardCharsets.UTF_8));
        files.put("wolf.ink", Files.readString(dir.resolve("wolf.ink"), StandardCharsets.UTF_8));
        files.put("altar.ink", Files.readString(dir.resolve("altar.ink"), StandardCharsets.UTF_8));
        files.put("garlic.ink", Files.readString(dir.resolve("garlic.ink"), StandardCharsets.UTF_8));
        files.put("volunteers.ink", Files.readString(dir.resolve("volunteers.ink"), StandardCharsets.UTF_8));
        files.put("oath.ink", Files.readString(dir.resolve("oath.ink"), StandardCharsets.UTF_8));
        files.put("walk.ink", Files.readString(dir.resolve("walk.ink"), StandardCharsets.UTF_8));
        files.put("watch.ink", Files.readString(dir.resolve("watch.ink"), StandardCharsets.UTF_8));
        files.put("neighbor.ink", Files.readString(dir.resolve("neighbor.ink"), StandardCharsets.UTF_8));
        files.put("bitten.ink", Files.readString(dir.resolve("bitten.ink"), StandardCharsets.UTF_8));
        files.put("thrall.ink", Files.readString(dir.resolve("thrall.ink"), StandardCharsets.UTF_8));
        files.put("counting.ink", Files.readString(dir.resolve("counting.ink"), StandardCharsets.UTF_8));
        files.put("name.ink", Files.readString(dir.resolve("name.ink"), StandardCharsets.UTF_8));
        files.put("contracts.ink", Files.readString(dir.resolve("contracts.ink"), StandardCharsets.UTF_8));
        StoryCompiler.Result result = StoryCompiler.compile(files);
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        assertTrue(result.warnings().isEmpty(), () -> "warnings: " + result.warnings());
        assertNotNull(result.knots().get("greet"));
        assertNotNull(result.knots().get("meeting"));
        assertNotNull(result.knots().get("wolf"));
        assertNotNull(result.knots().get("gift_meat"));
        assertNotNull(result.knots().get("altar"));
        assertNotNull(result.knots().get("garlic"));
        assertNotNull(result.knots().get("volunteers"));
        assertNotNull(result.knots().get("first_oath"));
        assertNotNull(result.knots().get("walk_after"));
        assertNotNull(result.knots().get("watch_after"));
        assertNotNull(result.knots().get("neighbor_back"));
        assertNotNull(result.knots().get("bitten_after"));
        assertNotNull(result.knots().get("thrall_after"));
        assertNotNull(result.knots().get("counting"));
        assertNotNull(result.knots().get("the_name"));
    }

    @Test
    void shippedResidentVampireStoryCompiles() throws IOException {
        StoryCompiler.Result result = StoryCompiler.compile(folder("resident_vampire"));
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        assertTrue(result.warnings().isEmpty(), () -> "warnings: " + result.warnings());
        assertNotNull(result.knots().get("greet"));
    }

    @Test
    void shippedGrievingThrallStoryCompiles() throws IOException {
        StoryCompiler.Result result = StoryCompiler.compile(folder("grieving_thrall"));
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        assertTrue(result.warnings().isEmpty(), () -> "warnings: " + result.warnings());
        assertNotNull(result.knots().get("greet"));
    }

    @Test
    void shippedHuntmasterStoryCompiles() throws IOException {
        StoryCompiler.Result result = StoryCompiler.compile(folder("huntmaster"));
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        assertTrue(result.warnings().isEmpty(), () -> "warnings: " + result.warnings());
        assertNotNull(result.knots().get("greet"));
    }

    @Test
    void helpersAreDeclaredUnlessTheWriterDeclaresThem() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("story.ink", "EXTERNAL who(role)\n=== greet ===\nHi {who(\"cook\")}. {check(\"have 1 farmer\"): Busy.}\n-> DONE\n");
        StoryCompiler.Result result = StoryCompiler.compile(files);
        assertTrue(result.ok(), () -> "errors: " + result.errors());
    }

    @Test
    void errorsNameTheFileAndLine() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("story.ink", "=== greet ===\n-> nowhere\n");
        StoryCompiler.Result result = StoryCompiler.compile(files);
        assertFalse(result.ok());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("'story.ink' line 2"), result.errors().get(0));
    }

    @Test
    void tagsGroupByKey() {
        Map<String, List<String>> tags = Stories.tagMap(List.of("quest: Beds", "goal: have 3 farmers",
                "goal: wait 2 days", "Skip If: have 3 farmers", "emote"));
        assertEquals(List.of("Beds"), tags.get("quest"));
        assertEquals(List.of("have 3 farmers", "wait 2 days"), tags.get("goal"));
        assertEquals(List.of("have 3 farmers"), tags.get("skip if"));
        assertFalse(tags.containsKey("emote"));
    }

    @Test
    void shippedFarmerLoadsWithoutThrowing() throws IOException {
        loadPersona("farmer");
    }

    @Test
    void shippedVillageBuilderLoadsWithoutThrowing() throws IOException {
        StoryCompiler.Result compiled = loadPersona("village_builder");
        StoryCompiler.Knot pot = compiled.knots().get("pot");
        assertNotNull(pot);
        assertTrue(pot.tags().contains("quest: Something in the pot"));
        assertTrue(pot.stitches().containsAll(List.of("done", "skipped", "waiting")));
    }

    /** Compiles a shipped Persona and runs it through the loader, the way world load does. */
    private static StoryCompiler.Result loadPersona(String id) throws IOException {
        Path relative = Path.of("src/main/resources/data/townstead/persona", id);
        Path dir = relative;
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(relative))) {
                dir = root.resolve(relative);
                break;
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        Path base = dir;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                files.put(base.relativize(file).toString().replace(java.io.File.separatorChar, '/'), Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        StoryCompiler.Result compiled = StoryCompiler.compile(files.entrySet().stream()
                .filter(e -> e.getKey().endsWith(".ink"))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll));
        assertTrue(compiled.ok(), () -> "errors: " + compiled.errors());
        compiled.knots().values().forEach(knot -> assertNotNull(knot.tags(), knot.name()));
        List<String> issues = new java.util.ArrayList<>();
        Stories.build(net.minecraft.resources.ResourceLocation.tryParse("townstead:persona/" + id), files, issues,
                net.minecraft.resources.ResourceLocation.tryParse("townstead:" + id), new LinkedHashMap<>());
        return compiled;
    }

    private static Map<String, String> folder(String id) throws IOException {
        Path relative = Path.of("src/main/resources/data/townstead/story", id);
        Path dir = relative;
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isDirectory(root.resolve(relative))) {
                dir = root.resolve(relative);
                break;
            }
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put("story.ink", Files.readString(dir.resolve("story.ink"), StandardCharsets.UTF_8));
        Path base = dir;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".ink")).sorted().toList()) {
                String name = base.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                if (!name.equals("story.ink")) files.put(name, Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
