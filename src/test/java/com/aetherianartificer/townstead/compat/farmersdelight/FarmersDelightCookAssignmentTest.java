package com.aetherianartificer.townstead.compat.farmersdelight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmersDelightCookAssignmentTest {

    @Test
    void kitchenTypePrefixDetection() {
        assertTrue(CookTierRules.isKitchenType("compat/farmersdelight/kitchen_l1"));
        assertTrue(CookTierRules.isKitchenType("compat/farmersdelight/kitchen_l5"));
        assertFalse(CookTierRules.isKitchenType("compat/farmersdelight/kitchen"));
        assertFalse(CookTierRules.isKitchenType("compat/othermod/kitchen_l1"));
        assertFalse(CookTierRules.isKitchenType(null));
    }

    @Test
    void tierParsingCoversHappyAndErrorPaths() {
        assertEquals(1, CookTierRules.kitchenTierFromType("compat/farmersdelight/kitchen_l1"));
        assertEquals(3, CookTierRules.kitchenTierFromType("compat/farmersdelight/kitchen_l3"));
        assertEquals(5, CookTierRules.kitchenTierFromType("compat/farmersdelight/kitchen_l5"));

        assertEquals(0, CookTierRules.kitchenTierFromType("compat/farmersdelight/kitchen_lx"));
        assertEquals(0, CookTierRules.kitchenTierFromType("compat/farmersdelight/kitchen"));
        assertEquals(0, CookTierRules.kitchenTierFromType("other:path"));
        assertEquals(0, CookTierRules.kitchenTierFromType(null));
    }

    @Test
    void slotScalingMatchesDesign() {
        assertEquals(0, CookTierRules.slotsForTier(0));
        assertEquals(1, CookTierRules.slotsForTier(1));
        assertEquals(1, CookTierRules.slotsForTier(2));
        assertEquals(2, CookTierRules.slotsForTier(3));
        assertEquals(2, CookTierRules.slotsForTier(4));
        assertEquals(3, CookTierRules.slotsForTier(5));
        assertEquals(0, CookTierRules.slotsForTier(6));
        assertEquals(0, CookTierRules.slotsForTier(-1));
    }

    @Test
    void slotsForKitchenTypeHandlesInvalidGracefully() {
        assertEquals(1, CookTierRules.slotsForKitchenType("compat/farmersdelight/kitchen_l1"));
        assertEquals(3, CookTierRules.slotsForKitchenType("compat/farmersdelight/kitchen_l5"));
        assertEquals(0, CookTierRules.slotsForKitchenType("compat/farmersdelight/kitchen_lx"));
        assertEquals(0, CookTierRules.slotsForKitchenType(null));
    }
}
