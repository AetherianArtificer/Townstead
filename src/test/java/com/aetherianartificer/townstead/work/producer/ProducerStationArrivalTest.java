package com.aetherianartificer.townstead.work.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerStationArrivalTest {
    @Test
    void selectedStandHasTightArrivalTolerance() {
        assertTrue(ProducerWorkTask.isAtStationStand(0.36d));
        assertFalse(ProducerWorkTask.isAtStationStand(0.37d));
        assertFalse(ProducerWorkTask.isAtStationStand(4.0d));
    }

    @Test
    void ordinaryInteractionReachAllowsNaturalCounterPositioning() {
        assertTrue(ProducerWorkTask.isWithinStationInteractionReach(9.0d));
        assertFalse(ProducerWorkTask.isWithinStationInteractionReach(9.01d));
    }
}
