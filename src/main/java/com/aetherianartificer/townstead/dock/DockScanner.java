package com.aetherianartificer.townstead.dock;

import com.aetherianartificer.townstead.recognition.RecognitionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
 *
 *   Tier 2 — Pier
 *     - 24+ planks
 *     - 3+ light sources inside the bounds (lanterns or torches)
 *     - 6+ railing blocks inside the bounds ({@code #minecraft:fences} or
 *       {@code #minecraft:walls})
 *     - 2+ pillared planks (a plank with a {@code #minecraft:logs} block in the
 *       column below that's in contact with water — a post holding the deck up)
 *
 *   Tier 3 — Wharf (scaled-up Pier plus deep water)
 *     - 48+ planks
 *     - 6+ light sources, 12+ railings, 4+ pillared planks
 *     - 2+ planks with a water column ≥3 deep directly underneath (pier
 *       actually reaches over real water, not a shoreline raft)
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

    private static final int T2_MIN_PLANKS = 24;
    private static final int T2_MIN_LIGHTS = 3;
    private static final int T2_MIN_RAILING = 6;
    private static final int T2_MIN_PILLARS = 2;

    private static final int T3_MIN_PLANKS = 48;
    private static final int T3_MIN_LIGHTS = 6;
    private static final int T3_MIN_RAILING = 12;
    private static final int T3_MIN_PILLARS = 4;
    private static final int T3_MIN_DEEP_WATER_REACH = 2;
    private static final int DEEP_WATER_DEPTH = 3;

    private static final int PILLAR_SCAN_DEPTH = 8;

    private static final long CACHE_TTL_TICKS = 200L;
    private static final long EMPTY_CACHE_TTL_TICKS = 400L;

    private static final Map<Key, Entry> CACHE = new ConcurrentHashMap<>();

    // Max tier ever observed for a given dock, keyed by dock identity (bounds
    // min-corner), NOT scan origin — otherwise two scans from different
    // positions over the same deck would each fire their own tier-up effect.
    // In-memory only: on server restart the first scan re-fires MAJOR/GRAND
    // once. Deliberate simplification, no SavedData wiring needed yet.
    private static final Map<String, Integer> MAX_TIER_SEEN = new ConcurrentHashMap<>();

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
        if (dock != null) {
            maybeFireRecognitionForTier(level, dock);
        }
        return dock;
    }

    public static void invalidate(ServerLevel level, BlockPos near) {
        if (near == null) return;
        String dim = level.dimension().location().toString();
        long posKey = near.asLong();
        CACHE.keySet().removeIf(k -> k.dim.equals(dim) && k.posKey == posKey);
    }

    /**
     * Fires a one-shot recognition effect when a dock's tier rises past its
     * previous high-water mark. First-ever recognition (0 → 1) gets MAJOR;
     * subsequent tier-ups scale (2 → MAJOR, 3 → GRAND) so reaching Wharf
     * feels like the peak. Tier drops do nothing — we only celebrate upward.
     *
     * Uses {@code playArea} so particles span the full dock footprint, and
     * announces a translated one-line status message to players nearby so
     * the event is legible without being in direct sight of the structure.
     */
    private static void maybeFireRecognitionForTier(ServerLevel level, Dock dock) {
        String dockId = dockIdentityKey(level, dock);
        int prev = MAX_TIER_SEEN.getOrDefault(dockId, 0);
        if (dock.tier() <= prev) return;
        MAX_TIER_SEEN.put(dockId, dock.tier());
        RecognitionEffects.Tier effect = dock.tier() >= 3
                ? RecognitionEffects.Tier.GRAND
                : RecognitionEffects.Tier.MAJOR;
        RecognitionEffects.playArea(level, dock.bounds(), effect);
        RecognitionEffects.announce(level, dock.centerVec(),
                Component.translatable("townstead.dock.recognized." + tierKey(dock.tier())),
                48.0);
    }

    private static String tierKey(int tier) {
        return switch (tier) {
            case 2 -> "pier";
            case 3 -> "wharf";
            default -> "landing";
        };
    }

    private static String dockIdentityKey(ServerLevel level, Dock dock) {
        BoundingBox bb = dock.bounds();
        return level.dimension().location().toString()
                + "|" + bb.minX() + "," + bb.minY() + "," + bb.minZ();
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
                && countRailings(level, bounds) >= T2_MIN_RAILING
                && countPillars(level, deck) >= T2_MIN_PILLARS;
    }

    private static boolean wharfQualifies(ServerLevel level, Set<Long> deck, BoundingBox bounds) {
        return countLights(level, bounds) >= T3_MIN_LIGHTS
                && countRailings(level, bounds) >= T3_MIN_RAILING
                && countPillars(level, deck) >= T3_MIN_PILLARS
                && countDeepWaterReach(level, deck) >= T3_MIN_DEEP_WATER_REACH;
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

    private static int countPillars(ServerLevel level, Set<Long> deck) {
        int count = 0;
        for (long k : deck) {
            if (hasPillar(level, BlockPos.of(k))) count++;
        }
        return count;
    }

    /**
     * A plank is "pillared" if some block in the column directly below it
     * ({@code #minecraft:logs}) has a water source horizontally adjacent.
     * Captures a post sunk into the water column holding up the deck.
     * Walks down to PILLAR_SCAN_DEPTH past intervening air or water; stops
     * at non-log solid ground (can't pillar through solid terrain).
     */
    private static boolean hasPillar(ServerLevel level, BlockPos plank) {
        for (int i = 1; i <= PILLAR_SCAN_DEPTH; i++) {
            BlockPos under = plank.below(i);
            BlockState us = level.getBlockState(under);
            if (us.is(BlockTags.LOGS)) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockState ns = level.getBlockState(under.relative(d));
                    if (ns.getFluidState().isSource() && ns.getFluidState().is(FluidTags.WATER)) {
                        return true;
                    }
                }
                continue;
            }
            if (us.isAir() || us.getFluidState().is(FluidTags.WATER)) continue;
            return false;
        }
        return false;
    }

    private static int countDeepWaterReach(ServerLevel level, Set<Long> deck) {
        int count = 0;
        for (long k : deck) {
            BlockPos p = BlockPos.of(k);
            boolean allWater = true;
            for (int i = 1; i <= DEEP_WATER_DEPTH; i++) {
                BlockState s = level.getBlockState(p.below(i));
                if (!s.getFluidState().isSource() || !s.getFluidState().is(FluidTags.WATER)) {
                    allWater = false;
                    break;
                }
            }
            if (allWater) count++;
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
