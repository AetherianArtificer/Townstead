package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledProfessionLayoutTest {

    private static final List<String> PROFESSIONS = List.of(
            "minecraft:armorer", "minecraft:butcher", "minecraft:cartographer",
            "minecraft:farmer", "minecraft:fisherman", "minecraft:fletcher",
            "minecraft:leatherworker", "minecraft:librarian", "minecraft:mason",
            "minecraft:shepherd", "minecraft:toolsmith", "minecraft:weaponsmith",
            "townstead:baker", "townstead:barista", "townstead:cook", "townstead:scribe");

    @Test
    void everyBundledProfessionUsesTheCanonicalSidecarLayout() {
        for (String id : PROFESSIONS) {
            String[] parts = id.split(":", 2);
            String directory = "/data/" + parts[0] + "/profession/" + parts[1] + "/";
            JsonObject profession = resource(directory + "profession.json");
            JsonObject progression = resource(directory + "progression.json");
            JsonObject work = resource(directory + "work.json");

            assertEquals("townstead:profession/v2", profession.get("schema").getAsString(), id);
            for (String misplaced : List.of("poi", "work_tasks", "daily_cap", "max_xp", "levels")) {
                assertFalse(profession.has(misplaced), id + " keeps " + misplaced + " in profession.json");
            }
            assertEquals(ProfessionProgressionOverlay.SCHEMA,
                    progression.get("schema").getAsString(), id);
            assertEquals(ProfessionWorkOverlay.SCHEMA, work.get("schema").getAsString(), id);
            if (id.equals("townstead:cook") || id.equals("townstead:barista")
                    || id.equals("townstead:scribe")) {
                assertTrue(work.get("register_profession").getAsBoolean(),
                        id + " owns registration policy in work.json");
                assertTrue(profession.has("work_sound"),
                        id + " owns its registered work sound in profession.json");
            }
        }
    }

    @Test
    void customProfessionsDeclareOrderedClothingFallbacks() {
        assertEquals(List.of("townstead:baker", "mca:baker"),
                strings(resource("/data/townstead/profession/baker/profession.json"), "clothing"));
        assertEquals(List.of("townstead:cook", "chefsdelight:cook",
                        "chefsdelight:chef", "minecraft:butcher"),
                strings(resource("/data/townstead/profession/cook/profession.json"), "clothing"));
        assertEquals(List.of("townstead:scribe", "iceandfire:scribe", "minecraft:librarian"),
                strings(resource("/data/townstead/profession/scribe/profession.json"), "clothing"));
        assertFalse(resource("/data/townstead/profession/barista/profession.json").has("clothing"),
                "barista keeps its current clothes until a suitable wardrobe is authored");
    }

    private static List<String> strings(JsonObject object, String member) {
        return object.getAsJsonArray(member).asList().stream()
                .map(element -> element.getAsString())
                .toList();
    }

    private static JsonObject resource(String path) {
        InputStream input = BundledProfessionLayoutTest.class.getResourceAsStream(path);
        assertNotNull(input, "shipped resource missing: " + path);
        return JsonParser.parseReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
