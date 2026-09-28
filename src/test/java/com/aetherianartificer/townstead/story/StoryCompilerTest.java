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
    void shippedFarmerStoryCompiles() throws IOException {
        StoryCompiler.Result result = StoryCompiler.compile(folder("farmer"));
        assertTrue(result.ok(), () -> "errors: " + result.errors());
        assertTrue(result.warnings().isEmpty(), () -> "warnings: " + result.warnings());
        assertNotNull(result.knots().get("greet"));
        StoryCompiler.Knot fieldPost = result.knots().get("field_post");
        assertNotNull(fieldPost);
        assertTrue(fieldPost.tags().contains("quest: A plan for the fields"));
        assertTrue(fieldPost.stitches().containsAll(List.of("done", "skipped", "waiting")));
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
    void shippedVillageBuilderLoadsWithoutThrowing() throws IOException {
        Path relative = Path.of("src/main/resources/data/townstead/persona/village_builder");
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
        Stories.build(net.minecraft.resources.ResourceLocation.tryParse("townstead:persona/village_builder"), files, issues,
                net.minecraft.resources.ResourceLocation.tryParse("townstead:village_builder"), new LinkedHashMap<>());
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
