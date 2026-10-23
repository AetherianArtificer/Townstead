package com.aetherianartificer.townstead.contract;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContractDefinitionTest {
    private static final List<String> SHIPPED = List.of("put_one_down", "clear_a_nest", "hold_the_fence",
            "garlic", "stakes", "bolts", "fangs", "the_bitten");

    @Test
    void shippedLodgeContractsParse() throws Exception {
        for (String name : SHIPPED) {
            try (var in = ContractDefinitionTest.class.getResourceAsStream("/data/townstead/contract/hunter/" + name + ".json")) {
                assertNotNull(in, name);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                ContractDefinition def = ContractDefinition.parse(ResourceLocation.tryParse("townstead:hunter/" + name), json);
                assertEquals(ResourceLocation.tryParse("townstead:hunter_lodge"), def.pool(), name);
                assertFalse(def.objectives().isEmpty(), name);
            }
        }
    }

    @Test
    void schemaIsRequired() {
        JsonObject json = JsonParser.parseString("""
                { "giver": { "pool": "townstead:x" }, "title": "T",
                  "objectives": [ { "type": "townstead:cure_vampirism" } ] }""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ContractDefinition.parse(ResourceLocation.tryParse("townstead:bad"), json));
    }

    @Test
    void unknownObjectiveTypeIsRejected() {
        JsonObject json = JsonParser.parseString("""
                { "schema": "townstead:contract/v1", "giver": { "pool": "townstead:x" }, "title": "T",
                  "objectives": [ { "type": "townstead:juggle" } ] }""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ContractDefinition.parse(ResourceLocation.tryParse("townstead:bad"), json));
    }

    @Test
    void unknownPlaceholderIsRejected() {
        JsonObject json = JsonParser.parseString("""
                { "schema": "townstead:contract/v1", "giver": { "pool": "townstead:x" }, "title": "Bring {amount}",
                  "objectives": [ { "type": "townstead:cure_vampirism" } ] }""").getAsJsonObject();
        assertThrows(IllegalArgumentException.class, () -> ContractDefinition.parse(ResourceLocation.tryParse("townstead:bad"), json));
    }

    @Test
    void exactPlaceholdersBecomeNumbers() {
        JsonObject raw = JsonParser.parseString("""
                { "count": "{count}", "key": "nest_{mark}", "text": "Bring {{literal}}" }""").getAsJsonObject();
        JsonObject filled = Contracts.filled(raw, Map.of("count", "3", "mark", "42")).getAsJsonObject();
        assertEquals(3, filled.get("count").getAsInt());
        assertTrue(filled.get("count").getAsJsonPrimitive().isNumber());
        assertEquals("nest_42", filled.get("key").getAsString());
        assertEquals("Bring {literal}", filled.get("text").getAsString());
    }
}
