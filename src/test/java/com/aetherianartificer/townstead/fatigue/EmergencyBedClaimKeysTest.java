package com.aetherianartificer.townstead.fatigue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EmergencyBedClaimKeysTest {
    @Test
    void claimKeyIsDimensionScoped() {
        long samePos = 12345L;
        String overworld = EmergencyBedClaimKeys.claimKey("minecraft:overworld", samePos);
        String nether = EmergencyBedClaimKeys.claimKey("minecraft:the_nether", samePos);

        assertNotEquals(overworld, nether);
    }

    @Test
    void claimKeyUsesUnknownForMissingDimension() {
        assertEquals("unknown|42", EmergencyBedClaimKeys.claimKey(null, 42L));
        assertEquals("unknown|42", EmergencyBedClaimKeys.claimKey("", 42L));
        assertEquals("unknown|42", EmergencyBedClaimKeys.claimKey("   ", 42L));
    }
}
