package com.aetherianartificer.townstead.ritual;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RitualDefinitionTest {

    @Test
    void bundledHunterOathParses() throws Exception {
        try (var in = RitualDefinitionTest.class.getResourceAsStream("/data/townstead/ritual/hunter_oath.json")) {
            assertNotNull(in);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            RitualDefinition ritual = RitualDefinition.parse(ResourceLocation.tryParse("townstead:hunter_oath"), json);
            assertEquals(ResourceLocation.tryParse("townstead:lodge_master"), ritual.officiantOffice());
            assertTrue(ritual.outcome().joinOrder());
            assertNotNull(ritual.offering());
            assertTrue(ritual.offering().tag());
            assertTrue(ritual.steps().stream().anyMatch(step -> step.role() == RitualDefinition.Role.WITNESSES));
        }
    }

    @Test
    void stepPastDurationIsRejected() {
        JsonObject json = JsonParser.parseString("""
                { "schema": "townstead:ritual/v1", "place": { "block": "townstead:oath_altar" }, "duration": 20,
                  "steps": [ { "at": 10, "role": "all", "ticks": 20 } ] }""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,
                () -> RitualDefinition.parse(ResourceLocation.tryParse("townstead:bad"), json));
    }
}
