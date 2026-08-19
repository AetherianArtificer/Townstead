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

    @Test
    void shimmerHasSmallDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:shimmer"},
                  {"type":"townstead:shimmer","strength":0.6,"speed":1.5,"interval":5.5,"color":"#FFF4CC"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:shimmer", defaults.type().toString());
        assertEquals(0.35f, defaults.strength());
        assertEquals(1f, defaults.speed());
        assertEquals(3.6f, defaults.interval());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.6f, custom.strength());
        assertEquals(1.5f, custom.speed());
        assertEquals(5.5f, custom.interval());
        assertEquals(0xFFF4CC, custom.color());
    }

    @Test
    void pulseHasRestrainedDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:pulse"},
                  {"type":"townstead:pulse","strength":0.45,"speed":0.8,"color":"#D8B4FF"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:pulse", defaults.type().toString());
        assertEquals(0.25f, defaults.strength());
        assertEquals(1f, defaults.speed());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.45f, custom.strength());
        assertEquals(0.8f, custom.speed());
        assertEquals(0xD8B4FF, custom.color());
    }

    @Test
    void flowHasSimpleDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:flow"},
                  {"type":"townstead:flow","strength":0.5,"speed":1.4,"color":"#A8ECFF"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:flow", defaults.type().toString());
        assertEquals(0.30f, defaults.strength());
        assertEquals(1f, defaults.speed());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.5f, custom.strength());
        assertEquals(1.4f, custom.speed());
        assertEquals(0xA8ECFF, custom.color());
    }

    @Test
    void liquidHasAdjustableSpringPhysicsAndOptionalColour() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:liquid"},
                  {"type":"townstead:liquid","strength":0.5,"speed":1.4,
                   "color":"#D8B4FF","surface_points":16,
                   "tension":0.28,"damping":0.94,"splash":0.8,"movement_influence":0.35}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:liquid", defaults.type().toString());
        assertEquals(0.35f, defaults.strength());
        assertEquals(1f, defaults.speed());
        assertEquals(1f, defaults.frequency());
        assertEquals(-1, defaults.color());
        assertEquals(12, defaults.surfacePoints());
        assertEquals(0.18f, defaults.tension());
        assertEquals(0.92f, defaults.damping());
        assertEquals(0.65f, defaults.splash());
        assertEquals(0.20f, defaults.movementInfluence());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.5f, custom.strength());
        assertEquals(1.4f, custom.speed());
        assertEquals(1f, custom.frequency());
        assertEquals(0xD8B4FF, custom.color());
        assertEquals(16, custom.surfacePoints());
        assertEquals(0.28f, custom.tension());
        assertEquals(0.94f, custom.damping());
        assertEquals(0.8f, custom.splash());
        assertEquals(0.35f, custom.movementInfluence());
    }

    @Test
    void oldWaveEffectNameIsNotAnAlias() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parse("""
                {"max":100,"display":{"effects":[{"type":"townstead:wave"}]}}
                """));
        assertTrue(error.getMessage().contains("Unknown resource bar effect"));
    }

    @Test
    void viscousHasGooeyDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:viscous"},
                  {"type":"townstead:viscous","strength":0.7,"speed":0.8,
                   "color":"#B7FF74","lobes":7,"viscosity":0.86,"stringiness":0.72}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:viscous", defaults.type().toString());
        assertEquals(0.55f, defaults.strength());
        assertEquals(0.65f, defaults.speed());
        assertEquals(5, defaults.lobeCount());
        assertEquals(0.78f, defaults.viscosity());
        assertEquals(0.55f, defaults.stringiness());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.7f, custom.strength());
        assertEquals(0.8f, custom.speed());
        assertEquals(7, custom.lobeCount());
        assertEquals(0.86f, custom.viscosity());
        assertEquals(0.72f, custom.stringiness());
        assertEquals(0xB7FF74, custom.color());
    }

    @Test
    void bubblesHavePixelParticleDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:bubbles"},
                  {"type":"townstead:bubbles","strength":0.8,"speed":1.2,
                   "color":"#E8FFFF","density":9,"size":3,"wobble":0.6}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:bubbles", defaults.type().toString());
        assertEquals(0.65f, defaults.strength());
        assertEquals(0.85f, defaults.speed());
        assertEquals(6, defaults.bubbleCount());
        assertEquals(2, defaults.bubbleSize());
        assertEquals(0.35f, defaults.bubbleWobble());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.8f, custom.strength());
        assertEquals(1.2f, custom.speed());
        assertEquals(9, custom.bubbleCount());
        assertEquals(3, custom.bubbleSize());
        assertEquals(0.6f, custom.bubbleWobble());
        assertEquals(0xE8FFFF, custom.color());
    }

    @Test
    void embersHaveCoolingParticleDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:embers"},
                  {"type":"townstead:embers","strength":0.82,"speed":1.3,
                   "density":12,"drift":0.7,"flicker":0.4,"escape":0.9,
                   "hot_color":"#FFF0A0","cool_color":"#A82B18"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:embers", defaults.type().toString());
        assertEquals(0.70f, defaults.strength());
        assertEquals(0.95f, defaults.speed());
        assertEquals(8, defaults.emberCount());
        assertEquals(0.45f, defaults.emberDrift());
        assertEquals(0.65f, defaults.emberFlicker());
        assertEquals(0.8f, defaults.emberEscape());
        assertEquals(-1, defaults.color());
        assertEquals(-1, defaults.shadowColor());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.82f, custom.strength());
        assertEquals(1.3f, custom.speed());
        assertEquals(12, custom.emberCount());
        assertEquals(0.7f, custom.emberDrift());
        assertEquals(0.4f, custom.emberFlicker());
        assertEquals(0.9f, custom.emberEscape());
        assertEquals(0xFFF0A0, custom.color());
        assertEquals(0xA82B18, custom.shadowColor());
    }

    private static ResourceGeneType.Instance parse(String json) {
        return (ResourceGeneType.Instance) new ResourceGeneType().parse(
                JsonParser.parseString(json).getAsJsonObject());
    }
}
