package com.aetherianartificer.townstead.root.appearance;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HairPolicyTest {

    @Test
    void absentDeclarationInherits() {
        HairPolicy policy = HairPolicy.parse(JsonParser.parseString("{}").getAsJsonObject());
        assertFalse(policy.isSpecified());
    }

    @Test
    void acceptsCompactBooleanForm() {
        HairPolicy policy = HairPolicy.parse(
                JsonParser.parseString("{\"hair\":false}").getAsJsonObject());
        assertTrue(policy.isSpecified());
        assertEquals(Boolean.FALSE, policy.enabled());
    }

    @Test
    void acceptsExtensibleObjectForm() {
        HairPolicy policy = HairPolicy.parse(
                JsonParser.parseString("{\"hair\":{\"enabled\":true}}").getAsJsonObject());
        assertTrue(policy.isSpecified());
        assertEquals(Boolean.TRUE, policy.enabled());
    }

    @Test
    void parsesFriendlyAndGeneticColorAxes() {
        HairPolicy policy = HairPolicy.parse(JsonParser.parseString("""
                {"hair":{"color_ranges":[
                  {"darkness":[0.0,0.15],"redness":{"min":0.6,"max":1.0},"weight":4},
                  {"eumelanin":0.5,"pheomelanin":[0.2,0.3]}
                ]}}
                """).getAsJsonObject());
        assertTrue(policy.isSpecified());
        assertEquals(2, policy.colorRanges().size());
        assertEquals(0.15f, policy.colorRanges().get(0).darkness().max());
        assertEquals(0.6f, policy.colorRanges().get(0).redness().min());
        assertEquals(4, policy.colorRanges().get(0).weight());
        assertEquals(0.5f, policy.colorRanges().get(1).darkness().min());
    }

    @Test
    void parsesExactFantasyPaletteAndWeights() {
        HairPolicy policy = HairPolicy.parse(JsonParser.parseString("""
                {"hair":{"colors":[
                  "#4A78FF",
                  {"color":"#FF73C6","weight":3},
                  "not-a-color"
                ]}}
                """).getAsJsonObject());
        assertTrue(policy.isSpecified());
        assertEquals(2, policy.colors().size());
        assertEquals(0x4A78FF, policy.colors().get(0).rgb());
        assertEquals(1, policy.colors().get(0).weight());
        assertEquals(0xFF73C6, policy.colors().get(1).rgb());
        assertEquals(3, policy.colors().get(1).weight());
    }

    @Test
    void parsesWeightedMultiStopGradients() {
        HairPolicy policy = HairPolicy.parse(JsonParser.parseString("""
                {"hair":{"gradients":[
                  {"stops":["#4A78FF","#8F55E8","#FF73C6"],"weight":4,"space":"hsv"},
                  {"from":"#112233","to":"#AABBCC","space":"rgb"}
                ]}}
                """).getAsJsonObject());
        assertEquals(2, policy.gradients().size());
        assertEquals(List.of(0x4A78FF, 0x8F55E8, 0xFF73C6), policy.gradients().get(0).stops());
        assertEquals(4, policy.gradients().get(0).weight());
        assertEquals(HairGradient.Space.HSV, policy.gradients().get(0).space());
        assertEquals(HairGradient.Space.RGB, policy.gradients().get(1).space());
    }

    @Test
    void multiStopGradientPassesThroughAuthoredStops() {
        HairGradient gradient = new HairGradient(
                List.of(0xFF0000, 0x00FF00, 0x0000FF), 1, HairGradient.Space.RGB);
        assertEquals(0xFFFF0000, gradient.colorAt(0f));
        assertEquals(0xFF00FF00, gradient.colorAt(0.5f));
        assertEquals(0xFF0000FF, gradient.colorAt(1f));
        assertEquals(0xFF00FF00, gradient.nearest(0xFF00FF00));
    }

    @Test
    void rangeClampsToItsNearestAllowedColor() {
        HairColorRange range = new HairColorRange(
                new com.aetherianartificer.townstead.root.GeneRange(0.2f, 0.4f),
                new com.aetherianartificer.townstead.root.GeneRange(0.7f, 0.9f), 1);
        assertEquals(0.2f, range.clampDarkness(0.0f));
        assertEquals(0.3f, range.clampDarkness(0.3f));
        assertEquals(0.9f, range.clampRedness(1.0f));
        assertEquals(0f, range.distanceSquared(0.3f, 0.8f));
    }
}
