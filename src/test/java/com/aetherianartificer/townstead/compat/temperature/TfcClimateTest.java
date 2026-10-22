package com.aetherianartificer.townstead.compat.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TfcClimateTest {
    private static final long DAY = 24000L;

    @Test
    void phaseZeroIsTheFirstDayOfSpringAtTheVanillaHour() {
        int month = 8, spring = 2;
        long year = 12L * month * DAY;
        long now = 5 * year + 40 * DAY + 123;
        long tick = TfcClimate.tickAt(now, month, spring, 0.0, 6000);
        assertEquals(5 * year + spring * month * DAY + 12000, tick, "phase 0 at vanilla noon is 12:00 on the calendar");
    }

    @Test
    void phaseWalksTheYearByDays() {
        int month = 8, spring = 2;
        long year = 12L * month * DAY;
        long now = 3 * year + spring * month * DAY;
        long start = TfcClimate.tickAt(now, month, spring, 0.0, 18000);
        assertEquals(start % DAY, 0, "vanilla midnight is calendar hour 0");
        assertEquals(start + 48 * DAY, TfcClimate.tickAt(now, month, spring, 0.5, 18000));
        assertEquals(start + 95 * DAY, TfcClimate.tickAt(now, month, spring, 0.999, 18000));
    }

    @Test
    void yearStartsAtTheLatestSpringBeforeNow() {
        int month = 8, spring = 2;
        long year = 12L * month * DAY;
        long beforeSpring = 4 * year + DAY;
        long tick = TfcClimate.tickAt(beforeSpring, month, spring, 0.0, 18000);
        assertEquals(3 * year + spring * month * DAY, tick);
    }

    @Test
    void dayMeanSamplesMidnightAndNoonOfTheSameDay() {
        long[] ticks = TfcClimate.dayMeanTicks(7 * DAY + 15000);
        assertArrayEquals(new long[]{7 * DAY, 7 * DAY + 12000}, ticks);
    }
}
