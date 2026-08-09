package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Personality;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Runtime contract tests for registry-based MCA. The 1.20 production compile separately proves
// that these compatibility classes retain the legacy enum/registry surface without direct linkage.
//? if neoforge {
class McaRegistryCompatibilityTest {

    @Test
    void personalityResolvesRegistryIdsAndRejectsUnknownReferences() {
        Personality playful = McaPersonalityCompat.resolve("mca:playful").orElseThrow();

        assertEquals("mca:playful", McaPersonalityCompat.id(playful));
        assertEquals("PLAYFUL", McaPersonalityCompat.legacyName(playful));
        assertTrue(McaPersonalityCompat.all().contains(playful));
        assertFalse(McaPersonalityCompat.resolve("townstead_test:definitely_missing").isPresent());
    }

    @Test
    void traitRegistryMethodsAreReflectedFromTheOuterTraitsClass() {
        // Directly initializing Traits in plain JUnit fails verification because MCA expects its
        // mod-loader transformations. Method discovery itself is initialization-free and guards the
        // exact owner regression that broke every registry lookup.
        assertEquals(Traits.class, McaTraitCompat.registryLookupOwner());
        assertEquals(Traits.class, McaTraitCompat.registryRegistrationOwner());
    }
}
//?}
