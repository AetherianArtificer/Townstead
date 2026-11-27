package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.recognition.SiteGeometry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading block entities from chunks must find the same candidates, in the same order, as probing
 * every block around the bounds did.
 */
class StorageCandidateOrderTest {
    private static final int H = StorageCandidateOrder.HORIZONTAL_RADIUS;
    private static final int V = StorageCandidateOrder.VERTICAL_RADIUS;

    @Test
    void matchesTheProbeOnRandomRooms() {
        Random random = new Random(7);
        int nonEmpty = 0;
        for (int round = 0; round < 400; round++) {
            long[] bounds = randomBounds(random);
            long[] blockEntities = randomBlockEntities(random, bounds);
            long[] expected = probe(bounds, Set.of(box(blockEntities)));
            long[] actual = StorageCandidateOrder.inProbeOrder(bounds, blockEntities);
            assertArrayEquals(expected, actual, "round " + round);
            if (expected.length > 0) nonEmpty++;
        }
        assertTrue(nonEmpty > 300, "too few rounds had candidates: " + nonEmpty);
    }

    @Test
    void dropsBlockEntitiesOutOfReach() {
        long[] bounds = {SiteGeometry.pack(0, 64, 0)};
        long[] blockEntities = {
                SiteGeometry.pack(2, 65, -2),
                SiteGeometry.pack(3, 64, 0),
                SiteGeometry.pack(0, 66, 0),
                SiteGeometry.pack(-2, 63, 2)};
        long[] expected = {SiteGeometry.pack(-2, 63, 2), SiteGeometry.pack(2, 65, -2)};
        assertArrayEquals(expected, StorageCandidateOrder.inProbeOrder(bounds, blockEntities));
    }

    /** The old scan: sorted bounds, then dx, dy, dz, first sighting wins. */
    private static long[] probe(long[] sortedBounds, Set<Long> blockEntities) {
        Set<Long> seen = new HashSet<>();
        List<Long> found = new ArrayList<>();
        for (long key : sortedBounds) {
            int x = SiteGeometry.x(key), y = SiteGeometry.y(key), z = SiteGeometry.z(key);
            for (int dx = -H; dx <= H; dx++) {
                for (int dy = -V; dy <= V; dy++) {
                    for (int dz = -H; dz <= H; dz++) {
                        long pos = SiteGeometry.pack(x + dx, y + dy, z + dz);
                        if (!seen.add(pos)) continue;
                        if (blockEntities.contains(pos)) found.add(pos);
                    }
                }
            }
        }
        return found.stream().mapToLong(Long::longValue).toArray();
    }

    private static long[] randomBounds(Random random) {
        Set<Long> cells = new HashSet<>();
        int count = 1 + random.nextInt(120);
        int baseY = random.nextInt(2) == 0 ? 64 : -60;
        for (int i = 0; i < count; i++) {
            cells.add(SiteGeometry.pack(random.nextInt(25) - 12, baseY + random.nextInt(3), random.nextInt(25) - 12));
        }
        long[] sorted = cells.stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(sorted);
        return sorted;
    }

    private static long[] randomBlockEntities(Random random, long[] bounds) {
        Set<Long> positions = new HashSet<>();
        int count = random.nextInt(40);
        int baseY = SiteGeometry.y(bounds[0]);
        for (int i = 0; i < count; i++) {
            positions.add(SiteGeometry.pack(random.nextInt(35) - 17, baseY - 3 + random.nextInt(8), random.nextInt(35) - 17));
        }
        return positions.stream().mapToLong(Long::longValue).toArray();
    }

    private static Long[] box(long[] values) {
        Long[] boxed = new Long[values.length];
        for (int i = 0; i < values.length; i++) boxed[i] = values[i];
        return boxed;
    }
}
