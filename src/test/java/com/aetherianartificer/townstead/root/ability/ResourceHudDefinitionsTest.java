package com.aetherianartificer.townstead.root.ability;

import com.google.gson.JsonParser;
import com.aetherianartificer.townstead.root.gene.types.ResourceDisplay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceHudDefinitionsTest {

    @Test
    void colorThemeParsesBothColors() {
        var json = JsonParser.parseString("""
                {
                  "schema": "townstead:resource_color_theme/v1",
                  "primary_color": "#4A90B8",
                  "secondary_color": "#A8ECFF"
                }
                """).getAsJsonObject();

        ResourceHudDefinitions.ColorTheme theme = ResourceHudDefinitions.parseColorTheme(json);

        assertEquals(0x4A90B8, theme.framePrimaryColor());
        assertEquals(0xA8ECFF, theme.frameSecondaryColor());
    }

    @Test
    void omittedSecondaryColorGetsReadableAccent() {
        var json = JsonParser.parseString("""
                {
                  "schema": "townstead:resource_color_theme/v1",
                  "primary_color": "#B52E3A"
                }
                """).getAsJsonObject();

        ResourceHudDefinitions.ColorTheme theme = ResourceHudDefinitions.parseColorTheme(json);

        assertEquals(0xB52E3A, theme.framePrimaryColor());
        assertEquals(0xD68C93, theme.frameSecondaryColor());
    }

    @Test
    void frameV3ParsesIndependentAuthoredAndTintLayersPerShape() {
        var json = JsonParser.parseString("""
                {
                  "schema": "townstead:resource_frame/v3",
                  "background_color": "#15131B",
                  "thickness": 2,
                  "art": {
                    "horizontal": {
                      "base_texture": "example:textures/gui/resource_frame/runes_h.png",
                      "primary_texture": "example:textures/gui/resource_frame/runes_h_primary.png",
                      "secondary_texture": "example:textures/gui/resource_frame/runes_h_secondary.png"
                    },
                    "squircle": {
                      "base_texture": "example:textures/gui/resource_frame/runes_s.png"
                    }
                  }
                }
                """).getAsJsonObject();

        ResourceHudDefinitions.Frame frame = ResourceHudDefinitions.parseFrame(json);
        ResourceHudDefinitions.FrameArt horizontal = frame.art(ResourceDisplay.Shape.HORIZONTAL);
        assertEquals("example:textures/gui/resource_frame/runes_h.png",
                horizontal.baseTexture().toString());
        assertEquals("example:textures/gui/resource_frame/runes_h_primary.png",
                horizontal.primaryTexture().toString());
        assertEquals("example:textures/gui/resource_frame/runes_h_secondary.png",
                horizontal.secondaryTexture().toString());
        assertNull(frame.art(ResourceDisplay.Shape.VERTICAL));
        assertEquals("example:textures/gui/resource_frame/runes_s.png",
                frame.art(ResourceDisplay.Shape.SQUIRCLE).baseTexture().toString());
    }

    @Test
    void emptyShapeArtIsRejectedInsteadOfSilentlyPretendingToBeCustom() {
        var json = JsonParser.parseString("""
                {"schema":"townstead:resource_frame/v3","art":{"horizontal":{}}}
                """).getAsJsonObject();

        assertThrows(IllegalArgumentException.class,
                () -> ResourceHudDefinitions.parseFrame(json));
    }
}
