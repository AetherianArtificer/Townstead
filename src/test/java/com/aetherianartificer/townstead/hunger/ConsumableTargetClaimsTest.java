package com.aetherianartificer.townstead.hunger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConsumableTargetClaimsTest {

    @Test
    void posClaimKeyIsDimensionScoped() {
        String overworld = ConsumableClaimKeys.posClaimKey("minecraft:overworld", "consumable", 42L);
        String nether = ConsumableClaimKeys.posClaimKey("minecraft:the_nether", "consumable", 42L);

        assertNotEquals(overworld, nether);
    }

    @Test
    void slotClaimKeyDistinguishesSlotsAndSides() {
        String base = ConsumableClaimKeys.slotClaimKey("minecraft:overworld", "consumable", 99L, 3, false, null);
        String otherSlot = ConsumableClaimKeys.slotClaimKey("minecraft:overworld", "consumable", 99L, 4, false, null);
        String otherSide = ConsumableClaimKeys.slotClaimKey("minecraft:overworld", "consumable", 99L, 3, true, "north");

        assertNotEquals(base, otherSlot);
        assertNotEquals(base, otherSide);
    }

    @Test
    void blankDimensionFallsBackToUnknown() {
        assertEquals(
                "unknown|consumable|pos:7",
                ConsumableClaimKeys.posClaimKey("   ", "consumable", 7L)
        );
    }
}
