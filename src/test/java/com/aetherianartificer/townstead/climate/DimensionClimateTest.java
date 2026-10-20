package com.aetherianartificer.townstead.climate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DimensionClimateTest {
    private static DimensionClimate parse(String json) {
        return DimensionClimate.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static JsonObject shipped(String name) {
        var stream = DimensionClimateTest.class.getResourceAsStream("/data/minecraft/climate/dimension/" + name + ".json");
        assertNotNull(stream, name);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void biomeDimensionsKeepEverythingOnByDefault() {
        var climate = parse("{ \"temperature\": \"biomes\" }");
        assertTrue(climate.usesBiomes());
        assertTrue(climate.seasons() && climate.dayCycle() && climate.weather() && climate.underground());
    }

    @Test
    void fixedTemperatureTurnsOffSkyAndGroundUnlessAsked() {
        var climate = parse("{ \"temperature\": -20, \"day_cycle\": true }");
        assertFalse(climate.usesBiomes());
        assertEquals(-20f, climate.fixedCelsius());
        assertTrue(climate.dayCycle());
        assertFalse(climate.weather());
        assertFalse(climate.underground());
    }

    @Test
    void layersAddUpByHeight() {
        var climate = parse("""
                { "layers": [ { "above_y": 200, "offset_c": -10 }, { "below_y": 0, "offset_c": 4 },
                              { "above_y": 250, "offset_c": -5 } ] }
                """);
        assertEquals(0f, climate.layerOffset(64));
        assertEquals(-10f, climate.layerOffset(220));
        assertEquals(-15f, climate.layerOffset(260));
        assertEquals(4f, climate.layerOffset(-30));
    }

    @Test
    void badValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("{ \"temperature\": \"lava\" }"));
        assertThrows(IllegalArgumentException.class, () -> parse("{ \"season_clock\": \"moon\" }"));
    }

    @Test
    void shippedVanillaDimensionsParse() {
        assertTrue(DimensionClimate.parse(shipped("overworld")).usesBiomes());
        assertEquals(45f, DimensionClimate.parse(shipped("the_nether")).fixedCelsius());
        assertEquals(5f, DimensionClimate.parse(shipped("the_end")).fixedCelsius());
        assertFalse(DimensionClimate.parse(shipped("the_end")).seasons());
    }
}
