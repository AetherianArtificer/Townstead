package com.aetherianartificer.townstead.client.gui.career;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordArtTest {
    @Test
    void countedMetersMatchSmallTargetsAndCapLargeOnes() {
        assertEquals(8, RecordArt.meterSegments(8, 200));
        assertEquals(24, RecordArt.meterSegments(24, 200));
        assertEquals(RecordArt.MAX_METER_SEGMENTS, RecordArt.meterSegments(1_000, 200));
    }

    @Test
    void countedMetersNeverCreateMoreSegmentsThanPixels() {
        assertEquals(6, RecordArt.meterSegments(24, 6));
        assertEquals(1, RecordArt.meterSegments(0, 200));
    }
}
