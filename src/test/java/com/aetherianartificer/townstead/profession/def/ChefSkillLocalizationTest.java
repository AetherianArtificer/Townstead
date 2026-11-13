package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChefSkillLocalizationTest {

    private static final List<String> SKILLS = List.of(
            "pass", "hands", "expedite", "all_day", "chef_de_cuisine",
            "chefs_knife", "julienne", "hot_pan", "sizzle", "dinner_rush",
            "plating", "honed_edge", "running_the_pass", "family_meal", "in_the_weeds");

    private static final Map<String, String> REQUIRES = Map.ofEntries(
            Map.entry("hands", "pass"),
            Map.entry("expedite", "hands"),
            Map.entry("all_day", "expedite"),
            Map.entry("chef_de_cuisine", "all_day"),
            Map.entry("julienne", "chefs_knife"),
            Map.entry("hot_pan", "julienne"),
            Map.entry("sizzle", "hot_pan"),
            Map.entry("dinner_rush", "sizzle"),
            Map.entry("honed_edge", "plating"),
            Map.entry("running_the_pass", "honed_edge"),
            Map.entry("family_meal", "running_the_pass"),
            Map.entry("in_the_weeds", "family_meal"));

    @Test
    void everyChefSkillUsesKeysPresentInBothLocaleSources() {
        JsonObject assets = resource("assets/townstead/lang/en_us.json");
        JsonObject sidecar = resource("data/townstead/lang/en_us.json");

        for (String skill : SKILLS) {
            JsonObject definition = resource("data/townstead/profession/cook/path/chef/skill/"
                    + skill + ".json");
            String nameKey = definition.getAsJsonObject("display_name")
                    .get("translate").getAsString();
            String descriptionKey = definition.getAsJsonObject("description")
                    .get("translate").getAsString();

            assertEquals("skill.townstead.cook.chef." + skill, nameKey);
            assertEquals(nameKey + ".description", descriptionKey);
            assertTrue(assets.has(nameKey), "missing client name: " + nameKey);
            assertTrue(assets.has(descriptionKey), "missing client description: " + descriptionKey);
            assertTrue(sidecar.has(nameKey), "missing data-pack name: " + nameKey);
            assertTrue(sidecar.has(descriptionKey),
                    "missing data-pack description: " + descriptionKey);
        }
    }

    @Test
    void chefLanesAreAuthoredAsRealSkillDependencies() {
        for (String skill : SKILLS) {
            JsonObject definition = resource("data/townstead/profession/cook/path/chef/skill/"
                    + skill + ".json");
            String required = REQUIRES.get(skill);
            if (required == null) {
                assertTrue(!definition.has("requires"),
                        "first-rank skill should start a lane: " + skill);
                continue;
            }
            assertEquals(1, definition.getAsJsonArray("requires").size(),
                    "Chef lane should have one preceding skill: " + skill);
            assertEquals(required,
                    definition.getAsJsonArray("requires").get(0).getAsString(),
                    "wrong preceding skill for " + skill);
        }
    }

    private static JsonObject resource(String path) {
        InputStream stream = ChefSkillLocalizationTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, "missing resource: " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("failed to read " + path, exception);
        }
    }
}
