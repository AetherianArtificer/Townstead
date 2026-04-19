package com.aetherianartificer.townstead.ai.work.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProducerClaimKeysTest {

    @Test
    void claimKeyIsDimensionScoped() {
        long samePos = 123456789L;
        String overworld = ProducerClaimKeys.claimKey("minecraft:overworld", samePos);
        String nether = ProducerClaimKeys.claimKey("minecraft:the_nether", samePos);
        assertNotEquals(overworld, nether);
    }

    @Test
    void claimKeyIsStableForSameInputs() {
        String dim = "minecraft:overworld";
        long pos = -987654321L;
        String key1 = ProducerClaimKeys.claimKey(dim, pos);
        String key2 = ProducerClaimKeys.claimKey(dim, pos);
        assertEquals(key1, key2);
    }

    @Test
    void blankDimensionFallsBackToUnknown() {
        assertEquals("unknown|42", ProducerClaimKeys.claimKey("", 42L));
        assertEquals("unknown|42", ProducerClaimKeys.claimKey("   ", 42L));
        assertEquals("unknown|42", ProducerClaimKeys.claimKey(null, 42L));
    }
}
