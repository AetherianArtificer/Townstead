package com.aetherianartificer.townstead.data;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ThermalCompatCoverageTest {
    private JsonElement read(String path) throws Exception {
        //? if forge {
        /*path = path.replace("/tags/block/", "/tags/blocks/");
        *///?}
        try (var stream = getClass().getClassLoader().getResourceAsStream("data/" + path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test void everyOptionalLitSourceHasColdSweatAndTanCoverage() throws Exception {
        var covered = new HashSet<String>();
        var directory = Path.of(getClass().getClassLoader().getResource(
                "data/townstead/cold_sweat/block/block_temp").toURI());
        try (var files = Files.list(directory)) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var requirements = json.getAsJsonArray("required_mods");
                assertTrue(requirements.contains(new JsonPrimitive("cold_sweat")));
                for (var block : json.getAsJsonObject("block").getAsJsonArray("blocks")) {
                    var id = block.getAsString();
                    assertTrue(requirements.contains(new JsonPrimitive(id.split(":")[0])), id);
                    if (json.getAsJsonObject("block").getAsJsonObject("state").has("lit"))
                        assertTrue(covered.add(id), "Duplicate Cold Sweat heater: " + id);
                }
            }
        }
        assertFalse(covered.contains("herbalbrews:stove"), "Native SmokerBlock heat must not be duplicated");
        covered.add("herbalbrews:stove");
        var sources = read("townstead/tags/block/thermal/compat_lit_heat_sources.json").getAsJsonObject();
        for (var entry : sources.getAsJsonArray("values")) {
            assertFalse(entry.getAsJsonObject().get("required").getAsBoolean());
            assertTrue(covered.contains(entry.getAsJsonObject().get("id").getAsString()), entry.toString());
        }
        var tan = read("toughasnails/tags/block/heating_blocks.json").getAsJsonObject();
        assertFalse(tan.get("replace").getAsBoolean());
        assertTrue(tan.getAsJsonArray("values").contains(new JsonPrimitive("#townstead:thermal/compat_lit_heat_sources")));
        assertFalse(tan.toString().contains("brew_oven"), "TAN tags cannot check Brewery's enum heat state");
    }

    @Test void breweryResidualHeatIsWeakerAndOffHasNoHeat() throws Exception {
        var lit = read("townstead/cold_sweat/block/block_temp/brewery_oven_lit.json").getAsJsonObject();
        var weak = read("townstead/cold_sweat/block/block_temp/brewery_oven_weak.json").getAsJsonObject();
        assertEquals("lit", lit.getAsJsonObject("block").getAsJsonObject("state").get("heat").getAsString());
        assertEquals("weak", weak.getAsJsonObject("block").getAsJsonObject("state").get("heat").getAsString());
        assertTrue(weak.get("temperature").getAsDouble() < lit.get("temperature").getAsDouble());
        var lso = read("brewery/legendarysurvivaloverhaul/temperature/blocks/brew_oven.json").getAsJsonArray();
        var temperatures = new HashMap<String, Double>();
        for (var entry : lso) {
            var row = entry.getAsJsonObject();
            temperatures.put(row.getAsJsonObject("properties").get("heat").getAsString(), row.get("temperature").getAsDouble());
        }
        assertEquals(0.0, temperatures.get("off"));
        assertTrue(temperatures.get("weak") > 0 && temperatures.get("weak") < temperatures.get("lit"));
    }

    @Test void lsoNativeDefinitionsAreNotReplaced() {
        for (var id : List.of("farmersdelight/stove", "farm_and_charm/stove", "farm_and_charm/roaster",
                "herbalbrews/stove", "candlelight/cobblestone_stove")) {
            var parts = id.split("/");
            assertNull(getClass().getClassLoader().getResource("data/" + parts[0]
                    + "/legendarysurvivaloverhaul/temperature/blocks/" + parts[1] + ".json"), id);
        }
    }
}
