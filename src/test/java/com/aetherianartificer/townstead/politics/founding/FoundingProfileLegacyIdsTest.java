package com.aetherianartificer.townstead.politics.founding;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FoundingProfileLegacyIdsTest {
    private static final ResourceLocation OLD = ResourceLocation.tryParse("test:briarguard");
    private static final ResourceLocation CURRENT = ResourceLocation.tryParse("test:rosaguarda");

    @AfterEach
    void clear() {
        FoundingProfiles.replace(Map.of());
    }

    @Test
    void oldProfileIdResolvesWithoutAppearingInProfileSuggestions() {
        var government = new FoundingProfileDefinition.Government(
                ResourceLocation.tryParse("test:council"), "{village} Council", List.of());
        var profile = new FoundingProfileDefinition(CURRENT, Component.literal("Rosaguarda"),
                null, 1.0F, null, null, null, government);
        FoundingProfiles.replace(Map.of(CURRENT, profile), Map.of(OLD, CURRENT));

        assertSame(profile, FoundingProfiles.get(OLD));
        assertEquals(CURRENT, FoundingProfiles.canonicalId(OLD));
        assertEquals(List.of(CURRENT), FoundingProfiles.ids());
    }
}
