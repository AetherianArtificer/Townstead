package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitchenStorageIndexBoundsKeyTest {

    @Test
    void boundsKeyIsStableAcrossInputOrdering() {
        Set<Long> first = new LinkedHashSet<>();
        first.add(30L);
        first.add(10L);
        first.add(20L);

        Set<Long> second = new LinkedHashSet<>();
        second.add(20L);
        second.add(30L);
        second.add(10L);

        KitchenStorageIndex.BoundsKey firstKey = KitchenStorageIndex.BoundsKey.of(first);
        KitchenStorageIndex.BoundsKey secondKey = KitchenStorageIndex.BoundsKey.of(second);

        assertEquals(firstKey, secondKey);
        assertEquals(firstKey.hashCode(), secondKey.hashCode());
    }

    @Test
    void boundsKeyCanCheckContainedPositions() {
        KitchenStorageIndex.BoundsKey key = KitchenStorageIndex.BoundsKey.of(Set.of(11L, 22L, 33L));

        assertTrue(key.positionsContain(22L));
        assertFalse(key.positionsContain(44L));
    }
}
