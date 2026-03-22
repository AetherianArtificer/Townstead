package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ButcherWorkIndex {
    private static final long SNAPSHOT_TTL_TICKS = 20L;
    private static final int REFRESH_BUDGET_PER_TICK = 2;
    private static final Map<SmokerSearchKey, SmokerSnapshot> SMOKER_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<StandKey, StandSnapshot> STAND_SNAPSHOTS = new ConcurrentHashMap<>();

    private ButcherWorkIndex() {}

    static @Nullable BlockPos nearestSmoker(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius) {
        SmokerSearchKey key = new SmokerSearchKey(level.dimension().location().toString(), villager.blockPosition().asLong(), horizontalRadius, verticalRadius);
        SmokerSnapshot snapshot = SMOKER_SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (snapshot == null || !snapshot.validAt(gameTime)) {
            if (snapshot == null || VillageAiBudget.tryConsume(level, "butcher-smoker:" + key.centerKey() + ":" + horizontalRadius + ":" + verticalRadius, REFRESH_BUDGET_PER_TICK)) {
                snapshot = buildSmokerSnapshot(level, villager.blockPosition(), horizontalRadius, verticalRadius, gameTime);
                SMOKER_SNAPSHOTS.put(key, snapshot);
            }
        }
        return snapshot == null ? null : snapshot.nearestTo(villager);
    }

    static @Nullable BlockPos nearestStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (anchor == null) return null;
        StandKey key = new StandKey(level.dimension().location().toString(), anchor.asLong());
        StandSnapshot snapshot = STAND_SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (snapshot == null || !snapshot.validAt(gameTime)) {
            if (snapshot == null || VillageAiBudget.tryConsume(level, "butcher-stand:" + anchor.asLong(), REFRESH_BUDGET_PER_TICK)) {
                snapshot = buildStandSnapshot(level, anchor, gameTime);
                STAND_SNAPSHOTS.put(key, snapshot);
            }
        }
        return snapshot == null ? null : snapshot.nearestTo(villager);
    }

    private static SmokerSnapshot buildSmokerSnapshot(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius, long gameTime) {
        List<BlockPos> smokers = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            if (level.getBlockState(pos).is(Blocks.SMOKER)) {
                smokers.add(pos.immutable());
            }
        }
        return new SmokerSnapshot(List.copyOf(smokers), gameTime + SNAPSHOT_TTL_TICKS);
    }

    private static StandSnapshot buildStandSnapshot(ServerLevel level, BlockPos anchor, long gameTime) {
        List<BlockPos> standPositions = new ArrayList<>();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = anchor.relative(dir);
            if (isStandable(level, candidate)) {
                standPositions.add(candidate.immutable());
            }
        }
        return new StandSnapshot(List.copyOf(standPositions), gameTime + SNAPSHOT_TTL_TICKS);
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir()) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private record SmokerSearchKey(String dimensionId, long centerKey, int horizontalRadius, int verticalRadius) {}
    private record StandKey(String dimensionId, long anchorKey) {}

    private record SmokerSnapshot(List<BlockPos> smokers, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable BlockPos nearestTo(VillagerEntityMCA villager) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos smoker : smokers) {
                double dist = villager.distanceToSqr(smoker.getX() + 0.5, smoker.getY() + 0.5, smoker.getZ() + 0.5);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = smoker;
                }
            }
            return best;
        }
    }

    private record StandSnapshot(List<BlockPos> standPositions, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable BlockPos nearestTo(VillagerEntityMCA villager) {
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos stand : standPositions) {
                double dist = villager.distanceToSqr(stand.getX() + 0.5, stand.getY() + 0.5, stand.getZ() + 0.5);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = stand;
                }
            }
            return best;
        }
    }
}
