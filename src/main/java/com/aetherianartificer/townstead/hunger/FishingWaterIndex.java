package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.ai.work.WorkPathing;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans around a barrel anchor for fishable water source blocks, each paired with
 * a precomputed safe stand position. Results are cached per (dim, anchor, radius)
 * with a short TTL, matching the ButcherWorkIndex cadence pattern.
 */
public final class FishingWaterIndex {
    private static final long SNAPSHOT_TTL_TICKS = 60L;
    /** Longer TTL when the scan found nothing — cuts re-scan cost in waterless areas. */
    private static final long SNAPSHOT_EMPTY_TTL_TICKS = 200L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    /** Minimum connected water source blocks to count as a "fishable body" — excludes irrigation puddles. */
    private static final int MIN_POOL_SIZE = 20;
    /** Safety cap on per-pool flood-fill. Pools over this are still treated as large. */
    private static final int MAX_POOL_SCAN = 500;
    /** Tick interval between opportunistic expired-entry sweeps during rebuilds. */
    private static final long PRUNE_INTERVAL_TICKS = 1200L;
    private static volatile long lastPruneTick = 0L;
    private static final Map<WaterSearchKey, WaterSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private FishingWaterIndex() {}

    public record FishingSpot(BlockPos waterPos, BlockPos standPos) {}

    public static @Nullable FishingSpot nearestSpot(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadiusDown,
            int verticalRadiusUp
    ) {
        WaterSnapshot snapshot = getOrBuildSnapshot(level, anchor, horizontalRadius, verticalRadiusDown, verticalRadiusUp);
        return snapshot == null ? null : snapshot.nearestTo(villager);
    }

    /**
     * Returns a randomly chosen fishing spot from a large water body that isn't
     * claimed by another villager. Tries the configured horizontal radius first;
     * if nothing is available, expands to a larger radius before giving up.
     * Random selection gives natural rotation across work days as claims come and go.
     */
    public static @Nullable FishingSpot availableSpot(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadiusDown,
            int verticalRadiusUp,
            int fallbackHorizontalRadius
    ) {
        FishingSpot chosen = pickUnclaimed(level, villager, anchor, horizontalRadius, verticalRadiusDown, verticalRadiusUp);
        if (chosen != null) return chosen;
        if (fallbackHorizontalRadius > horizontalRadius) {
            return pickUnclaimed(level, villager, anchor, fallbackHorizontalRadius, verticalRadiusDown, verticalRadiusUp);
        }
        return null;
    }

    private static @Nullable FishingSpot pickUnclaimed(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadiusDown,
            int verticalRadiusUp
    ) {
        WaterSnapshot snapshot = getOrBuildSnapshot(level, anchor, horizontalRadius, verticalRadiusDown, verticalRadiusUp);
        if (snapshot == null || snapshot.spots().isEmpty()) return null;
        java.util.UUID uuid = villager.getUUID();
        List<FishingSpot> unclaimed = new ArrayList<>();
        for (FishingSpot spot : snapshot.spots()) {
            if (FishingSpotClaims.isClaimedByOther(level, uuid, spot.waterPos())) continue;
            unclaimed.add(spot);
        }
        if (unclaimed.isEmpty()) return null;
        return unclaimed.get(level.random.nextInt(unclaimed.size()));
    }

    private static @Nullable WaterSnapshot getOrBuildSnapshot(
            ServerLevel level,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadiusDown,
            int verticalRadiusUp
    ) {
        if (anchor == null) return null;
        WaterSearchKey key = new WaterSearchKey(
                level.dimension().location().toString(),
                anchor.asLong(),
                horizontalRadius,
                verticalRadiusDown,
                verticalRadiusUp);
        WaterSnapshot snapshot = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (snapshot == null || !snapshot.validAt(gameTime)) {
            if (snapshot == null || VillageAiBudget.tryConsume(level, "fisherman-water:" + key.anchorKey() + ":" + horizontalRadius + ":" + verticalRadiusDown + ":" + verticalRadiusUp, REFRESH_BUDGET_PER_TICK)) {
                snapshot = buildSnapshot(level, anchor, horizontalRadius, verticalRadiusDown, verticalRadiusUp, gameTime);
                SNAPSHOTS.put(key, snapshot);
                maybePruneExpired(gameTime);
            }
        }
        return snapshot;
    }

    private static void maybePruneExpired(long gameTime) {
        if (gameTime - lastPruneTick < PRUNE_INTERVAL_TICKS) return;
        lastPruneTick = gameTime;
        SNAPSHOTS.entrySet().removeIf(e -> e.getValue().expiresAt() < gameTime);
    }

    public static void invalidate(ServerLevel level, BlockPos anchor) {
        if (anchor == null) return;
        String dim = level.dimension().location().toString();
        long anchorKey = anchor.asLong();
        SNAPSHOTS.keySet().removeIf(k -> k.dimensionId().equals(dim) && k.anchorKey() == anchorKey);
    }

    private static WaterSnapshot buildSnapshot(
            ServerLevel level,
            BlockPos anchor,
            int horizontalRadius,
            int verticalRadiusDown,
            int verticalRadiusUp,
            long gameTime
    ) {
        // Pass 1: collect candidate water-source blocks (source water + passable above).
        List<BlockPos> candidates = new ArrayList<>();
        Map<Long, BlockPos> candidateByKey = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                anchor.offset(-horizontalRadius, -verticalRadiusDown, -horizontalRadius),
                anchor.offset(horizontalRadius, verticalRadiusUp, horizontalRadius))) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isSource()) continue;
            if (!state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
            BlockPos above = pos.above();
            BlockState aboveState = level.getBlockState(above);
            if (!aboveState.isAir() && !aboveState.getCollisionShape(level, above).isEmpty()) continue;
            BlockPos immutable = pos.immutable();
            candidates.add(immutable);
            candidateByKey.put(immutable.asLong(), immutable);
        }

        // Pass 2: flood-fill to classify each candidate's pool as large or small.
        // Fill traverses all water-source blocks, including ones outside the search
        // box, so a pond that extends beyond the barrel's radius still counts as
        // a single big body. Small puddles (farm irrigation) get filtered out.
        Set<Long> visited = new HashSet<>();
        Set<Long> largePoolMembers = new HashSet<>();
        for (BlockPos candidate : candidates) {
            long key = candidate.asLong();
            if (visited.contains(key)) continue;
            Set<Long> pool = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            pool.add(key);
            visited.add(key);
            queue.push(candidate);
            while (!queue.isEmpty() && pool.size() < MAX_POOL_SCAN) {
                BlockPos cur = queue.poll();
                for (Direction dir : Direction.values()) {
                    BlockPos next = cur.relative(dir);
                    long nextKey = next.asLong();
                    if (pool.contains(nextKey)) continue;
                    BlockState ns = level.getBlockState(next);
                    if (!ns.getFluidState().isSource()) continue;
                    if (!ns.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
                    pool.add(nextKey);
                    visited.add(nextKey);
                    queue.push(next);
                }
            }
            if (pool.size() >= MIN_POOL_SIZE) {
                largePoolMembers.addAll(pool);
            }
        }

        // Pass 3: build FishingSpot entries only for large-pool candidates.
        List<FishingSpot> spots = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (!largePoolMembers.contains(pos.asLong())) continue;
            BlockPos stand = findStandFor(level, pos);
            if (stand == null) continue;
            spots.add(new FishingSpot(pos, stand.immutable()));
        }
        long ttl = spots.isEmpty() ? SNAPSHOT_EMPTY_TTL_TICKS : SNAPSHOT_TTL_TICKS;
        return new WaterSnapshot(List.copyOf(spots), gameTime + ttl);
    }

    private static @Nullable BlockPos findStandFor(ServerLevel level, BlockPos waterPos) {
        List<BlockPos> candidates = WorkPathing.standCandidatesAround(level, waterPos, null);
        if (candidates.isEmpty()) return null;
        return candidates.get(0);
    }

    private record WaterSearchKey(String dimensionId, long anchorKey, int horizontalRadius, int verticalRadiusDown, int verticalRadiusUp) {}

    private record WaterSnapshot(List<FishingSpot> spots, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable FishingSpot nearestTo(VillagerEntityMCA villager) {
            FishingSpot best = null;
            double bestDist = Double.MAX_VALUE;
            for (FishingSpot spot : spots) {
                double dist = villager.distanceToSqr(
                        spot.standPos().getX() + 0.5,
                        spot.standPos().getY() + 0.5,
                        spot.standPos().getZ() + 0.5);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = spot;
                }
            }
            return best;
        }
    }
}
