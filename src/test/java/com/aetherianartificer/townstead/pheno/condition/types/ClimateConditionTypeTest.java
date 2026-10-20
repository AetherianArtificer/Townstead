package com.aetherianartificer.townstead.pheno.condition.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClimateConditionTypeTest {
    @Test
    void phaseRangeMatchesInsideIt() {
        assertTrue(ClimateConditionType.inPhase(0.4, 0.3, 0.5));
        assertFalse(ClimateConditionType.inPhase(0.6, 0.3, 0.5));
    }

    @Test
    void phaseRangeWrapsAcrossTheNewYear() {
        assertTrue(ClimateConditionType.inPhase(0.95, 0.9, 0.1));
        assertTrue(ClimateConditionType.inPhase(0.05, 0.9, 0.1));
        assertFalse(ClimateConditionType.inPhase(0.5, 0.9, 0.1));
    }
}
