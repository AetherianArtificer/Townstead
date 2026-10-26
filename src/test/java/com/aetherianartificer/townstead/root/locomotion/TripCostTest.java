package com.aetherianartificer.townstead.root.locomotion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripCostTest {

    @Test
    void glidingCostsNoStamina() {
        // A pure glider never pays for lift: 50 blocks down a 10-block drop is time plus takeoff only.
        double ticks = TripCost.estimateFlyTicks(FlightStyle.GLIDE, 50.0, 10.0);
        assertEquals(Math.sqrt(50 * 50 + 10 * 10) / FlightStyle.GLIDE.cruiseSpeed() + 40.0, ticks, 1.0E-9);
    }

    @Test
    void flappingPaysForTheSinkAndTheClimb() {
        double level = TripCost.estimateFlyTicks(FlightStyle.FLAP, 30.0, 0.0);
        double climbing = TripCost.estimateFlyTicks(FlightStyle.FLAP, 30.0, -6.0);
        assertTrue(climbing > level);
        // 30 blocks at 1:5 sinks 6, bought back at 4 ticks a block.
        assertEquals(30.0 / FlightStyle.FLAP.cruiseSpeed() + 40.0 + 4.0 * 6.0, level, 1.0E-9);
    }

    @Test
    void hoveringNeverSinks() {
        double ticks = TripCost.estimateFlyTicks(FlightStyle.HOVER, 12.0, 0.0);
        assertEquals(12.0 / FlightStyle.HOVER.cruiseSpeed() + 40.0, ticks, 1.0E-9);
    }

    @Test
    void flightMustBeClearlyQuicker() {
        assertTrue(TripCost.flightWins(70, 100));
        assertFalse(TripCost.flightWins(80, 100));
        assertTrue(TripCost.flightWins(5000, Double.POSITIVE_INFINITY));
    }
}
