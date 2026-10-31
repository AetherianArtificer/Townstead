package com.aetherianartificer.townstead.culture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a village's wall runs, worked out on the ground plan alone: a convex outline around every
 * building and street, pushed out by a margin; the cells of that outline, joined edge to edge so the
 * wall has no diagonal gaps; gates where the main streets leave the centre; and the outline's
 * corners. Coordinates are block x and z.
 */
public final class EnclosurePlan {
    /** A footprint on the ground, inclusive. */
    public record Box(int minX, int minZ, int maxX, int maxZ) {
        int centreX() { return (minX + maxX) / 2; }
        int centreZ() { return (minZ + maxZ) / 2; }
    }

    /** One gate: its cells, and whether you pass through it along z (else along x). */
    public record Gate(List<long[]> cells, boolean alongZ) {}

    public record Plan(List<long[]> wall, List<Gate> gates, List<long[]> corners) {
        public boolean isGate(long x, long z) {
            for (Gate gate : gates) for (long[] cell : gate.cells()) if (cell[0] == x && cell[1] == z) return true;
            return false;
        }
    }

    private EnclosurePlan() {}

    /**
     * @param pieces  every piece's footprint; the first is the village centre
     * @param streets the footprints of street pieces, which decide where gates go
     */
    public static Plan plan(List<Box> pieces, List<Box> streets, int margin, int gates, int gateWidth) {
        List<long[]> points = new ArrayList<>();
        for (Box box : pieces) {
            points.add(new long[]{box.minX() - margin, box.minZ() - margin});
            points.add(new long[]{box.maxX() + margin, box.minZ() - margin});
            points.add(new long[]{box.minX() - margin, box.maxZ() + margin});
            points.add(new long[]{box.maxX() + margin, box.maxZ() + margin});
        }
        List<long[]> hull = hull(points);
        Set<Long> seen = new LinkedHashSet<>();
        List<long[]> wall = new ArrayList<>();
        for (int i = 0; i < hull.size(); i++) {
            long[] a = hull.get(i), b = hull.get((i + 1) % hull.size());
            for (long[] cell : line(a[0], a[1], b[0], b[1])) {
                if (seen.add(key(cell[0], cell[1]))) wall.add(cell);
            }
        }
        Box centre = pieces.get(0);
        List<Gate> chosen = gates(seen, centre, streets, gates, gateWidth);
        return new Plan(wall, chosen, corners(hull));
    }

    /** Gates on the sides the most street runs toward, ties broken north, east, south, west. */
    static List<Gate> gates(Set<Long> wall, Box centre, List<Box> streets, int count, int width) {
        int[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        Map<int[], Integer> reach = new LinkedHashMap<>();
        for (int[] d : directions) {
            int score = 0;
            for (Box street : streets) {
                boolean ahead = d[0] != 0
                        ? (d[0] > 0 ? street.minX() > centre.maxX() : street.maxX() < centre.minX())
                          && street.maxZ() >= centre.minZ() && street.minZ() <= centre.maxZ()
                        : (d[1] > 0 ? street.minZ() > centre.maxZ() : street.maxZ() < centre.minZ())
                          && street.maxX() >= centre.minX() && street.minX() <= centre.maxX();
                if (ahead) score++;
            }
            reach.put(d, score);
        }
        List<int[]> order = new ArrayList<>(reach.keySet());
        order.sort(Comparator.comparingInt((int[] d) -> -reach.get(d)));
        List<Gate> out = new ArrayList<>();
        for (int[] d : order) {
            if (out.size() >= count) break;
            long x = centre.centreX(), z = centre.centreZ();
            for (int step = 0; step < 512 && !wall.contains(key(x, z)); step++) { x += d[0]; z += d[1]; }
            if (!wall.contains(key(x, z))) continue;
            List<long[]> cells = new ArrayList<>();
            int half = width / 2;
            for (int offset = -half; offset <= width - 1 - half; offset++) {
                long cx = d[0] != 0 ? x : x + offset, cz = d[0] != 0 ? z + offset : z;
                // The opening follows the wall where it slopes, a cell either side of the straight line.
                for (long nudge : new long[]{0, -1, 1}) {
                    long nx = cx + (d[0] != 0 ? nudge : 0), nz = cz + (d[0] != 0 ? 0 : nudge);
                    if (wall.contains(key(nx, nz))) { cells.add(new long[]{nx, nz}); break; }
                }
            }
            out.add(new Gate(cells, d[1] != 0));
        }
        return out;
    }

    /** The outline's four outermost corners, or all of them when it has four or fewer. */
    static List<long[]> corners(List<long[]> hull) {
        if (hull.size() <= 4) return hull;
        Set<long[]> picked = new LinkedHashSet<>();
        picked.add(hull.stream().min(Comparator.comparingLong(p -> p[0] + p[1])).orElseThrow());
        picked.add(hull.stream().max(Comparator.comparingLong(p -> p[0] - p[1])).orElseThrow());
        picked.add(hull.stream().max(Comparator.comparingLong(p -> p[0] + p[1])).orElseThrow());
        picked.add(hull.stream().min(Comparator.comparingLong(p -> p[0] - p[1])).orElseThrow());
        return List.copyOf(picked);
    }

    /** Convex hull, counter-clockwise, by Andrew's monotone chain. */
    static List<long[]> hull(List<long[]> points) {
        List<long[]> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.<long[]>comparingLong(p -> p[0]).thenComparingLong(p -> p[1]));
        List<long[]> out = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int base = out.size();
            for (long[] p : sorted) {
                while (out.size() >= base + 2 && cross(out.get(out.size() - 2), out.get(out.size() - 1), p) <= 0) {
                    out.remove(out.size() - 1);
                }
                out.add(p);
            }
            out.remove(out.size() - 1);
            java.util.Collections.reverse(sorted);
        }
        return out;
    }

    private static long cross(long[] o, long[] a, long[] b) {
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
    }

    /** The cells from one point to another, each sharing an edge with the last. */
    static List<long[]> line(long x0, long z0, long x1, long z1) {
        List<long[]> out = new ArrayList<>();
        long dx = Math.abs(x1 - x0), dz = Math.abs(z1 - z0);
        long sx = x0 < x1 ? 1 : -1, sz = z0 < z1 ? 1 : -1;
        long x = x0, z = z0;
        out.add(new long[]{x, z});
        // One axis per step, whichever keeps nearer the true line: a diagonal step would leave a
        // gap between two wall posts.
        for (long ix = 0, iz = 0; ix < dx || iz < dz; ) {
            if (iz >= dz || (ix < dx && (1 + 2 * ix) * dz < (1 + 2 * iz) * dx)) { x += sx; ix++; }
            else { z += sz; iz++; }
            out.add(new long[]{x, z});
        }
        return out;
    }

    static long key(long x, long z) {
        return (x << 32) ^ (z & 0xffffffffL);
    }
}
