package com.aetherianartificer.townstead.root.ability;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
