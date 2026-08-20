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

    @Test
    void flamesHaveBaseDefaultsAndOptionalSurfacePlacement() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:flames"},
                  {"type":"townstead:flames","strength":0.9,"speed":1.4,
                   "density":11,"height":8,"flicker":0.55,"placement":"surface",
                   "hot_color":"#FFF4B0","cool_color":"#B83218"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:flames", defaults.type().toString());
        assertEquals(0.8f, defaults.strength());
        assertEquals(1.1f, defaults.speed());
        assertEquals(7, defaults.flameCount());
        assertEquals(7, defaults.flameHeight());
        assertEquals(0.8f, defaults.flameFlicker());
        assertEquals("base", defaults.flamePlacement());
        assertEquals(-1, defaults.color());
        assertEquals(-1, defaults.shadowColor());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.9f, custom.strength());
        assertEquals(1.4f, custom.speed());
        assertEquals(11, custom.flameCount());
        assertEquals(8, custom.flameHeight());
        assertEquals(0.55f, custom.flameFlicker());
        assertEquals("surface", custom.flamePlacement());
        assertEquals(0xFFF4B0, custom.color());
        assertEquals(0xB83218, custom.shadowColor());
    }

    @Test
    void steamHasExpandingPuffDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:steam"},
                  {"type":"townstead:steam","strength":0.9,"speed":1.2,
                   "density":10,"size":4,"drift":0.75,"color":"#C8D8DE"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:steam", defaults.type().toString());
        assertEquals(0.65f, defaults.strength());
        assertEquals(0.75f, defaults.speed());
        assertEquals(6, defaults.steamCount());
        assertEquals(3, defaults.steamSize());
        assertEquals(0.45f, defaults.steamDrift());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.9f, custom.strength());
        assertEquals(1.2f, custom.speed());
        assertEquals(10, custom.steamCount());
        assertEquals(4, custom.steamSize());
        assertEquals(0.75f, custom.steamDrift());
        assertEquals(0xC8D8DE, custom.color());
    }

    @Test
    void electricHasArcDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:electric"},
                  {"type":"townstead:electric","strength":0.9,"speed":1.7,
                   "density":9,"branching":0.7,"reach":0.85,"color":"#E8FFFF"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:electric", defaults.type().toString());
        assertEquals(0.8f, defaults.strength());
        assertEquals(1.25f, defaults.speed());
        assertEquals(5, defaults.electricCount());
        assertEquals(0.45f, defaults.electricBranching());
        assertEquals(0.65f, defaults.electricReach());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.9f, custom.strength());
        assertEquals(1.7f, custom.speed());
        assertEquals(9, custom.electricCount());
        assertEquals(0.7f, custom.electricBranching());
        assertEquals(0.85f, custom.electricReach());
        assertEquals(0xE8FFFF, custom.color());
    }

    @Test
    void wispsHaveSpectralMoteDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:wisps"},
                  {"type":"townstead:wisps","strength":0.9,"speed":1.15,
                   "density":9,"trail":5,"wander":0.85,"color":"#D8FFF2"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:wisps", defaults.type().toString());
        assertEquals(0.7f, defaults.strength());
        assertEquals(0.85f, defaults.speed());
        assertEquals(5, defaults.wispCount());
        assertEquals(3, defaults.wispTrail());
        assertEquals(0.7f, defaults.wispWander());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.9f, custom.strength());
        assertEquals(1.15f, custom.speed());
        assertEquals(9, custom.wispCount());
        assertEquals(5, custom.wispTrail());
        assertEquals(0.85f, custom.wispWander());
        assertEquals(0xD8FFF2, custom.color());
    }

    @Test
    void sparkleHasPixelStarDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:sparkle"},
                  {"type":"townstead:sparkle","strength":0.9,"speed":1.35,
                   "density":11,"size":5,"twinkle":0.8,"color":"#FFF4CC"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:sparkle", defaults.type().toString());
        assertEquals(0.75f, defaults.strength());
        assertEquals(1f, defaults.speed());
        assertEquals(6, defaults.sparkleCount());
        assertEquals(3, defaults.sparkleSize());
        assertEquals(0.65f, defaults.sparkleTwinkle());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.9f, custom.strength());
        assertEquals(1.35f, custom.speed());
        assertEquals(11, custom.sparkleCount());
        assertEquals(5, custom.sparkleSize());
        assertEquals(0.8f, custom.sparkleTwinkle());
        assertEquals(0xFFF4CC, custom.color());
    }

    @Test
    void crystallineHasGlassDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:crystalline"},
                  {"type":"townstead:crystalline","strength":0.82,"speed":1.1,
                   "density":3,"depth":4,"glint":0.75,"color":"#CFF8FF"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:crystalline", defaults.type().toString());
        assertEquals(0.65f, defaults.strength());
        assertEquals(0.65f, defaults.speed());
        assertEquals(2, defaults.crystalCount());
        assertEquals(2, defaults.crystalDepth());
        assertEquals(0.55f, defaults.crystalGlint());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.82f, custom.strength());
        assertEquals(1.1f, custom.speed());
        assertEquals(3, custom.crystalCount());
        assertEquals(4, custom.crystalDepth());
        assertEquals(0.75f, custom.crystalGlint());
        assertEquals(0xCFF8FF, custom.color());
    }

    @Test
    void runesHaveBuiltInDefaultsAndSupportPackGlyphSheets() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:runes"},
                  {"type":"townstead:runes","strength":0.85,"speed":1.2,
                   "mode":"blink","spacing":5,"color":"#E9D2FF",
                   "texture":"example:textures/gui/runes.png","glyph_width":4,
                   "glyph_height":6,"columns":10,"rows":3,"escape":0.8}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:runes", defaults.type().toString());
        assertEquals(0.70f, defaults.strength());
        assertEquals(0.75f, defaults.speed());
        assertEquals("scroll", defaults.runeMode());
        assertEquals(3, defaults.runeSpacing());
        assertEquals("", defaults.runeTexture());
        assertEquals(3, defaults.runeGlyphWidth());
        assertEquals(5, defaults.runeGlyphHeight());
        assertEquals(0.55f, defaults.runeEscape());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals("blink", custom.runeMode());
        assertEquals(5, custom.runeSpacing());
        assertEquals("example:textures/gui/runes.png", custom.runeTexture());
        assertEquals(4, custom.runeGlyphWidth());
        assertEquals(6, custom.runeGlyphHeight());
        assertEquals(10, custom.runeColumns());
        assertEquals(3, custom.runeRows());
        assertEquals(0.8f, custom.runeEscape());
        assertEquals(0xE9D2FF, custom.color());
    }

    @Test
    void corruptionHasCrawlingPatchDefaultsAndOptionalOverrides() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:corruption"},
                  {"type":"townstead:corruption","strength":0.9,"speed":1.25,
                   "density":14,"size":4,"color":"#4D165E"}
                ]}}
                """);

        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals("townstead:corruption", defaults.type().toString());
        assertEquals(0.75f, defaults.strength());
        assertEquals(0.80f, defaults.speed());
        assertEquals(8, defaults.corruptionCount());
        assertEquals(2, defaults.corruptionSize());
        assertEquals(-1, defaults.color());

        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(0.9f, custom.strength());
        assertEquals(1.25f, custom.speed());
        assertEquals(14, custom.corruptionCount());
        assertEquals(4, custom.corruptionSize());
        assertEquals(0x4D165E, custom.color());
    }

    @Test
    void voidHasConfigurablePixelDeletionAndGlitchInstability() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:void"},
                  {"type":"townstead:void","strength":0.9,"speed":1.1,
                   "density":12,"instability":0.8}
                ]}}
                """);
        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals(0.70f, defaults.strength());
        assertEquals(0.75f, defaults.speed());
        assertEquals(6, defaults.voidCount());
        assertEquals(0.60f, defaults.voidInstability());
        ResourceDisplay.BarEffect custom = resource.resourceDisplay().effects().get(1);
        assertEquals(12, custom.voidCount());
        assertEquals(0.8f, custom.voidInstability());
        assertEquals(-1, custom.color());
    }

    @Test
    void prismaticHasAControlledTravellingBand() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:prismatic"},
                  {"type":"townstead:prismatic","strength":0.82,"speed":1.3,
                   "band_width":0.58}
                ]}}
                """);
        ResourceDisplay.BarEffect defaults = resource.resourceDisplay().effects().get(0);
        assertEquals(0.65f, defaults.strength());
        assertEquals(0.70f, defaults.speed());
        assertEquals(0.35f, defaults.prismaticWidth());
        assertEquals(0.58f, resource.resourceDisplay().effects().get(1).prismaticWidth());
    }

    @Test
    void sporesAndFallingMotesHaveDistinctParticleControls() {
        ResourceGeneType.Instance resource = parse("""
                {"max":100,"display":{"effects":[
                  {"type":"townstead:spores","density":11,"size":3,"drift":0.7,
                   "color":"#C9E88A"},
                  {"type":"townstead:falling_motes","density":15,"size":4,"drift":0.6,
                   "color":"#F2F7FF","texture":"example:textures/gui/motes.png",
                   "mark_width":3,"mark_height":4,"columns":9,"rows":3}
                ]}}
                """);
        ResourceDisplay.BarEffect spores = resource.resourceDisplay().effects().get(0);
        assertEquals(11, spores.sporeCount());
        assertEquals(3, spores.sporeSize());
        assertEquals(0.7f, spores.sporeDrift());
        assertEquals(0xC9E88A, spores.color());
        ResourceDisplay.BarEffect falling = resource.resourceDisplay().effects().get(1);
        assertEquals(15, falling.fallingCount());
        assertEquals(4, falling.fallingSize());
        assertEquals(0.6f, falling.fallingDrift());
        assertEquals("example:textures/gui/motes.png", falling.fallingTexture());
        assertEquals(3, falling.fallingMarkWidth());
        assertEquals(4, falling.fallingMarkHeight());
        assertEquals(9, falling.fallingColumns());
        assertEquals(3, falling.fallingRows());
    }

    private static ResourceGeneType.Instance parse(String json) {
        return (ResourceGeneType.Instance) new ResourceGeneType().parse(
                JsonParser.parseString(json).getAsJsonObject());
    }
}
