package com.aetherianartificer.townstead.calendar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeasonClockTest {
    @Test
    void phaseStartsAtSpringAndWrapsEachYear() {
        assertEquals(0.0, SeasonClock.cyclePhase(0, 0, 96), 1e-9);
        assertEquals(0.0, SeasonClock.cyclePhase(96, 0, 96), 1e-9);
        assertEquals(3.5 / 96, SeasonClock.cyclePhase(3, 0.5, 96), 1e-9);
    }

    @Test
    void daysBeforeTheAnchorCountBackFromTheEndOfTheYear() {
        assertEquals(95.0 / 96, SeasonClock.cyclePhase(-1, 0, 96), 1e-9);
    }

    @Test
    void eclipticSolsticesLandOnTheSharedConvention() {
        int termDays = 7, year = 24 * termDays;
        assertEquals(0.375, SeasonClock.cyclePhase(9L * termDays, 0, year), 1e-9);
        assertEquals(0.875, SeasonClock.cyclePhase(21L * termDays, 0, year), 1e-9);
    }

    @Test
    void emptyYearIsSafe() {
        assertEquals(0.0, SeasonClock.cyclePhase(5, 0.5, 0), 1e-9);
    }
}
