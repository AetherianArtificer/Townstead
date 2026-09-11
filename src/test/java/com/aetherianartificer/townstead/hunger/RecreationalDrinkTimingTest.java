package com.aetherianartificer.townstead.hunger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecreationalDrinkTimingTest {
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
        assertEquals(5, sips);
        assertFalse(RecreationalDrinkTiming.sipping(-1, 32));
        assertFalse(RecreationalDrinkTiming.sipping(RecreationalDrinkTiming.HOLD_TICKS, 32));
    }
}
