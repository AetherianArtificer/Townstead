package com.aetherianartificer.townstead.hunger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecreationalDrinkTimingTest {
    @Test
    void randomizedSipsStaySafeAndDifferBetweenGuests() {
        var patterns = new java.util.HashSet<String>();
        for (int seed : new int[]{0, 1, 7254, -49371, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            StringBuilder pattern = new StringBuilder();
            for (int duration : new int[]{1, 2, 8, 16, 32, 64}) {
                int consecutive = 0;
                for (int tick = 0; tick < RecreationalDrinkTiming.HOLD_TICKS; tick++) {
                    boolean sip = RecreationalDrinkTiming.sipping(tick, duration, seed);
                    consecutive = sip ? consecutive + 1 : 0;
                    assertTrue(consecutive < duration);
                    if (duration == 32) pattern.append(sip ? '1' : '0');
                }
            }
            patterns.add(pattern.toString());
        }
        assertTrue(patterns.size() >= 5, "guests should not all raise their drinks together");
    }

    @Test
    void gesturesNeverFinishEvenShortUseItems() {
        for (int duration : new int[]{1, 2, 8, 16, 32, 64}) {
            int consecutive = 0;
            for (int tick = 0; tick < RecreationalDrinkTiming.HOLD_TICKS; tick++) {
                consecutive = RecreationalDrinkTiming.sipping(tick, duration) ? consecutive + 1 : 0;
                assertTrue(consecutive < duration, "intermediate sip must not consume the item");
            }
        }
    }

    @Test
    void holdsTheSameServingBetweenRepeatedSipsUntilFinalConsumption() {
        int sips = 0;
        boolean previous = false;
        for (int tick = 0; tick < RecreationalDrinkTiming.HOLD_TICKS; tick++) {
            boolean current = RecreationalDrinkTiming.sipping(tick, 32);
            if (current && !previous) sips++;
            previous = current;
        }
        assertEquals(4, sips, "one serving should last through long pauses, not constant drinking");
        assertFalse(RecreationalDrinkTiming.sipping(-1, 32));
        assertFalse(RecreationalDrinkTiming.sipping(RecreationalDrinkTiming.HOLD_TICKS, 32));
    }
}
