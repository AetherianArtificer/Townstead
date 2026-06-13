package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.port.ApoliConditionTranslator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batch 1 entity-condition ports onto the native conditions. */
class EntityConditionPortTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void fieldlessStatesPortDirectly() {
        assertEquals("pheno:crawling",
                ApoliConditionTranslator.translate(obj("{ 'type':'apugli:crawling' }")).get("type").getAsString());
        assertEquals("pheno:exists",
                ApoliConditionTranslator.translate(obj("{ 'type':'apoli:exists' }")).get("type").getAsString());
    }

    @Test
    void relativeHealthBecomesHealthWithRelativeFlag() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apoli:relative_health', 'comparison':'>=', 'compare_to':0.5 }"));
        assertNotNull(out);
        assertEquals("pheno:health", out.get("type").getAsString());
        assertEquals(0.5, out.get("min").getAsDouble());
        assertTrue(out.get("relative").getAsBoolean());
    }

    @Test
    void fluidHeightCarriesFluidAndThreshold() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apoli:fluid_height', 'fluid':'minecraft:water', 'comparison':'>=', 'compare_to':1.0 }"));
        assertNotNull(out);
        assertEquals("pheno:fluid_height", out.get("type").getAsString());
        assertEquals("minecraft:water", out.get("fluid").getAsString());
        assertEquals(1.0, out.get("min").getAsDouble());
    }

    @Test
    void passengerRecursiveCarriesComparison() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apoli:passenger_recursive', 'comparison':'>', 'compare_to':0 }"));
        assertNotNull(out);
        assertEquals("pheno:passenger_recursive", out.get("type").getAsString());
        assertEquals(">", out.get("comparison").getAsString());
        assertEquals(0, out.get("compare_to").getAsInt());
    }
}
