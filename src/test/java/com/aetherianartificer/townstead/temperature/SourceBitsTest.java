package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceBitsTest {
    @Test void indexRoundTripsWorldCoordinatesIncludingNegatives() {
        int index = SourceBits.index(-1, 17, 34);
        assertEquals(15, index & 15);
        assertEquals(1, index >>> 8);
        assertEquals(2, index >>> 4 & 15);
    }

    @Test void setIsIdempotentAndCountTracksChanges() {
        SourceBits bits = new SourceBits();
        assertTrue(bits.set(4095, true));
        assertFalse(bits.set(4095, true));
        assertTrue(bits.set(0, true));
        assertEquals(2, bits.count());
        assertTrue(bits.set(4095, false));
        assertFalse(bits.set(4095, false));
        assertEquals(1, bits.count());
        assertTrue(bits.get(0));
        assertFalse(bits.get(4095));
    }

    @Test void visitsExactlyTheSetCellsInStorageOrder() {
        SourceBits bits = new SourceBits();
        int[] cells = {0, 63, 64, 1000, 2048, 4095};
        for (int cell : cells) bits.set(cell, true);
        List<Integer> seen = new ArrayList<>();
        bits.forEach(seen::add);
        assertEquals(List.of(0, 63, 64, 1000, 2048, 4095), seen);
    }

    @Test void fullSectionVisitsAllCells() {
        SourceBits bits = new SourceBits();
        for (int i = 0; i < 4096; i++) bits.set(i, true);
        int[] visited = {0};
        bits.forEach(index -> visited[0]++);
        assertEquals(4096, visited[0]);
        assertEquals(4096, bits.count());
    }
}
