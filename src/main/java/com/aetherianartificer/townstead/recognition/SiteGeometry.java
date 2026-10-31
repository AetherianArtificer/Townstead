package com.aetherianartificer.townstead.recognition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

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

    /** A test on one cell. Separating it from the world is the whole point of this class. */
    @FunctionalInterface
    public interface CellTest {
        boolean test(int x, int y, int z);
    }

    /** What the deck rules read about one cell. */
    public interface DeckView {
        boolean loaded(int x, int y, int z);

        /** Has a collision shape. */
        boolean solid(int x, int y, int z);

        /** Collides as a full cube. */
        boolean full(int x, int y, int z);

        /** Natural ground that is never a deck: ice, leaves, lily pads. */
        boolean natural(int x, int y, int z);

        /** Holds the liquid the deck must stand over, as a block or waterlogged. */
        boolean liquid(int x, int y, int z);

        /** Holds any fluid at all. */
        boolean fluid(int x, int y, int z);
    }

    private SiteGeometry() {}

    /** How much of the smaller footprint the other one covers too, from 0 to 1. */
    public static double overlapShare(Set<Long> first, Set<Long> second) {
        if (first.isEmpty() || second.isEmpty()) return 0;
        Set<Long> small = first.size() <= second.size() ? first : second;
        Set<Long> large = small == first ? second : first;
        int shared = 0;
        for (long cell : small) {
            if (large.contains(cell)) shared++;
        }
        return (double) shared / small.size();
    }

    /**
     * Keeps the best of every group of overlapping candidates: walk them best first, and keep one
     * unless it overlaps one already kept. The order decides, never which kind was tried first.
     */
    public static <T> List<T> select(List<T> ranked, BiPredicate<T, T> overlaps) {
        List<T> kept = new ArrayList<>();
        for (T candidate : ranked) {
            boolean clear = true;
            for (T better : kept) {
                if (overlaps.test(better, candidate)) {
                    clear = false;
                    break;
                }
            }
            if (clear) kept.add(candidate);
        }
        return kept;
    }

    /**
     * The patch of the type's own blocks the seeds stand in: farmland of one field, say. Members
     * within {@code link} blocks of each other horizontally, and a step of height, are one patch,
     * so the water channel between two rows does not split a field. Bounded from the origin.
     */
    public static Set<Long> patch(Collection<Long> seeds, CellTest member, int seedReach, int link,
                                  int originX, int originZ, int radius, int maxCells) {
        Set<Long> cells = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (long seed : seeds) {
            int sx = x(seed), sy = y(seed), sz = z(seed);
            for (int dx = -seedReach; dx <= seedReach; dx++) {
                for (int dz = -seedReach; dz <= seedReach; dz++) {
                    for (int dy = -seedReach - 1; dy <= 1; dy++) {
                        if (!member.test(sx + dx, sy + dy, sz + dz)) continue;
                        long cell = pack(sx + dx, sy + dy, sz + dz);
                        if (cells.add(cell)) queue.add(cell);
                    }
                }
            }
        }
        long radiusSquared = (long) radius * radius;
        while (!queue.isEmpty() && cells.size() < maxCells) {
            long current = queue.poll();
            int cx = x(current), cy = y(current), cz = z(current);
            for (int dx = -link; dx <= link; dx++) {
                for (int dz = -link; dz <= link; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dz == 0) continue;
                        int nx = cx + dx, ny = cy + dy, nz = cz + dz;
                        long dxr = nx - originX, dzr = nz - originZ;
                        if (dxr * dxr + dzr * dzr > radiusSquared) continue;
                        long next = pack(nx, ny, nz);
                        if (cells.contains(next) || !member.test(nx, ny, nz)) continue;
                        cells.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return cells;
    }

    /**
     * A solid block a villager could stand on, with the liquid beneath it and only open space
     * between: a deck over the water. Ground with a cave pool somewhere under it is not one.
     */
    public static boolean isDeck(DeckView view, int x, int y, int z, int maxDrop) {
        if (!view.loaded(x, y, z) || !view.solid(x, y, z) || view.natural(x, y, z)) return false;
        if (view.full(x, y + 1, z)) return false;
        for (int dy = 1; dy <= maxDrop; dy++) {
            if (view.liquid(x, y - dy, z)) return true;
            if (view.solid(x, y - dy, z)) return false;
        }
        return false;
    }

    /**
     * Somewhere along its edge the deck meets the liquid open to the air: water a boat could sit
     * on, not a pool sealed under a floor.
     */
    public static boolean bordersOpenLiquid(DeckView view, Set<Long> cells, int maxDrop) {
        for (long cell : cells) {
            int x = x(cell), y = y(cell), z = z(cell);
            for (int i = 1; i < DX.length; i++) {
                int nx = x + DX[i], nz = z + DZ[i];
                if (cells.contains(pack(nx, y, nz))) continue;
                for (int dy = 0; dy <= maxDrop; dy++) {
                    int ny = y - dy;
                    if (!view.loaded(nx, ny, nz)) break;
                    if (view.liquid(nx, ny, nz)) {
                        if (!view.fluid(nx, ny + 1, nz) && !view.solid(nx, ny + 1, nz)) return true;
                        break;
                    }
                    if (view.solid(nx, ny, nz)) break;
                }
            }
        }
        return false;
    }

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
