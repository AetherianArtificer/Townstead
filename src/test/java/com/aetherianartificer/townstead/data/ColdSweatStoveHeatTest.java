package com.aetherianartificer.townstead.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ColdSweatStoveHeatTest {
    private JsonObject definition() throws Exception {
        String path = "data/townstead/cold_sweat/block/block_temp/farmersdelight_stove.json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void optionalIntegrationOnlyHeatsLitFarmersDelightStoves() throws Exception {
        var data = definition();
        assertEquals(JsonParser.parseString("[\"cold_sweat\",\"farmersdelight\"]"), data.get("required_mods"));
        var block = data.getAsJsonObject("block");
        assertEquals(JsonParser.parseString("[\"farmersdelight:stove\"]"), block.get("blocks"));
        assertTrue(block.getAsJsonObject("state").get("lit").getAsBoolean());
    }
    @Test void furnaceEquivalentEffectUsesBackendUnitsRangeAndCumulativeCap() throws Exception {
        var data = definition();
        assertEquals("mc", data.get("units").getAsString());
        assertEquals(0.33, data.get("temperature").getAsDouble());
        assertEquals(7, data.get("range").getAsInt());
        assertEquals(0.88, data.get("max_effect").getAsDouble());
        assertEquals(12.6, data.get("max_temp").getAsDouble());
        assertTrue(data.get("fade").getAsBoolean());
        assertTrue(data.get("max_effect").getAsDouble() > data.get("temperature").getAsDouble(),
                "Multiple stoves must be able to contribute before reaching the shared limit");
    }
}
