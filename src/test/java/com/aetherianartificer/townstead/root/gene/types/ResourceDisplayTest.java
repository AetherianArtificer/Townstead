package com.aetherianartificer.townstead.root.gene.types;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDisplayTest {
    @Test
    void legacyResourceKeepsCompatibleDefaults() {
        ResourceGeneType.Instance resource = parse("{\"max\":100}");
        assertEquals(ResourceDisplay.Shape.HORIZONTAL, resource.resourceDisplay().shape());
        assertEquals(ResourceDisplay.FillMode.CONTINUOUS, resource.resourceDisplay().fillMode());
        assertEquals(ResourceDisplay.Anchor.TOP_LEFT, resource.resourceDisplay().anchor());
        assertEquals(ResourceDisplay.Eligibility.WHEN_EXPRESSED, resource.resourceDisplay().eligibility());
        assertEquals(0x3FA0FF, resource.color());
        assertTrue(resource.resourceDisplay().effects().isEmpty());
        assertFalse(resource.persistOnDeath());
    }

    @Test
    void resourceColorAndPresentationParse() {
        ResourceGeneType.Instance resource = parse("""
                {"min":-10,"max":30,"start":0,"persist_on_death":true,"color":"#A01020",
                 "display":{"shape":"circle","fill_mode":"separated","segments":12,
                 "frame":"example:bones","color_theme":"example:blood","visibility":"when_referenced",
                 "effects":[{"type":"townstead:gradient","preset":"glossy","shape":"leading_edge",
                 "strength":0.4,"highlight_color":"#E8FFFF","shadow_color":"#163A66"}],
                 "anchor":"bottom_center","priority":7}}
                """);
        assertEquals(ResourceDisplay.Shape.SQUIRCLE, resource.resourceDisplay().shape());
        assertEquals(ResourceDisplay.FillMode.PIPS, resource.resourceDisplay().fillMode());
        assertEquals(ResourceDisplay.Eligibility.WHEN_REFERENCED, resource.resourceDisplay().eligibility());
        assertEquals(ResourceDisplay.Anchor.BOTTOM_CENTER, resource.resourceDisplay().anchor());
        assertEquals(12, resource.resourceDisplay().segments());
        assertEquals(7, resource.resourceDisplay().priority());
        assertEquals(0xA01020, resource.color());
        assertEquals(1, resource.resourceDisplay().effects().size());
        assertEquals("townstead:gradient", resource.resourceDisplay().effects().get(0).type().toString());
        assertEquals(0.4f, resource.resourceDisplay().effects().get(0).strength());
        assertEquals("leading_edge", resource.resourceDisplay().effects().get(0).gradientShape());
        assertEquals(0xE8FFFF, resource.resourceDisplay().effects().get(0).highlightColor());
        assertEquals(0x163A66, resource.resourceDisplay().effects().get(0).shadowColor());
        assertTrue(resource.persistOnDeath());
    }

    @Test
    void gradientPresetSuppliesConvenientDefaults() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:gradient","preset":"subtle"}
                ]}}
                """);
        ResourceDisplay.BarEffect effect = resource.resourceDisplay().effects().get(0);
        assertEquals(0.15f, effect.strength());
        assertEquals("crosswise", effect.gradientShape());
        assertEquals(-1, effect.highlightColor());
        assertEquals(-1, effect.shadowColor());
    }

    private static ResourceGeneType.Instance parse(String json) {
        return (ResourceGeneType.Instance) new ResourceGeneType().parse(
                JsonParser.parseString(json).getAsJsonObject());
    }
}
