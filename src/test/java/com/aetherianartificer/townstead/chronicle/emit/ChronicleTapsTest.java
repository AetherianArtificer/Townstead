package com.aetherianartificer.townstead.chronicle.emit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChronicleTapsTest {
    @Test
    void batchCompletionCountsEveryProducedDish() {
        assertEquals(16, ChronicleTaps.counterAmount(Map.of("amount", "16")));
    }

    @Test
    void malformedOrMissingAmountStillCountsOneCompletion() {
        assertEquals(1, ChronicleTaps.counterAmount(Map.of()));
        assertEquals(1, ChronicleTaps.counterAmount(Map.of("amount", "many")));
        assertEquals(1, ChronicleTaps.counterAmount(Map.of("amount", "0")));
    }
}
