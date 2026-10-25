package com.aetherianartificer.townstead.recognition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A type's {@code core} is one of its own recipe groups. A core the recipe does not hold is
 * silently ignored at runtime, and the type falls back to its rarest group, so catch it here.
 */
class SiteCoreResourceTest {
    @Test
    void everyCoreNamesAGroupOfItsOwnRecipe() throws IOException, URISyntaxException {
        Path extended = Path.of(SiteCoreResourceTest.class.getResource("/data/townstead/extended_buildings").toURI());
        // Beside our own data: MCA's jar holds a building_types folder of its own.
        Path recipes = extended.getParent().getParent().resolve("mca/building_types");
        Path compatRecipes = extended.getParent().getParent().getParent().resolve("townstead_compat/building_types");
        List<String> problems = new ArrayList<>();
        int cores = 0;
        try (Stream<Path> files = Files.walk(extended)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject entry = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!entry.has("core")) continue;
                cores++;
                String type = extended.relativize(file).toString().replace('\\', '/').replace(".json", "");
                Path recipe = recipes.resolve(type + ".json");
                // The build moves compat recipes out of data/ so they load only with their mod.
                if (!Files.exists(recipe)) recipe = compatRecipes.resolve(type + ".json");
                if (!Files.exists(recipe)) {
                    problems.add(type + ": no MCA recipe");
                    continue;
                }
                JsonObject blocks = JsonParser.parseString(Files.readString(recipe, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("blocks");
                String core = entry.get("core").getAsString();
                if (!blocks.has(core)) problems.add(type + ": core " + core + " is not in " + blocks.keySet());
            }
        }
        assertTrue(cores > 10, "the open-air types name their cores, saw " + cores);
        assertEquals(List.of(), problems);
    }
}
