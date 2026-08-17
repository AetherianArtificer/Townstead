package com.aetherianartificer.townstead.needs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedDefinitionResourcesTest {
    private static JsonObject resource(String path) throws IOException {
        try (InputStream stream = NeedDefinitionResourcesTest.class.getResourceAsStream("/" + path)) {
            if (stream == null) throw new IOException("Missing classpath resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void farmAndCharmTeaIsDataDefined() throws IOException {
        JsonObject tea = resource("data/townstead/consumable/farm_and_charm_tea.json");
        assertEquals("townstead:consumable/v1", tea.get("schema").getAsString());
        assertTrue(tea.get("fallback").getAsBoolean());
        assertEquals("pheno:hydrate", tea.getAsJsonObject("effects").get("type").getAsString());
        assertTrue(tea.getAsJsonArray("items").size() >= 6);

        JsonObject soups = resource("data/townstead/consumable/farm_and_charm_soups.json");
        assertTrue(soups.get("fallback").getAsBoolean());
        assertTrue(soups.getAsJsonArray("items").asList().stream()
                .anyMatch(value -> "farm_and_charm:goulash".equals(value.getAsString())));
        assertEquals(4, soups.getAsJsonObject("effects").get("immediate").getAsInt());
        assertEquals(2, soups.getAsJsonObject("effects").get("lasting").getAsInt());
    }

    @Test
    void sweetEnergySourcesAreDataDefinedThroughNeutralTags() throws IOException {
        JsonObject chocolate = resource("data/townstead/consumable/chocolate.json");
        assertEquals("#townstead:chocolate", chocolate.getAsJsonArray("items").get(0).getAsString());
        assertEquals("pheno:energize", chocolate.getAsJsonObject("effects").get("type").getAsString());
        assertEquals(2, chocolate.getAsJsonObject("effects").get("amount").getAsInt());

        JsonObject sugarySnacks = resource("data/townstead/consumable/sugary_snacks.json");
        assertEquals("#townstead:sugary_snacks", sugarySnacks.getAsJsonArray("items").get(0).getAsString());
        assertEquals(1, sugarySnacks.getAsJsonObject("effects").get("amount").getAsInt());

        JsonObject sugaryTag = resource("data/townstead/tags/item/sugary_snacks.json");
        assertTrue(sugaryTag.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> value.isJsonPrimitive() && "minecraft:cookie".equals(value.getAsString())));
    }

    @Test
    void bakerySinkUsesPhenoForStateAndEffect() throws IOException {
        JsonObject sink = resource("data/townstead/amenity/bakery_kitchen_sink.json");
        assertEquals("townstead:amenity/v1", sink.get("schema").getAsString());
        assertEquals("pheno:use_block", sink.getAsJsonObject("prepare").get("type").getAsString());
        assertEquals("pheno:hydrate", sink.getAsJsonObject("effect").get("type").getAsString());
    }
}
