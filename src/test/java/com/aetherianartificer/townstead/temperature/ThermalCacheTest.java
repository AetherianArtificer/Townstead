package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalCacheTest {
    @Test
    void adjacentRoomsKeepSeparateReadingsAndExpireAfterOneSecond() {
        ThermalCache<Object, String, Float> cache = new ThermalCache<>(20, 8);
        Object world = new Object();
        assertEquals(30f, cache.get(world, "4,64,4", 100, () -> 30f));
        assertEquals(5f, cache.get(world, "5,64,4", 100, () -> 5f));
        assertEquals(30f, cache.get(world, "4,64,4", 119, () -> 10f));
        assertEquals(10f, cache.get(world, "4,64,4", 120, () -> 10f));
    }

    @Test
    void identicalRoomIdsInAnotherSaveCannotReuseOldTemperatures() {
        ThermalCache<Object, String, Float> cache = new ThermalCache<>(200, 8);
        Object first = new Object(), second = new Object();
        assertEquals(40f, cache.get(first, "village1/room1", 100000, () -> 40f));
        assertEquals(-5f, cache.get(second, "village1/room1", 0, () -> -5f));
        cache.clear();
        assertEquals(18f, cache.get(first, "village1/room1", 100001, () -> 18f));
    }

    @Test
    void clockRollbackFailuresAndCapacityDoNotKeepStaleValues() {
        ThermalCache<Object, String, Float> cache = new ThermalCache<>(200, 2);
        Object world = new Object();
        cache.get(world, "a", 1000, () -> 40f);
        assertEquals(10f, cache.get(world, "a", 0, () -> 10f));
        assertNull(cache.get(world, "failed", 0, () -> null));
        assertEquals(20f, cache.get(world, "failed", 0, () -> 20f));
        cache.get(world, "b", 0, () -> 5f);
        assertEquals(30f, cache.get(world, "a", 1, () -> 30f));
    }
}
