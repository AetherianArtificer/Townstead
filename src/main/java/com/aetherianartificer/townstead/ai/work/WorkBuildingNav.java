package com.aetherianartificer.townstead.ai.work;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler.StationSlot;
import com.aetherianartificer.townstead.hunger.TargetReachabilityCache;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkBuildingNav {
    private static final long SNAPSHOT_TTL_TICKS = 80L;
    private static final int REFRESH_BUDGET_PER_TICK = 4;
    private static final int MAX_PATH_PROBES = 12;
    private static final int MAX_APPROACH_TARGETS = 32;
    private static final int APPROACH_MARGIN = 4;
    private static final int MAX_APPROACH_BASES = 4;
    private static final int MAX_INTERMEDIATE_APPROACH_TARGETS = 24;
    private static final int INTERMEDIATE_STEP_DISTANCE = 12;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private WorkBuildingNav() {}

    public static Snapshot snapshot(ServerLevel level, Set<Long> ownedBounds, @Nullable BlockPos reference) {
        if (level == null || ownedBounds == null || ownedBounds.isEmpty()) {
            return Snapshot.EMPTY;
        }
        SnapshotKey key = SnapshotKey.create(level, ownedBounds, reference);
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current;
        }
        if (current != null && !VillageAiBudget.tryConsume(level, "work-nav:" + key.boundsKey().hash, REFRESH_BUDGET_PER_TICK)) {
            return current;
        }
        Snapshot rebuilt = buildSnapshot(level, ownedBounds, reference, gameTime);
        SNAPSHOTS.put(key, rebuilt);
        return rebuilt;
    }

    public static boolean isInside(Snapshot snapshot, @Nullable BlockPos pos) {
        return snapshot != null && pos != null && snapshot.walkableInterior().contains(pos.asLong());
    }

    public static boolean isInsideOrOnStationStand(Snapshot snapshot, @Nullable BlockPos pos) {
        if (snapshot == null || pos == null) return false;
        if (snapshot.walkableInterior().contains(pos.asLong())) return true;
        for (List<BlockPos> stands : snapshot.stationStandPositions().values()) {
            for (BlockPos stand : stands) {
                if (stand.equals(pos)) return true;
            }
        }
        return false;
    }

    public static boolean isInsideOrNearEntry(Snapshot snapshot, @Nullable BlockPos pos, double maxDistanceSq) {
        if (snapshot == null || pos == null) return false;
        if (isInsideOrOnStationStand(snapshot, pos)) return true;
        return isNearEntry(snapshot, pos, maxDistanceSq);
    }

    public static boolean isNearEntry(Snapshot snapshot, @Nullable BlockPos pos, double maxDistanceSq) {
        if (snapshot == null || pos == null) return false;
        Vec3iLike here = new Vec3iLike(pos.getX(), pos.getY(), pos.getZ());
        for (BlockPos entry : snapshot.entryTargets()) {
            if (here.distToCenterSqr(entry) <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable BlockPos nearestStationStand(Snapshot snapshot, VillagerEntityMCA villager, BlockPos anchor) {
        if (snapshot == null || villager == null || anchor == null) return null;
        List<BlockPos> stands = snapshot.stationStandPositions().get(anchor.asLong());
        if (stands == null || stands.isEmpty()) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos stand : stands) {
            double dist = villager.distanceToSqr(stand.getX() + 0.5, stand.getY() + 0.5, stand.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = stand;
            }
        }
        return best;
    }

    public static @Nullable BlockPos chooseEntryTarget(ServerLevel level, VillagerEntityMCA villager, Snapshot snapshot, int closeEnough) {
        return chooseReachableTarget(level, villager, snapshot.entryTargets(), closeEnough, true);
    }

    public static @Nullable BlockPos chooseApproachTarget(ServerLevel level, VillagerEntityMCA villager, Snapshot snapshot, int closeEnough) {
        BlockPos direct = chooseReachableTarget(level, villager, snapshot.approachTargets(), closeEnough, false);
        if (direct != null) return direct;
        return chooseReachableTarget(level, villager, intermediateApproachTargets(level, villager, snapshot), closeEnough, false);
    }

    public static @Nullable BlockPos chooseReachableTarget(
            ServerLevel level,
            VillagerEntityMCA villager,
            List<BlockPos> candidates,
            int closeEnough
    ) {
        return chooseReachableTarget(level, villager, candidates, closeEnough, true);
    }

    public static @Nullable BlockPos chooseReachableTarget(
            ServerLevel level,
            VillagerEntityMCA villager,
            List<BlockPos> candidates,
            int closeEnough,
            boolean useReachabilityCache
    ) {
        if (level == null || villager == null || candidates == null || candidates.isEmpty()) return null;
        List<BlockPos> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(pos ->
                villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        int attempts = 0;
        for (BlockPos pos : ordered) {
            if (++attempts > MAX_PATH_PROBES) break;
            if (useReachabilityCache && !TargetReachabilityCache.canAttempt(level, villager, pos)) continue;
            Path path = villager.getNavigation().createPath(pos, closeEnough);
            if (path == null || !path.canReach()) {
                WorkNavigationMetrics.recordPathAttempt(false);
                if (useReachabilityCache) {
                    TargetReachabilityCache.recordFailure(level, villager, pos, 80);
                }
                continue;
            }
            WorkNavigationMetrics.recordPathAttempt(true);
            if (useReachabilityCache) {
                TargetReachabilityCache.clear(level, villager, pos);
            }
            return pos;
        }
        return null;
    }

    public static ReachabilitySelection traceReachableTargets(
            ServerLevel level,
            VillagerEntityMCA villager,
            List<BlockPos> candidates,
            int closeEnough,
            int maxProbes
    ) {
        return traceReachableTargets(level, villager, candidates, closeEnough, maxProbes, true);
    }

    public static ReachabilitySelection traceReachableTargets(
            ServerLevel level,
            VillagerEntityMCA villager,
            List<BlockPos> candidates,
            int closeEnough,
            int maxProbes,
            boolean useReachabilityCache
    ) {
        if (level == null || villager == null || candidates == null || candidates.isEmpty()) {
            return new ReachabilitySelection(null, List.of());
        }
        List<BlockPos> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble(pos ->
                villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        List<ReachabilityProbe> probes = new ArrayList<>();
        int attempts = 0;
        for (BlockPos pos : ordered) {
            if (++attempts > maxProbes) break;
            double distSq = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (useReachabilityCache && !TargetReachabilityCache.canAttempt(level, villager, pos)) {
                probes.add(new ReachabilityProbe(pos, "cached_skip", distSq));
                continue;
            }
            Path path = villager.getNavigation().createPath(pos, closeEnough);
            if (path == null) {
                probes.add(new ReachabilityProbe(pos, "null", distSq));
                continue;
            }
            if (!path.canReach()) {
                probes.add(new ReachabilityProbe(pos, "cannot_reach", distSq));
                continue;
            }
            probes.add(new ReachabilityProbe(pos, "reachable", distSq));
            return new ReachabilitySelection(pos, List.copyOf(probes));
        }
        return new ReachabilitySelection(null, List.copyOf(probes));
    }

    public static List<BlockPos> intermediateApproachTargets(
            ServerLevel level,
            VillagerEntityMCA villager,
            Snapshot snapshot
    ) {
        if (level == null || villager == null || snapshot == null || snapshot.approachTargets().isEmpty()) {
            return List.of();
        }
        List<BlockPos> orderedBases = new ArrayList<>(snapshot.approachTargets());
        orderedBases.sort(Comparator.comparingDouble(pos ->
                villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        BlockPos start = villager.blockPosition();
        Set<Long> seen = new HashSet<>();
        List<BlockPos> candidates = new ArrayList<>();
        int baseCount = 0;
        for (BlockPos base : orderedBases) {
            if (++baseCount > MAX_APPROACH_BASES) break;
            double dx = base.getX() - start.getX();
            double dz = base.getZ() - start.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal < INTERMEDIATE_STEP_DISTANCE + 2) continue;
            double ratio = Math.min(0.6d, INTERMEDIATE_STEP_DISTANCE / Math.max(1.0d, horizontal));
            int projectedX = (int) Math.round(start.getX() + dx * ratio);
            int projectedZ = (int) Math.round(start.getZ() + dz * ratio);
            int projectedY = (int) Math.round(start.getY() + (base.getY() - start.getY()) * ratio);
            addIntermediateCandidates(level, new BlockPos(projectedX, projectedY, projectedZ), seen, candidates);
            if (candidates.size() >= MAX_INTERMEDIATE_APPROACH_TARGETS) break;
        }
        candidates.sort(Comparator.comparingDouble(pos ->
                villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        if (candidates.size() > MAX_INTERMEDIATE_APPROACH_TARGETS) {
            return List.copyOf(candidates.subList(0, MAX_INTERMEDIATE_APPROACH_TARGETS));
        }
        return List.copyOf(candidates);
    }

    private static void addIntermediateCandidates(
            ServerLevel level,
            BlockPos projected,
            Set<Long> seen,
            List<BlockPos> candidates
    ) {
        int[] yOffsets = {0, 1, -1, 2, -2, 3};
        int[][] offsets = {
                {0, 0},
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };
        for (int yOffset : yOffsets) {
            for (int[] offset : offsets) {
                BlockPos candidate = projected.offset(offset[0], yOffset, offset[1]);
                if (!WorkPathing.isSafeStandPosition(level, candidate)) continue;
                if (seen.add(candidate.asLong())) {
                    candidates.add(candidate.immutable());
                }
            }
        }
    }

    private static Snapshot buildSnapshot(ServerLevel level, Set<Long> ownedBounds, @Nullable BlockPos reference, long gameTime) {
        WorkNavigationMetrics.recordSnapshotRebuild();
        Bounds bounds = Bounds.of(ownedBounds);
        if (bounds == null || bounds.volume() > 32768) {
            return Snapshot.EMPTY.withExpiry(gameTime + SNAPSHOT_TTL_TICKS);
        }

        Set<Long> standableTiles = new HashSet<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (WorkPathing.isSafeStandPosition(level, pos)) {
                        standableTiles.add(pos.asLong());
                    }
                }
            }
        }

        List<StationSlot> stations = new ArrayList<>();
        Map<Long, List<BlockPos>> stationStands = new HashMap<>();
        for (long key : ownedBounds) {
            BlockPos pos = BlockPos.of(key);
            ModRecipeRegistry.StationType type = StationHandler.stationType(level, pos);
            if (type == null) continue;
            int capacity = switch (type) {
                case FIRE_STATION -> StationHandler.surfaceFreeSlotCount(level, pos);
                case HOT_STATION -> 1;
                case CUTTING_BOARD -> 1;
            };
            if (capacity <= 0) continue;
            List<BlockPos> stands = WorkPathing.standCandidatesAround(level, pos, standableTiles);
            if (stands.isEmpty()) continue;
            stations.add(new StationSlot(pos.immutable(), type,
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()),
                    capacity));
            stationStands.put(pos.asLong(), stands);
        }

        Set<Long> walkable = floodWalkable(bounds, standableTiles, stationStands, reference);
        Map<Long, List<BlockPos>> filteredStands = new HashMap<>();
        for (Map.Entry<Long, List<BlockPos>> entry : stationStands.entrySet()) {
            List<BlockPos> kept = entry.getValue().stream()
                    .filter(pos -> walkable.contains(pos.asLong()))
                    .toList();
            if (!kept.isEmpty()) filteredStands.put(entry.getKey(), kept);
        }
        List<BlockPos> entryTargets = computeEntryTargets(bounds, walkable);
        if (entryTargets.isEmpty()) {
            entryTargets = walkable.stream().map(BlockPos::of).toList();
        }
        List<BlockPos> approachTargets = computeApproachTargets(level, bounds, walkable);
        return new Snapshot(Set.copyOf(ownedBounds), Set.copyOf(walkable), List.copyOf(entryTargets), List.copyOf(approachTargets), Map.copyOf(filteredStands), List.copyOf(stations), gameTime + SNAPSHOT_TTL_TICKS);
    }

    private static Set<Long> floodWalkable(
            Bounds bounds,
            Set<Long> standableTiles,
            Map<Long, List<BlockPos>> stationStands,
            @Nullable BlockPos reference
    ) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (List<BlockPos> stands : stationStands.values()) {
            for (BlockPos stand : stands) {
                if (visited.add(stand.asLong())) {
                    queue.add(stand);
                }
            }
        }
        if (queue.isEmpty()) {
            BlockPos seed = chooseFallbackSeed(standableTiles, reference);
            if (seed != null) {
                visited.add(seed.asLong());
                queue.add(seed);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!bounds.contains(next)) continue;
                if (!standableTiles.contains(next.asLong())) continue;
                if (visited.add(next.asLong())) {
                    queue.addLast(next);
                }
            }
        }
        return visited;
    }

    private static @Nullable BlockPos chooseFallbackSeed(Set<Long> standableTiles, @Nullable BlockPos reference) {
        if (standableTiles.isEmpty()) return null;
        if (reference == null) return BlockPos.of(standableTiles.iterator().next());
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (long key : standableTiles) {
            BlockPos pos = BlockPos.of(key);
            double dist = reference.distSqr(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    private static List<BlockPos> computeEntryTargets(Bounds bounds, Set<Long> walkable) {
        List<BlockPos> targets = new ArrayList<>();
        for (long key : walkable) {
            BlockPos pos = BlockPos.of(key);
            if (isEntryTile(bounds, walkable, pos)) {
                targets.add(pos);
            }
        }
        targets.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
        return List.copyOf(targets);
    }

    private static boolean isEntryTile(Bounds bounds, Set<Long> walkable, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(direction);
            if (!bounds.contains(next)) return true;
            if (!walkable.contains(next.asLong())) return true;
        }
        return false;
    }

    private static List<BlockPos> computeApproachTargets(ServerLevel level, Bounds bounds, Set<Long> walkable) {
        List<BlockPos> targets = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long key : walkable) {
            BlockPos interior = BlockPos.of(key);
            if (!isEntryTile(bounds, walkable, interior)) continue;
            if (seen.add(interior.asLong())) {
                targets.add(interior.immutable());
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = interior.relative(direction);
                if (bounds.contains(adjacent)) continue;
                for (int step = 1; step <= APPROACH_MARGIN; step++) {
                    BlockPos outside = interior.relative(direction, step);
                    if (bounds.contains(outside)) continue;
                    if (!WorkPathing.isSafeStandPosition(level, outside)) continue;
                    if (seen.add(outside.asLong())) {
                        targets.add(outside.immutable());
                    }
                    break;
                }
            }
        }
        // Fallback: use a small safe perimeter ring around the worksite bounds.
        if (targets.isEmpty()) {
            int minX = bounds.minX - APPROACH_MARGIN;
            int maxX = bounds.maxX + APPROACH_MARGIN;
            int minZ = bounds.minZ - APPROACH_MARGIN;
            int maxZ = bounds.maxZ + APPROACH_MARGIN;
            int minY = bounds.minY - 1;
            int maxY = bounds.maxY + 1;
            int centerX = (bounds.minX + bounds.maxX) / 2;
            int centerY = bounds.minY;
            int centerZ = (bounds.minZ + bounds.maxZ) / 2;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos north = new BlockPos(x, y, minZ);
                    BlockPos south = new BlockPos(x, y, maxZ);
                    if (seen.add(north.asLong()) && WorkPathing.isSafeStandPosition(level, north)) {
                        targets.add(north);
                    }
                    if (seen.add(south.asLong()) && WorkPathing.isSafeStandPosition(level, south)) {
                        targets.add(south);
                    }
                }
                for (int z = minZ + 1; z < maxZ; z++) {
                    BlockPos west = new BlockPos(minX, y, z);
                    BlockPos east = new BlockPos(maxX, y, z);
                    if (seen.add(west.asLong()) && WorkPathing.isSafeStandPosition(level, west)) {
                        targets.add(west);
                    }
                    if (seen.add(east.asLong()) && WorkPathing.isSafeStandPosition(level, east)) {
                        targets.add(east);
                    }
                }
            }
            targets.sort(Comparator.comparingDouble(pos -> pos.distSqr(new BlockPos(centerX, centerY, centerZ))));
            if (targets.size() > MAX_APPROACH_TARGETS) {
                targets = new ArrayList<>(targets.subList(0, MAX_APPROACH_TARGETS));
            }
        }
        targets.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
        return List.copyOf(targets);
    }

    public record Snapshot(
            Set<Long> ownedBounds,
            Set<Long> walkableInterior,
            List<BlockPos> entryTargets,
            List<BlockPos> approachTargets,
            Map<Long, List<BlockPos>> stationStandPositions,
            List<StationSlot> stations,
            long expiresAt
    ) {
        public static final Snapshot EMPTY = new Snapshot(Set.of(), Set.of(), List.of(), List.of(), Map.of(), List.of(), 0L);

        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        Snapshot withExpiry(long expiresAt) {
            return new Snapshot(ownedBounds, walkableInterior, entryTargets, approachTargets, stationStandPositions, stations, expiresAt);
        }
    }

    public record ReachabilityProbe(BlockPos pos, String result, double distanceSq) {}

    public record ReachabilitySelection(@Nullable BlockPos target, List<ReachabilityProbe> probes) {}

    private record SnapshotKey(String dimensionId, BoundsKey boundsKey, long referenceKey) {
        static SnapshotKey create(ServerLevel level, Set<Long> bounds, @Nullable BlockPos reference) {
            return new SnapshotKey(level.dimension().location().toString(), BoundsKey.of(bounds), reference == null ? Long.MIN_VALUE : reference.asLong());
        }
    }

    private record BoundsKey(int size, long hash) {
        static BoundsKey of(Set<Long> bounds) {
            long hash = 1125899906842597L;
            for (long key : bounds) {
                hash = 31L * hash + key;
            }
            return new BoundsKey(bounds.size(), hash);
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static @Nullable Bounds of(Set<Long> ownedBounds) {
            if (ownedBounds == null || ownedBounds.isEmpty()) return null;
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (long key : ownedBounds) {
                BlockPos pos = BlockPos.of(key);
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    private record Vec3iLike(int x, int y, int z) {
        double distToCenterSqr(BlockPos pos) {
            double dx = x + 0.5d - (pos.getX() + 0.5d);
            double dy = y + 0.5d - (pos.getY() + 0.5d);
            double dz = z + 0.5d - (pos.getZ() + 0.5d);
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
