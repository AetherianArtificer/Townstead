package com.aetherianartificer.townstead.root.gene.types;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinOverlayGeneTypeTest {

    @Test
    void parsesColorBlendAndStrength() {
        var json = JsonParser.parseString("""
                {
                  "texture": "test:textures/overlay/piglin.png",
                  "tint": "skin",
                  "order": 8,
                  "tint_blend": "color",
                  "tint_strength": 0.65
                }
                """).getAsJsonObject();

        var parsed = (SkinOverlayGeneType.Instance) new SkinOverlayGeneType().parse(json);

        assertEquals("test:textures/overlay/piglin.png", parsed.texture());
        assertEquals("skin", parsed.tint());
        assertEquals(8, parsed.order());
        assertEquals(3, parsed.tintBlend());
        assertEquals(0.65f, parsed.tintStrength());
        assertEquals("test:textures/overlay/piglin.png;skin;8;3;0.65",
                parsed.display().targetId());
    }

    @Test
    void defaultsToFullMultiplyAndClampsStrength() {
        var defaults = (SkinOverlayGeneType.Instance) new SkinOverlayGeneType().parse(
                JsonParser.parseString("{\"texture\":\"test:plain\"}").getAsJsonObject());
        var clamped = (SkinOverlayGeneType.Instance) new SkinOverlayGeneType().parse(
                JsonParser.parseString("""
                        { "texture": "test:strong", "tint_strength": 2.0 }
                        """).getAsJsonObject());

        assertEquals(0, defaults.tintBlend());
        assertEquals(1f, defaults.tintStrength());
        assertEquals(1f, clamped.tintStrength());
    }
}
