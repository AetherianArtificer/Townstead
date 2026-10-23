package com.aetherianartificer.townstead.compat.jei;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JepConsolidationRuleTest {

    @Test
    void hidesOnlyUniversalAcquisitionWithoutHeldWorkstation() {
        assertTrue(JepConsolidation.isUniversalAcquisitionWithoutWorkstation(
                true, true));
        assertFalse(JepConsolidation.isUniversalAcquisitionWithoutWorkstation(
                false, true));
        assertFalse(JepConsolidation.isUniversalAcquisitionWithoutWorkstation(
                true, false));
    }
}
