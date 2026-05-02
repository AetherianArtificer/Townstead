package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NearbyCropIndex {
    private static final long SNAPSHOT_TTL_TICKS = 10L;
    private static final int REFRESH_BUDGET_PER_TICK = 4;
    private static final Map<SnapshotKey, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private NearbyCropIndex() {}

    public static Snapshot snapshot(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius) {
        SnapshotKey key = SnapshotKey.from(level, center, horizontalRadius, verticalRadius);
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current;
        }
        if (current != null && !VillageAiBudget.tryConsume(level, "nearby-crop:" + key.centerKey() + ":" + horizontalRadius + ":" + verticalRadius, REFRESH_BUDGET_PER_TICK)) {
            return current;
        }
        Snapshot rebuilt = buildSnapshot(level, key, gameTime);
        SNAPSHOTS.put(key, rebuilt);
        return rebuilt;
    }

    public static void invalidate(ServerLevel level) {
        String dimensionId = level.dimension().location().toString();
        SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId));
    }

    public static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        String dimensionId = level.dimension().location().toString();
        for (Map.Entry<SnapshotKey, Snapshot> entry : SNAPSHOTS.entrySet()) {
            SnapshotKey key = entry.getKey();
            if (!key.dimensionId().equals(dimensionId) || !contains(key, changedPos)) continue;
            SNAPSHOTS.put(key, refreshSnapshotEntry(level, entry.getValue(), changedPos));
        }
    }

    private static Snapshot buildSnapshot(ServerLevel level, SnapshotKey key, long gameTime) {
        List<BlockPos> matureCrops = new ArrayList<>();
        int minSectionY = SectionPos.blockToSectionCoord(key.minY());
        int maxSectionY = SectionPos.blockToSectionCoord(key.maxY());

        for (int chunkX = key.minChunkX(); chunkX <= key.maxChunkX(); chunkX++) {
            int minX = Math.max(key.minX(), SectionPos.sectionToBlockCoord(chunkX));
            int maxX = Math.min(key.maxX(), SectionPos.sectionToBlockCoord(chunkX, 15));
            for (int chunkZ = key.minChunkZ(); chunkZ <= key.maxChunkZ(); chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                int minZ = Math.max(key.minZ(), SectionPos.sectionToBlockCoord(chunkZ));
                int maxZ = Math.min(key.maxZ(), SectionPos.sectionToBlockCoord(chunkZ, 15));

                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int sectionIndex = level.getSectionIndexFromSectionY(sectionY);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;

                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof CropBlock)) continue;

                    int minY = Math.max(key.minY(), SectionPos.sectionToBlockCoord(sectionY));
                    int maxY = Math.min(key.maxY(), SectionPos.sectionToBlockCoord(sectionY, 15));
                    for (int y = minY; y <= maxY; y++) {
                        int localY = y & 15;
                        for (int z = minZ; z <= maxZ; z++) {
                            int localZ = z & 15;
                            for (int x = minX; x <= maxX; x++) {
                                int localX = x & 15;
                                BlockState state = section.getBlockState(localX, localY, localZ);
                                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                                    matureCrops.add(new BlockPos(x, y, z));
                                }
                            }
                        }
                    }
                }
            }
        }
        return new Snapshot(List.copyOf(matureCrops), gameTime + SNAPSHOT_TTL_TICKS);
    }

    public record Snapshot(List<BlockPos> matureCrops, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        public @Nullable BlockPos nearestTo(VillagerEntityMCA villager) {
            BlockPos bestPos = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : matureCrops) {
                double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = pos;
                }
            }
            return bestPos;
        }
    }

    private record SnapshotKey(String dimensionId, long centerKey, int minX, int maxX, int minChunkX, int maxChunkX, int minZ, int maxZ, int minChunkZ, int maxChunkZ, int minY, int maxY) {
        static SnapshotKey from(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius) {
            BlockPos min = center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius);
            BlockPos max = center.offset(horizontalRadius, verticalRadius, horizontalRadius);
            return new SnapshotKey(
                    level.dimension().location().toString(),
                    center.asLong(),
                    min.getX(),
                    max.getX(),
                    SectionPos.blockToSectionCoord(min.getX()),
                    SectionPos.blockToSectionCoord(max.getX()),
                    min.getZ(),
                    max.getZ(),
                    SectionPos.blockToSectionCoord(min.getZ()),
                    SectionPos.blockToSectionCoord(max.getZ()),
                    min.getY(),
                    max.getY()
            );
        }
    }

    private static boolean contains(SnapshotKey key, BlockPos pos) {
        return pos.getX() >= key.minX() && pos.getX() <= key.maxX()
                && pos.getZ() >= key.minZ() && pos.getZ() <= key.maxZ()
                && pos.getY() >= key.minY() && pos.getY() <= key.maxY();
    }

    private static Snapshot refreshSnapshotEntry(ServerLevel level, Snapshot snapshot, BlockPos changedPos) {
        List<BlockPos> refreshed = new ArrayList<>();
        for (BlockPos pos : snapshot.matureCrops()) {
            if (!pos.equals(changedPos)) {
                refreshed.add(pos);
            }
        }
        BlockState state = level.getBlockState(changedPos);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            refreshed.add(changedPos.immutable());
        }
        return new Snapshot(List.copyOf(refreshed), snapshot.expiresAt());
    }
}
