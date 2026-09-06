package com.aetherianartificer.townstead.expression;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionCueTest {
    @Test
    void parsesBoundedPresentationAndAudience() {
        JsonObject json = JsonParser.parseString("""
                {"kind":"text","translation":"expression.example.question","duration_ticks":999,
                 "cooldown_ticks":1,"rise":9,"scale":0,"color":"#80FFEEDD","audience":"nearby"}
                """).getAsJsonObject();
        ExpressionCue cue = ExpressionCue.parse(id("example:question"), json);
        assertEquals(ExpressionCue.Kind.TEXT, cue.kind());
        assertEquals(ExpressionCue.Audience.NEARBY, cue.audience());
        assertEquals(400, cue.durationTicks());
        assertEquals(10, cue.cooldownTicks());
        assertEquals(4f, cue.rise());
        assertEquals(.25f, cue.scale());
        assertEquals(0x80FFEEDD, cue.color());
    }

    @Test
    void bundledPaletteUsesItsOwnNamespaceAndHasRealIconAssets() throws Exception {
        for (String name : List.of("question", "surprise", "annoyed", "embarrassed", "sleepy", "music",
                "idea", "delight")) {
            String path = "/data/townstead_expressions/expression_cue/" + name + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                ExpressionCue cue = ExpressionCue.parse(id("townstead_expressions:" + name), json);
                assertTrue(cue.cooldownTicks() >= cue.durationTicks(), name);
                assertFalse(cue.fallbackParticle().isBlank(), name);
                if (cue.kind() == ExpressionCue.Kind.ICON) {
                    String texturePath = "/assets/townstead_expressions/" + cue.content().substring(
                            "townstead_expressions:".length());
                    assertNotNull(getClass().getResource(texturePath), texturePath);
                }
            }
        }
    }

    @Test
    void rejectsUnlocalizableTextAndInvalidIconIds() {
        assertThrows(IllegalArgumentException.class, () -> ExpressionCue.parse(id("example:bad"),
                JsonParser.parseString("{\"kind\":\"icon\",\"texture\":\"not an id\"}").getAsJsonObject()));
    }

    @Test
    void parsesAndBoundsAuthoredIconAndParticleBursts() {
        ExpressionCue cue = ExpressionCue.parse(id("example:sparks"), JsonParser.parseString("""
                {"kind":"icon","texture":"example:textures/expression/sparks.png",
                 "icon_burst":{"count":99,"spread":9,"vertical_spread":0.4,
                   "stagger_ticks":99,"scale_variance":2},
                 "particle_burst":{"type":"minecraft:flame","count":99,"spread":0.6,
                   "vertical_spread":0.8,"speed":9}}
                """).getAsJsonObject());

        assertEquals(32, cue.iconBurst().count());
        assertEquals(4f, cue.iconBurst().spread());
        assertEquals(0.4f, cue.iconBurst().verticalSpread());
        assertEquals(40, cue.iconBurst().staggerTicks());
        assertEquals(0.9f, cue.iconBurst().scaleVariance());
        assertEquals("minecraft:flame", cue.particleBurst().type());
        assertEquals(64, cue.particleBurst().count());
        assertEquals(2f, cue.particleBurst().speed());
    }

    @Test
    void textBurstCanSupplyAStaggeredLocalizedPhraseSequence() {
        ExpressionCue cue = ExpressionCue.parse(id("example:chatter"), JsonParser.parseString("""
                {"kind":"text","duration_ticks":30,
                 "text_burst":{"translations":["expression.example.opening",
                   "expression.example.aside","expression.example.afterthought"],
                   "spread":0.5,"vertical_spread":0.2,"stagger_ticks":12,"scale_variance":0.15}}
                """).getAsJsonObject());

        assertEquals("expression.example.opening", cue.content());
        assertEquals(List.of("expression.example.opening", "expression.example.aside",
                "expression.example.afterthought"), cue.textBurst().translations());
        assertEquals(12, cue.textBurst().staggerTicks());
        assertEquals(0.5f, cue.textBurst().spread());
    }

    @Test
    void delightIsATransparentExpressionIconAndNotTheAutoSeedControl() throws Exception {
        var delightUrl = getClass().getResource(
                "/assets/townstead_expressions/textures/expression/delight.png");
        var autoSeedUrl = getClass().getResource(
                "/assets/townstead/textures/gui/icon_auto.png");
        assertNotNull(delightUrl);
        assertNotNull(autoSeedUrl);

        var delight = ImageIO.read(delightUrl);
        var autoSeed = ImageIO.read(autoSeedUrl);
        assertEquals(16, delight.getWidth());
        assertEquals(16, delight.getHeight());
        assertTrue(delight.getColorModel().hasAlpha());
        assertFalse(Arrays.equals(
                autoSeed.getRGB(0, 0, autoSeed.getWidth(), autoSeed.getHeight(), null, 0,
                        autoSeed.getWidth()),
                delight.getRGB(0, 0, delight.getWidth(), delight.getHeight(), null, 0,
                        delight.getWidth())));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
