package com.aetherianartificer.townstead.temperature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ThermalPathPolicyTest {
    @Test void mcasExistingWaterCostDoesNotNeutralizeColdAvoidance() {
        assertEquals(-1, ThermalPathPolicy.waterCost(16, true, false));
        assertTrue(ThermalPathPolicy.avoidColdWater(12, 1, false, false));
    }
    @Test void aVillagerInColdWaterCanStillEscape() {
        assertEquals(32, ThermalPathPolicy.waterCost(16, true, true));
    }
    @Test void warmingRestoresNativePolicyAndOtherImpassablePoliciesRemain() {
        assertEquals(16, ThermalPathPolicy.waterCost(16, false, false));
        assertEquals(-1, ThermalPathPolicy.waterCost(-1, true, true));
    }
    @Test void actualClimateAndBiologyMatterMoreThanCalendarLabels() {
        assertFalse(ThermalPathPolicy.avoidColdWater(25, 1, false, false));
        assertFalse(ThermalPathPolicy.avoidColdWater(5, 0, false, false));
        assertFalse(ThermalPathPolicy.avoidColdWater(5, 1, true, false));
        assertFalse(ThermalPathPolicy.avoidColdWater(5, 1, false, true));
    }
}
