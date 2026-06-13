package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.port.PowerToGeneConverter;
import com.aetherianartificer.townstead.origin.port.PowerToGeneConverter.ConvertedGene;
import com.aetherianartificer.townstead.origin.port.PowerToGeneConverter.Skip;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PowerToGeneConverter ports for the movement powers: step_height (step-up half) and sprinting
 * (force-sprint, with requires_input gated on pheno:moving).
 */
class StepHeightPortTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    private static List<ConvertedGene> convert(String powerJson) {
        List<ConvertedGene> genes = new ArrayList<>();
        List<Skip> skips = new ArrayList<>();
        Map<ResourceLocation, JsonObject> recipes = new HashMap<>();
        PowerToGeneConverter.convert("townstead_origins", ResourceLocation.tryParse("test:tall_step"),
                obj(powerJson), genes, skips, recipes);
        return genes;
    }

    @Test
    void upperHeightBecomesAStepHeightGene() {
        List<ConvertedGene> genes = convert(
                "{ 'type':'apugli:step_height', 'upper_height':1.0, 'lower_height':0.5, 'allow_jump_after':true }");
        assertEquals(1, genes.size());
        JsonObject gene = genes.get(0).json();
        assertEquals("pheno:step_height", gene.get("type").getAsString());
        assertEquals(1.0, gene.get("amount").getAsDouble(), "upper_height becomes amount; the rest is dropped");
    }

    @Test
    void stepDownOnlyPowerConvertsToNothing() {
        List<ConvertedGene> genes = convert("{ 'type':'apugli:step_height', 'lower_height':0.5 }");
        assertTrue(genes.isEmpty(), "no step-up half means no portable gene");
    }

    @Test
    void sprintingPortsToTheSprintingAbility() {
        List<ConvertedGene> genes = convert("{ 'type':'apugli:sprinting' }");
        assertEquals(1, genes.size());
        JsonObject gene = genes.get(0).json();
        assertEquals("pheno:ability", gene.get("type").getAsString());
        assertEquals("sprinting", gene.get("ability").getAsString());
        assertFalse(gene.has("condition"), "unconditional force-sprint");
    }

    @Test
    void requiresInputGatesSprintingOnMoving() {
        List<ConvertedGene> genes = convert("{ 'type':'apugli:sprinting', 'requires_input':true }");
        JsonObject gene = genes.get(0).json();
        assertEquals("sprinting", gene.get("ability").getAsString());
        assertEquals("pheno:moving", gene.getAsJsonObject("condition").get("type").getAsString());
    }
}
