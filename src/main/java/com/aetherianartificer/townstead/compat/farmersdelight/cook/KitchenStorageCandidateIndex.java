package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class KitchenStorageCandidateIndex {
    private static final long SNAPSHOT_TTL_TICKS = 200L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final int HORIZONTAL_RADIUS = 2;
    private static final int VERTICAL_RADIUS = 1;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private KitchenStorageCandidateIndex() {}

    static List<BlockPos> candidates(ServerLevel level, Set<Long> kitchenBounds) {
        if (level == null || kitchenBounds == null || kitchenBounds.isEmpty()) {
            return List.of();
        }
        SnapshotKey key = SnapshotKey.create(level, kitchenBounds);
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current.positions();
        }
        if (current != null
                && !VillageAiBudget.tryConsume(level, "kitchen-storage-candidates:" + key.boundsKey().cachedHash(),
                REFRESH_BUDGET_PER_TICK)) {
            return current.positions();
        }
        Snapshot rebuilt = buildSnapshot(level, key.boundsKey(), gameTime);
        SNAPSHOTS.put(key, rebuilt);
        return rebuilt.positions();
    }

    static void invalidate(ServerLevel level) {
        if (level == null) return;
        String dimensionId = level.dimension().location().toString();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId));
    }

    static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        String dimensionId = level.dimension().location().toString();
        long changedKey = changedPos.asLong();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId)
                && key.boundsKey().candidateSearchContains(changedKey));
    }

    private static Snapshot buildSnapshot(ServerLevel level, KitchenStorageIndex.BoundsKey boundsKey, long gameTime) {
        int estimatedObservedBlocks = Math.max(64, boundsKey.positions().length * 20);
        StorageSearchContext searchContext = new StorageSearchContext(level, estimatedObservedBlocks, estimatedObservedBlocks / 4);
        List<BlockPos> positions = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long key : boundsKey.positions()) {
            BlockPos base = BlockPos.of(key);
            for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                    for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                        BlockPos pos = base.offset(dx, dy, dz);
                        if (!seen.add(pos.asLong())) continue;
                        StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
                        BlockEntity blockEntity = observed.blockEntity();
                        if (blockEntity == null) continue;
                        if (!StationHandler.isCookStorageCandidate(level, observed.pos(), blockEntity)) continue;
                        positions.add(observed.pos());
                    }
                }
            }
        }
        return new Snapshot(List.copyOf(positions), gameTime + SNAPSHOT_TTL_TICKS);
    }

    private record Snapshot(List<BlockPos> positions, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }
    }

    private record SnapshotKey(String dimensionId, KitchenStorageIndex.BoundsKey boundsKey) {
        static SnapshotKey create(ServerLevel level, Set<Long> kitchenBounds) {
            return new SnapshotKey(level.dimension().location().toString(), KitchenStorageIndex.BoundsKey.of(kitchenBounds));
        }
    }
}
