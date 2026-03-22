package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

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
        SnapshotKey key = new SnapshotKey(level.dimension().location().toString(), center.asLong(), horizontalRadius, verticalRadius);
        Snapshot current = SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (current != null && current.validAt(gameTime)) {
            return current;
        }
        if (current != null && !VillageAiBudget.tryConsume(level, "nearby-crop:" + key.centerKey() + ":" + horizontalRadius + ":" + verticalRadius, REFRESH_BUDGET_PER_TICK)) {
            return current;
        }
        Snapshot rebuilt = buildSnapshot(level, center, horizontalRadius, verticalRadius, gameTime);
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

    private static Snapshot buildSnapshot(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius, long gameTime) {
        List<BlockPos> matureCrops = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                matureCrops.add(pos.immutable());
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

    private record SnapshotKey(String dimensionId, long centerKey, int horizontalRadius, int verticalRadius) {}

    private static boolean contains(SnapshotKey key, BlockPos pos) {
        BlockPos center = BlockPos.of(key.centerKey());
        return Math.abs(pos.getX() - center.getX()) <= key.horizontalRadius()
                && Math.abs(pos.getY() - center.getY()) <= key.verticalRadius()
                && Math.abs(pos.getZ() - center.getZ()) <= key.horizontalRadius();
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
