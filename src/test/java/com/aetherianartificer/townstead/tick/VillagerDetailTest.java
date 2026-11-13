package com.aetherianartificer.townstead.tick;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerDetailTest {
    @Test
    void fullRateNearAPlayer() {
        assertEquals(1, VillagerDetail.intervalFor(0));
        assertEquals(1, VillagerDetail.intervalFor(16.0 * 16.0));
    }

    @Test
    void slowerWithDistanceUpToTheCap() {
        assertEquals(1, VillagerDetail.intervalFor(17.0 * 17.0));
        assertEquals(4, VillagerDetail.intervalFor(32.0 * 32.0));
        assertEquals(16, VillagerDetail.intervalFor(64.0 * 64.0));
        assertEquals(VillagerDetail.MAX_INTERVAL, VillagerDetail.intervalFor(200.0 * 200.0));
    }

    @Test
    void noPlayerMeansTheSlowestRate() {
        assertEquals(VillagerDetail.MAX_INTERVAL, VillagerDetail.intervalFor(Double.POSITIVE_INFINITY));
    }

    @Test
    void strideMatchesTheModuloWhenTickedEveryTick() {
        Stride stride = new Stride(7);
        for (long time = 1000; time < 1200; time++) {
            assertEquals(time % 7 == 0, stride.due(time, null), "tick " + time);
        }
    }
}
