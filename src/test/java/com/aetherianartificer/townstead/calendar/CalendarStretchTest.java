package com.aetherianartificer.townstead.calendar;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CalendarStretchTest {
    @Test
    void lengthsAlwaysSumToTheSeasonYear() {
        int[] gregorian = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        for (int year : new int[]{12, 48, 96, 168, 365, 400}) {
            int[] lengths = CalendarStretch.lengths(gregorian, year);
            assertEquals(year, Arrays.stream(lengths).sum(), "year " + year);
            assertTrue(Arrays.stream(lengths).allMatch(d -> d >= 1));
        }
    }

    @Test
    void proportionsSurviveTheStretch() {
        int[] lengths = CalendarStretch.lengths(new int[]{30, 30, 30, 30, 30, 30, 30, 30, 30, 30}, 96);
        assertTrue(Arrays.stream(lengths).allMatch(d -> d == 9 || d == 10));
        int[] uneven = CalendarStretch.lengths(new int[]{10, 20, 30, 40}, 100);
        assertArrayEquals(new int[]{10, 20, 30, 40}, uneven);
    }

    @Test
    void leftoverDaysGoToTheHeaviestMonths() {
        int[] lengths = CalendarStretch.lengths(new int[]{28, 31, 30}, 10);
        assertEquals(10, Arrays.stream(lengths).sum());
        assertEquals(4, lengths[1]);
    }

    @Test
    void shiftPutsTodayOnTheProvidersDay() {
        assertEquals(0, CalendarStretch.alignShift(96, 0.0, 96));
        assertEquals(24, CalendarStretch.alignShift(96, 0.25, 96));
        long today = 1000;
        long shift = CalendarStretch.alignShift(today, 0.6, 96);
        assertEquals((long) Math.floor(0.6 * 96), Math.floorMod(today + shift, 96L));
    }

    @Test
    void shiftIsStableAsDaysPass() {
        int year = 96;
        double phase = 10.0 / year;
        long shift = CalendarStretch.alignShift(500, phase, year);
        assertEquals(shift, CalendarStretch.alignShift(501, phase + 1.0 / year, year));
        assertEquals(shift, CalendarStretch.alignShift(500 + (year - 1 - 10), (year - 1.0) / year, year));
        assertEquals(shift, CalendarStretch.alignShift(500 + (year - 10), 0.0, year));
    }
}
