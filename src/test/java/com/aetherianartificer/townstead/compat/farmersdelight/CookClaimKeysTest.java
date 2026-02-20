package com.aetherianartificer.townstead.compat.farmersdelight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CookClaimKeysTest {

    @Test
    void claimKeyIsDimensionScoped() {
        long samePos = 123456789L;
        String overworld = CookClaimKeys.claimKey("minecraft:overworld", samePos);
        String nether = CookClaimKeys.claimKey("minecraft:the_nether", samePos);
        assertNotEquals(overworld, nether);
    }

    @Test
    void claimKeyIsStableForSameInputs() {
        String dim = "minecraft:overworld";
        long pos = -987654321L;
        String key1 = CookClaimKeys.claimKey(dim, pos);
        String key2 = CookClaimKeys.claimKey(dim, pos);
        assertEquals(key1, key2);
    }

    @Test
    void blankDimensionFallsBackToUnknown() {
        assertEquals("unknown|42", CookClaimKeys.claimKey("", 42L));
        assertEquals("unknown|42", CookClaimKeys.claimKey("   ", 42L));
        assertEquals("unknown|42", CookClaimKeys.claimKey(null, 42L));
    }
}
