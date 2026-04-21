package com.aetherianartificer.townstead.dock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects a dock/wharf structure around a given position. Results cached per
 * scan origin with a short TTL. Callers include the fisherman AI (scans from
 * its barrel) and the player's building-report flow (scans from the player's
 * feet) — both just hand in a position; the dock itself doesn't require any
 * particular block at the origin.
 *
 * Tier ladder (each tier fully includes the prior — pure structure, no
 * fisherman-specific requirements):
 *
 *   Tier 1 — Landing
 *     - 9+ horizontally connected {@code #minecraft:planks} at the same Y-level
 *     - 6+ of those planks horizontally adjacent to a water source block
 *     - 3+ planks with a water source directly below (the deck actually
 *       extends over water, not a shoreline patio next to it)
 *     - Perimeter planks mostly unwalled: a solid block above a perimeter
 *       plank is a house wall, so at most 30% may be walled. Decisive filter
 *       for plank houses (walls everywhere) vs decks (railings, not walls).
 *     - 70%+ of planks have open sky (no solid roof within 6 above), so
 *       boat-house-style fully covered structures don't register as docks
 *
 *   Tier 2 — Pier
 *     - 24+ planks
 *     - 3+ light sources inside the bounds (lanterns or torches)
 *     - 6+ railing blocks inside the bounds ({@code #minecraft:fences} or
 *       {@code #minecraft:walls})
 *
 *   Tier 3 — Wharf
 *     - 48+ planks
 *     - 6+ light sources
 *     - 12+ railing blocks
 *
 * Tier thresholds mirror the dock_l2 / dock_l3 JSON block recipes exactly so
 * the Add / Refresh / Upgrade paths (which consult DockScanner and the JSON
 * separately) agree on the tier a given structure qualifies for.
 *
 * Bounds used for scanning are the plank component's axis-aligned box expanded
 * by 1 block horizontally and 2 blocks vertically, so decorations sitting just
 * next to or above the deck count.
 */
public final class DockScanner {
    private static final int HORIZONTAL_RADIUS = 12;
    private static final int VERTICAL_RADIUS = 4;

    private static final int T1_MIN_PLANKS = 9;
    private static final int T1_MIN_WATER_TOUCH = 6;
    // Minimum fraction of deck planks that must have open sky overhead.
    // 0.7 means a cabin with a small attached porch (majority roofed) still
    // reads as a cabin, not a dock. A real pier should be mostly open-top.
    private static final float T1_MIN_OPEN_SKY_RATIO = 0.7f;
    private static final int ROOF_SCAN_HEIGHT = 6;
    // A real dock extends OVER water. Require multiple planks with a water
    // source directly below, not just one — a shoreline house with a single
    // plank hanging over the edge shouldn't qualify.
    private static final int T1_MIN_PLANKS_OVER_WATER = 3;
    // The decisive house-vs-dock filter: on a house the perimeter planks have
    // walls directly above (the outside walls sitting on the floor). A dock
    // has at most a few fence/wall railings, which aren't counted as walls.
    // Reject if more than this fraction of perimeter planks have a wall above.
    private static final float T1_MAX_WALLED_PERIMETER_RATIO = 0.30f;

    // Tier thresholds — deliberately mirror the dock_l2 / dock_l3 JSON recipes
    // block-for-block. Keeping DockScanner and the JSON requirements in lockstep
    // means Add Building, Refresh, and Upgrade Building all reach the same
    // conclusion about which tier the dock qualifies for.
    private static final int T2_MIN_PLANKS = 24;
    private static final int T2_MIN_LIGHTS = 3;
    private static final int T2_MIN_RAILING = 6;

    private static final int T3_MIN_PLANKS = 48;
    private static final int T3_MIN_LIGHTS = 6;
    private static final int T3_MIN_RAILING = 12;

    private static final long CACHE_TTL_TICKS = 200L;
    private static final long EMPTY_CACHE_TTL_TICKS = 400L;

    private static final Map<Key, Entry> CACHE = new ConcurrentHashMap<>();

    private DockScanner() {}

    public static @Nullable Dock scan(ServerLevel level, BlockPos near) {
        return scan(level, near, HORIZONTAL_RADIUS);
    }

    /**
     * Scan variant with an override for how far out from {@code near} to
     * search. The default radius ({@value #HORIZONTAL_RADIUS}) is tuned for
     * fisherman-barrel scans, which sit close to the dock by construction.
     * Player-triggered scans (report-building, refresh) can fire from anywhere
     * on a large deck, so those callers pass a larger radius to guarantee
     * the full plank component gets captured — otherwise scanning from an
     * edge would yield a partial dock and incorrectly downgrade the tier.
     */
    public static @Nullable Dock scan(ServerLevel level, BlockPos near, int horizontalRadius) {
        if (near == null) return null;
        // Cache key includes the radius so mixed callers don't step on each
        // other's TTL windows.
        Key key = new Key(level.dimension().location().toString(), near.asLong(), horizontalRadius);
        long now = level.getGameTime();
        Entry cached = CACHE.get(key);
        if (cached != null && now <= cached.expiresAt) {
            return cached.dock;
        }
        Dock dock = scanUncached(level, near, horizontalRadius);
        long ttl = dock == null ? EMPTY_CACHE_TTL_TICKS : CACHE_TTL_TICKS;
        CACHE.put(key, new Entry(dock, now + ttl));
        return dock;
    }

    public static void invalidate(ServerLevel level, BlockPos near) {
        if (near == null) return;
        String dim = level.dimension().location().toString();
        long posKey = near.asLong();
        CACHE.keySet().removeIf(k -> k.dim.equals(dim) && k.posKey == posKey);
    }

    private static @Nullable Dock scanUncached(ServerLevel level, BlockPos near, int horizontalRadius) {
        Set<Long> planks = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(
                near.offset(-horizontalRadius, -VERTICAL_RADIUS, -horizontalRadius),
                near.offset(horizontalRadius, VERTICAL_RADIUS, horizontalRadius))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.PLANKS)) {
                planks.add(p.asLong());
            }
        }
        if (planks.size() < T1_MIN_PLANKS) return null;

        Set<Long> best = largestHorizontalComponent(planks);
        if (best.size() < T1_MIN_PLANKS) return null;
        if (!meetsWaterTouch(level, best, T1_MIN_WATER_TOUCH)) return null;
        if (!hasLowWalledPerimeter(level, best, T1_MAX_WALLED_PERIMETER_RATIO)) return null;
        if (!meetsOpenSkyRatio(level, best, T1_MIN_OPEN_SKY_RATIO)) return null;
        if (countPlanksOverWater(level, best) < T1_MIN_PLANKS_OVER_WATER) return null;

        BoundingBox bounds = computeBounds(best);

        int tier = 1;
        if (best.size() >= T2_MIN_PLANKS && pierQualifies(level, best, bounds)) {
            tier = 2;
            if (best.size() >= T3_MIN_PLANKS && wharfQualifies(level, best, bounds)) {
                tier = 3;
            }
        }

        return new Dock(bounds, best.size(), tier);
    }

    // ── Structural scan ──

    private static Set<Long> largestHorizontalComponent(Set<Long> planks) {
        Set<Long> visited = new HashSet<>();
        Set<Long> best = Collections.emptySet();
        for (long seedKey : planks) {
            if (visited.contains(seedKey)) continue;
            Set<Long> component = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            BlockPos seed = BlockPos.of(seedKey);
            queue.add(seed);
            component.add(seedKey);
            visited.add(seedKey);
            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos n = cur.relative(d);
                    long nkey = n.asLong();
                    if (!planks.contains(nkey) || component.contains(nkey)) continue;
                    component.add(nkey);
                    visited.add(nkey);
                    queue.add(n);
                }
            }
            if (component.size() > best.size()) best = component;
        }
        return best;
    }

    /**
     * Roof rejection. For each plank, look up to {@link #ROOF_SCAN_HEIGHT}
     * blocks above for anything that reads as a ceiling (planks, logs, stone,
     * slabs, stairs — basically any block with collision that isn't explicit
     * pier furniture). Fences, walls, lanterns, torches and leaves don't
     * count as roof, so a post with a hanging lantern over a plank is fine.
     * A dock qualifies when at least {@code minRatio} of its planks have
     * clear sky by this definition.
     */
    private static boolean meetsOpenSkyRatio(ServerLevel level, Set<Long> deck, float minRatio) {
        if (deck.isEmpty()) return false;
        int openCount = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            boolean roofed = false;
            for (int i = 1; i <= ROOF_SCAN_HEIGHT; i++) {
                BlockPos above = p.above(i);
                if (isRoofBlock(level.getBlockState(above), level, above)) {
                    roofed = true;
                    break;
                }
            }
            if (!roofed) openCount++;
        }
        return (float) openCount / deck.size() >= minRatio;
    }

    private static boolean isRoofBlock(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.getFluidState().isSource()) return false;
        if (state.is(BlockTags.LEAVES)) return false;
        if (state.is(BlockTags.FENCES)) return false;
        if (state.is(BlockTags.WALLS)) return false;
        if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) return false;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) return false;
        // Anything else with a non-empty collision shape is treated as a
        // ceiling — covers full blocks, slabs, stairs, half-blocks, etc.
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The decisive house-vs-dock test. A perimeter plank is one with at least
     * one horizontal neighbor that isn't itself in the plank component — the
     * outer ring of the deck footprint. On a house, each perimeter plank has
     * an outside wall sitting directly on it ({@link #isRoofBlock} returns
     * true for solid structural blocks, excluding fence/wall railings and
     * pier furniture). On a dock, perimeter planks have open air above, or
     * at most the occasional fence post / lantern — none of which count.
     *
     * Accept the component as a dock only when the fraction of perimeter
     * planks with a wall block directly above stays below {@code maxRatio}.
     */
    private static boolean hasLowWalledPerimeter(ServerLevel level, Set<Long> deck, float maxRatio) {
        int perimeterCount = 0;
        int walledCount = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            boolean onPerimeter = false;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (!deck.contains(p.relative(d).asLong())) {
                    onPerimeter = true;
                    break;
                }
            }
            if (!onPerimeter) continue;
            perimeterCount++;
            BlockPos above = p.above();
            if (isRoofBlock(level.getBlockState(above), level, above)) {
                walledCount++;
            }
        }
        if (perimeterCount == 0) return true;
        return (float) walledCount / perimeterCount <= maxRatio;
    }

    /**
     * Count planks with a water source block directly below. These are the
     * "over the water" parts of the dock. A pier on a shoreline has its
     * inner-edge planks on dirt and its water-facing planks over water, so
     * at least one will qualify. Pure land-side plank patches (a house, a
     * beach patio) have dirt/sand below all their planks — never qualify.
     */
    private static int countPlanksOverWater(ServerLevel level, Set<Long> deck) {
        int count = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            BlockState below = level.getBlockState(p.below());
            if (below.getFluidState().isSource() && below.getFluidState().is(FluidTags.WATER)) {
                count++;
            }
        }
        return count;
    }

    private static boolean meetsWaterTouch(ServerLevel level, Set<Long> deck, int required) {
        int touching = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                BlockState ns = level.getBlockState(n);
                if (ns.getFluidState().isSource() && ns.getFluidState().is(FluidTags.WATER)) {
                    touching++;
                    break;
                }
            }
            if (touching >= required) return true;
        }
        return false;
    }

    private static BoundingBox computeBounds(Set<Long> deck) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        return new BoundingBox(
                minX - 1, minY - 1, minZ - 1,
                maxX + 1, maxY + 2, maxZ + 1);
    }

    // ── Pier / Wharf evaluators ──

    private static boolean pierQualifies(ServerLevel level, Set<Long> deck, BoundingBox bounds) {
        return countLights(level, bounds) >= T2_MIN_LIGHTS
                && countRailings(level, bounds) >= T2_MIN_RAILING;
    }

    private static boolean wharfQualifies(ServerLevel level, Set<Long> deck, BoundingBox bounds) {
        return countLights(level, bounds) >= T3_MIN_LIGHTS
                && countRailings(level, bounds) >= T3_MIN_RAILING;
    }

    private static int countLights(ServerLevel level, BoundingBox bb) {
        int count = 0;
        for (BlockPos p : boundsPositions(bb)) {
            if (isLight(level.getBlockState(p))) count++;
        }
        return count;
    }

    /**
     * What counts as a dock light. Campfires were excluded — a firepit on a
     * wooden pier reads as a fire hazard rather than wayfinding. Lanterns and
     * torches fit the maritime vocabulary: posts with lanterns, wall torches
     * hung off railings.
     */
    private static boolean isLight(BlockState s) {
        return s.is(Blocks.LANTERN) || s.is(Blocks.SOUL_LANTERN)
                || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)
                || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH);
    }

    private static int countRailings(ServerLevel level, BoundingBox bb) {
        int count = 0;
        for (BlockPos p : boundsPositions(bb)) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)) count++;
        }
        return count;
    }

    private static Iterable<BlockPos> boundsPositions(BoundingBox bb) {
        return BlockPos.betweenClosed(
                new BlockPos(bb.minX(), bb.minY(), bb.minZ()),
                new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()));
    }

    private record Key(String dim, long posKey, int radius) {}
    private record Entry(@Nullable Dock dock, long expiresAt) {}
}
