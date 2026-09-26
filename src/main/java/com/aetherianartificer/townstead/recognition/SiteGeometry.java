package com.aetherianartificer.townstead.recognition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The cell rules behind {@link SiteRequirements}, with no world types in sight.
 *
 * <p>How far an open-air building reaches is the part that goes wrong quietly: a rule that reads
 * plausibly can still swallow a village, and the symptom surfaces layers away in something that
 * merely consumed the extent. Keeping the rules here, over packed cells, is what lets them be
 * tested directly instead of inferred from behaviour in a running world.</p>
 *
 * <p>Cells pack the way {@code BlockPos} packs them, so values pass between the two unchanged.</p>
 */
public final class SiteGeometry {
    /** Index 0 is the cell itself; 1..4 are its horizontal neighbours. */
    private static final int[] DX = {0, 0, 0, 1, -1};
    private static final int[] DZ = {0, -1, 1, 0, 0};
    /** Ground first: a seed is usually part of the ring, and the cell beside it is the inside. */
    private static final int[] ROOT_HEIGHTS = {0, -1, 1, 2};

    /** A test on one cell. Separating it from the world is the whole point of this class. */
    @FunctionalInterface
    public interface CellTest {
        boolean test(int x, int y, int z);
    }

    public enum Status {
        /** A closed ring was found. */
        FOUND,
        /** The fill escaped, so whatever surrounds this cell does not enclose it. */
        ESCAPED,
        /** The fill reached ground that is not loaded; nothing can be concluded. */
        UNLOADED
    }

    public record Region(Status status, int interior, Set<Long> cells) {
        static final Region ESCAPED_REGION = new Region(Status.ESCAPED, 0, Set.of());
        static final Region UNLOADED_REGION = new Region(Status.UNLOADED, 0, Set.of());
    }

    private SiteGeometry() {}

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public static int x(long cell) {
        return (int) (cell >> 38);
    }

    public static int y(long cell) {
        return (int) (cell << 52 >> 52);
    }

    public static int z(long cell) {
        return (int) (cell << 26 >> 38);
    }

    public static long column(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static long columnOf(long cell) {
        return column(x(cell), z(cell));
    }

    /**
     * Columns within {@code reach} of a cell. This is what binds an ingredient to a building:
     * belonging is being above or beside its ground, at any height, so a lantern counts from the
     * top of a mast while a fence across the village never does.
     */
    public static Set<Long> columnsNear(Collection<Long> cells, int reach) {
        if (cells.isEmpty()) return Set.of();
        Set<Long> columns = new HashSet<>();
        for (long cell : cells) {
            int cx = x(cell);
            int cz = z(cell);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    columns.add(column(cx + dx, cz + dz));
                }
            }
        }
        return columns;
    }

    /**
     * Standing room at or beside each seed, best candidates first. Seeds are often ring blocks
     * themselves, so neighbours are probed too, and several heights, because the height that
     * encloses is the one the ring stands at.
     */
    public static List<Long> enclosureRoots(Collection<Long> seeds, CellTest loaded, CellTest open) {
        List<Long> roots = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long seed : seeds) {
            int sx = x(seed);
            int sy = y(seed);
            int sz = z(seed);
            for (int dy : ROOT_HEIGHTS) {
                for (int i = 0; i < DX.length; i++) {
                    int px = sx + DX[i];
                    int py = sy + dy;
                    int pz = sz + DZ[i];
                    if (!seen.add(pack(px, py, pz))) continue;
                    if (!loaded.test(px, py, pz)) continue;
                    if (open.test(px, py, pz) && !open.test(px, py - 1, pz)) {
                        roots.add(pack(px, py, pz));
                    }
                }
            }
        }
        return roots;
    }

    /**
     * Horizontal fill at one level, stopping where the ring stands. Escaping {@code radius}, or
     * spreading past {@code maxInterior}, means there is no ring, so an open field never reads as
     * an enclosure. The ring itself comes back with the interior, because its fences are the
     * building's own ingredients.
     */
    public static Region enclosure(long root, CellTest loaded, CellTest open,
                                   int radius, int maxInterior) {
        int rootX = x(root);
        int rootY = y(root);
        int rootZ = z(root);
        Set<Long> interior = new HashSet<>();
        Set<Long> ring = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        interior.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            if (interior.size() > maxInterior) return Region.ESCAPED_REGION;
            long current = queue.poll();
            int cx = x(current);
            int cz = z(current);
            for (int i = 1; i < DX.length; i++) {
                int nx = cx + DX[i];
                int nz = cz + DZ[i];
                long next = pack(nx, rootY, nz);
                if (interior.contains(next) || ring.contains(next)) continue;
                if (!loaded.test(nx, rootY, nz)) return Region.UNLOADED_REGION;
                if (Math.abs(nx - rootX) > radius || Math.abs(nz - rootZ) > radius) {
                    return Region.ESCAPED_REGION;
                }
                if (open.test(nx, rootY, nz)) {
                    interior.add(next);
                    queue.add(next);
                } else {
                    ring.add(next);
                }
            }
        }
        Set<Long> all = new HashSet<>(interior);
        all.addAll(ring);
        return new Region(Status.FOUND, interior.size(), all);
    }

    /**
     * The connected run of surface cells reachable from the seeds, stepping one block up or down
     * as it goes so a deck with a step in it stays one deck. Bounded horizontally from
     * {@code originX, originZ} only; height never bounds a site.
     */
    public static Set<Long> surface(Collection<Long> seeds, CellTest surface, int seedReach,
                                    int originX, int originZ, int radius, int maxCells) {
        Set<Long> cells = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (long seed : seeds) {
            int sx = x(seed);
            int sy = y(seed);
            int sz = z(seed);
            for (int dx = -seedReach; dx <= seedReach; dx++) {
                for (int dz = -seedReach; dz <= seedReach; dz++) {
                    for (int dy = -seedReach - 1; dy <= 1; dy++) {
                        if (!surface.test(sx + dx, sy + dy, sz + dz)) continue;
                        long cell = pack(sx + dx, sy + dy, sz + dz);
                        if (cells.add(cell)) queue.add(cell);
                    }
                }
            }
        }
        long radiusSquared = (long) radius * radius;
        while (!queue.isEmpty() && cells.size() < maxCells) {
            long current = queue.poll();
            int cx = x(current);
            int cy = y(current);
            int cz = z(current);
            for (int i = 1; i < DX.length; i++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = cx + DX[i];
                    int ny = cy + dy;
                    int nz = cz + DZ[i];
                    long dxr = nx - originX;
                    long dzr = nz - originZ;
                    if (dxr * dxr + dzr * dzr > radiusSquared) continue;
                    long next = pack(nx, ny, nz);
                    if (cells.contains(next) || !surface.test(nx, ny, nz)) continue;
                    cells.add(next);
                    queue.add(next);
                }
            }
        }
        return cells;
    }
}
