package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.port.ApoliConditionTranslator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Apoli's entity biome condition, including the deprecated category sub-condition, ports onto pheno:biome. */
class BiomePortTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void categoryMapsToTheVanillaBiomeTagWhenOneExists() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apoli:biome', 'condition':{ 'type':'apoli:category', 'category':'forest' } }"));
        assertNotNull(out);
        assertEquals("pheno:biome", out.get("type").getAsString());
        assertEquals("minecraft:is_forest", out.get("biome_tag").getAsString());
    }

    @Test
    void categoryWithoutAVanillaTagFallsBackToApolisTag() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apoli:biome', 'condition':{ 'type':'apoli:category', 'category':'swamp' } }"));
        assertNotNull(out);
        assertEquals("apoli:category/swamp", out.get("biome_tag").getAsString());
    }

    @Test
    void inTagSubConditionBecomesABiomeTag() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apoli:biome', 'condition':{ 'type':'apoli:in_tag', 'tag':'c:is_hot' } }"));
        assertNotNull(out);
        assertEquals("c:is_hot", out.get("biome_tag").getAsString());
    }

    @Test
    void aBiomesListBecomesAnOr() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apoli:biome', 'biomes':['minecraft:plains','minecraft:desert'] }"));
        assertNotNull(out);
        assertEquals("pheno:or", out.get("type").getAsString());
        assertEquals(2, out.getAsJsonArray("conditions").size());
        assertEquals("minecraft:plains",
                out.getAsJsonArray("conditions").get(0).getAsJsonObject().get("biome").getAsString());
    }
}
