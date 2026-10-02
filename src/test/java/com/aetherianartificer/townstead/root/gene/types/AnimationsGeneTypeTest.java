package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneExpression;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationsGeneTypeTest {
    @Test void explicitProviderOrderSurvivesTheCatalogDescriptor() {
        var json = JsonParser.parseString("""
                {"providers":["minecraft:zombie","humanoid"]}
                """).getAsJsonObject();
        assertEquals("minecraft:zombie;humanoid", new AnimationsGeneType().parse(json).display().targetId());
    }
    @Test void vanillaZombieAnimationUsesTheStandardExpressionCondition() {
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.ConstantConditionType());
        var json = JsonParser.parseString("""
                {"animation":"minecraft:zombie","condition":{"type":"pheno:constant","value":false}}
                """).getAsJsonObject();
        var type = new AnimationsGeneType();
        var gene = type.parse(json);
        assertNotNull(gene);
        assertEquals(GeneDisplay.Kind.ANIMATIONS, gene.display().kind());
        assertEquals("minecraft:zombie", gene.display().targetId());
        assertFalse(GeneExpression.parse(json, type).test(null));
        json.addProperty("animation", "custom:zombie");
        assertNull(type.parse(json));
    }
}
