package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.recognition.SiteGeometry;

import java.util.Arrays;

/**
 * Puts known block entity positions in the order a probe of every block around the bounds would
 * reach them: bounds in sorted order, then dx, dy, dz. Positions out of reach are dropped.
 */
final class StorageCandidateOrder {
    static final int HORIZONTAL_RADIUS = 2;
    static final int VERTICAL_RADIUS = 1;
    private static final int OFFSETS = (2 * HORIZONTAL_RADIUS + 1) * (2 * VERTICAL_RADIUS + 1) * (2 * HORIZONTAL_RADIUS + 1);

    private StorageCandidateOrder() {}

    static long[] inProbeOrder(long[] sortedBounds, long[] blockEntities) {
        int kept = 0;
        long[] ranked = new long[blockEntities.length];
        long[] positions = new long[blockEntities.length];
        for (long pos : blockEntities) {
            int x = SiteGeometry.x(pos);
            int y = SiteGeometry.y(pos);
            int z = SiteGeometry.z(pos);
            long best = Long.MAX_VALUE;
            int offset = 0;
            for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                    for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++, offset++) {
                        int index = Arrays.binarySearch(sortedBounds, SiteGeometry.pack(x - dx, y - dy, z - dz));
                        if (index >= 0) best = Math.min(best, (long) index * OFFSETS + offset);
                    }
                }
            }
            if (best == Long.MAX_VALUE) continue;
            ranked[kept] = best;
            positions[kept] = pos;
            kept++;
        }
        Integer[] order = new Integer[kept];
        for (int i = 0; i < kept; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Long.compare(ranked[a], ranked[b]));
        long[] result = new long[kept];
        for (int i = 0; i < kept; i++) result[i] = positions[order[i]];
        return result;
    }
}
