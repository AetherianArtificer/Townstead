package com.aetherianartificer.townstead.storage;


import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class WorksiteStorageCandidateIndex {
    private static final long SNAPSHOT_TTL_TICKS = 200L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final int HORIZONTAL_RADIUS = StorageCandidateOrder.HORIZONTAL_RADIUS;
    private static final int VERTICAL_RADIUS = StorageCandidateOrder.VERTICAL_RADIUS;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private WorksiteStorageCandidateIndex() {}

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

    private static Snapshot buildSnapshot(ServerLevel level, WorksiteStorageIndex.BoundsKey boundsKey, long gameTime) {
        long[] bounds = boundsKey.positions();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : bounds) {
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
        }
        minX -= HORIZONTAL_RADIUS; maxX += HORIZONTAL_RADIUS;
        minZ -= HORIZONTAL_RADIUS; maxZ += HORIZONTAL_RADIUS;
        minY -= VERTICAL_RADIUS; maxY += VERTICAL_RADIUS;

        // Only block entities can be candidates, so read them from the loaded chunks instead of
        // probing every block. A chunk that is not loaded falls back to the probe, which loads it.
        List<Long> found = new ArrayList<>();
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) return probeSnapshot(level, boundsKey, gameTime);
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ
                            || pos.getY() < minY || pos.getY() > maxY) continue;
                    found.add(pos.asLong());
                }
            }
        }
        if (found.isEmpty()) return new Snapshot(List.of(), gameTime + SNAPSHOT_TTL_TICKS);

        long[] nearby = new long[found.size()];
        for (int i = 0; i < nearby.length; i++) nearby[i] = found.get(i);
        long[] ordered = StorageCandidateOrder.inProbeOrder(bounds, nearby);
        StorageSearchContext searchContext = new StorageSearchContext(level, Math.max(64, ordered.length), Math.max(16, ordered.length));
        List<BlockPos> positions = new ArrayList<>();
        for (long key : ordered) {
            StorageSearchContext.ObservedBlock observed = searchContext.observe(BlockPos.of(key));
            BlockEntity blockEntity = observed.blockEntity();
            if (blockEntity == null) continue;
            if (!StorageRoles.isStorageCandidate(level, observed.pos(), blockEntity)) continue;
            positions.add(observed.pos());
        }
        return new Snapshot(List.copyOf(positions), gameTime + SNAPSHOT_TTL_TICKS);
    }

    private static Snapshot probeSnapshot(ServerLevel level, WorksiteStorageIndex.BoundsKey boundsKey, long gameTime) {
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
                        if (!StorageRoles.isStorageCandidate(level, observed.pos(), blockEntity)) continue;
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

    private record SnapshotKey(String dimensionId, WorksiteStorageIndex.BoundsKey boundsKey) {
        static SnapshotKey create(ServerLevel level, Set<Long> kitchenBounds) {
            return new SnapshotKey(level.dimension().location().toString(), WorksiteStorageIndex.BoundsKey.of(kitchenBounds));
        }
    }
}
