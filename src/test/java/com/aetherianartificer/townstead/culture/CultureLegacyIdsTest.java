package com.aetherianartificer.townstead.culture;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CultureLegacyIdsTest {
    private static final ResourceLocation OLD = ResourceLocation.tryParse("test:briarguard");
    private static final ResourceLocation CURRENT = ResourceLocation.tryParse("test:rosaguarda");

    @AfterEach
    void clear() {
        Cultures.replace(Map.of());
    }

    @Test
    void oldCultureIdResolvesToCanonicalDefinitionWithoutBecomingAssignable() {
        Culture culture = new Culture(CURRENT, Component.literal("Rosaguarda"), null);
        Cultures.replace(Map.of(CURRENT, culture), Map.of(OLD, CURRENT));

        assertSame(culture, Cultures.get(OLD));
        assertEquals(CURRENT, Cultures.canonicalId(OLD));
        assertEquals(java.util.Set.of(CURRENT), Cultures.allIds());
    }
}
