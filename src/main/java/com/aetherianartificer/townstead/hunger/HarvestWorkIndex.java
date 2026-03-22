package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.compat.farming.FarmerCropCompatRegistry;
import com.aetherianartificer.townstead.compat.farming.FarmerRemovableWeedCompatRegistry;
import com.aetherianartificer.townstead.hunger.farm.FarmBlueprint;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class HarvestWorkIndex {
    private static final long COMPOSTER_TTL_TICKS = 40L;
    private static final long FARM_TTL_TICKS = 10L;
    private static final int COMPOSTER_REFRESH_BUDGET_PER_TICK = 2;
    private static final int FARM_REFRESH_BUDGET_PER_TICK = 3;
    private static final Map<ComposterKey, ComposterSnapshot> COMPOSTER_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<FarmKey, FarmSnapshot> FARM_SNAPSHOTS = new ConcurrentHashMap<>();

    private HarvestWorkIndex() {}

    static @Nullable BlockPos nearestComposter(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius) {
        ComposterKey key = new ComposterKey(level.dimension().location().toString(), villager.blockPosition().asLong(), horizontalRadius, verticalRadius);
        ComposterSnapshot snapshot = COMPOSTER_SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (snapshot == null || !snapshot.validAt(gameTime)) {
            if (snapshot == null || VillageAiBudget.tryConsume(level, "harvest-composter:" + key.centerKey() + ":" + horizontalRadius + ":" + verticalRadius, COMPOSTER_REFRESH_BUDGET_PER_TICK)) {
                snapshot = buildComposterSnapshot(level, villager.blockPosition(), horizontalRadius, verticalRadius, gameTime);
                COMPOSTER_SNAPSHOTS.put(key, snapshot);
            }
        }
        return snapshot == null ? null : snapshot.nearestTo(villager);
    }

    static FarmSnapshot snapshot(ServerLevel level, BlockPos farmAnchor, @Nullable FarmBlueprint farmBlueprint, int farmRadius, int verticalRadius, int groomRadius) {
        FarmKey key = new FarmKey(
                level.dimension().location().toString(),
                farmAnchor.asLong(),
                farmRadius,
                verticalRadius,
                groomRadius,
                blueprintSignature(farmBlueprint)
        );
        FarmSnapshot snapshot = FARM_SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (snapshot == null || !snapshot.validAt(gameTime)) {
            if (snapshot == null || VillageAiBudget.tryConsume(level, "harvest-farm:" + farmAnchor.asLong() + ":" + key.blueprintSignature(), FARM_REFRESH_BUDGET_PER_TICK)) {
                snapshot = buildFarmSnapshot(level, farmAnchor, farmBlueprint, farmRadius, verticalRadius, groomRadius, gameTime);
                FARM_SNAPSHOTS.put(key, snapshot);
            }
        }
        return snapshot == null ? FarmSnapshot.EMPTY : snapshot;
    }

    static void invalidate(ServerLevel level, BlockPos changedPos) {
        if (level == null || changedPos == null) return;
        String dimensionId = level.dimension().location().toString();
        FARM_SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId) && contains(key, changedPos));
    }

    private static ComposterSnapshot buildComposterSnapshot(ServerLevel level, BlockPos center, int horizontalRadius, int verticalRadius, long gameTime) {
        List<BlockPos> composters = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            if (level.getBlockState(pos).getBlock() instanceof ComposterBlock) {
                composters.add(pos.immutable());
            }
        }
        return new ComposterSnapshot(List.copyOf(composters), gameTime + COMPOSTER_TTL_TICKS);
    }

    private static FarmSnapshot buildFarmSnapshot(ServerLevel level, BlockPos farmAnchor, @Nullable FarmBlueprint farmBlueprint,
                                                  int farmRadius, int verticalRadius, int groomRadius, long gameTime) {
        List<BlockPos> soilCells = iterateSoilCells(level, farmAnchor, farmBlueprint, farmRadius, verticalRadius);
        List<BlockPos> harvestTargets = new ArrayList<>();
        List<BlockPos> plantTargets = new ArrayList<>();
        List<BlockPos> compatPlantTargets = new ArrayList<>();
        List<BlockPos> tillTargets = new ArrayList<>();
        List<BlockPos> hydratedTillTargets = new ArrayList<>();
        List<BlockPos> groomTargets = new ArrayList<>();
        Set<Long> harvestSeen = new HashSet<>();
        Set<Long> groomSeen = new HashSet<>();

        boolean includeCompatPlantTargets = FarmerCropCompatRegistry.hasAnyLoadedProvider();

        for (BlockPos soilPos : soilCells) {
            BlockState soilState = level.getBlockState(soilPos);
            BlockPos cropPos = soilPos.above();
            for (BlockPos candidate : harvestCandidatesNear(level, cropPos)) {
                if (!harvestSeen.add(candidate.asLong())) continue;
                BlockState state = level.getBlockState(candidate);
                if (isHarvestTargetValid(level, candidate, state, farmAnchor, farmBlueprint, farmRadius, verticalRadius)) {
                    harvestTargets.add(candidate.immutable());
                }
            }

            if (soilState.getBlock() instanceof FarmBlock && level.getBlockState(cropPos).isAir()) {
                plantTargets.add(cropPos.immutable());
            }

            if (includeCompatPlantTargets && FarmerCropCompatRegistry.isPlantableSpot(level, cropPos)) {
                compatPlantTargets.add(cropPos.immutable());
            }

            if (isTillable(level, soilPos, farmAnchor, farmBlueprint, farmRadius, verticalRadius)) {
                tillTargets.add(soilPos.immutable());
                if (hasNearbyWater(level, soilPos)) {
                    hydratedTillTargets.add(soilPos.immutable());
                }
            }

            for (int dx = -groomRadius; dx <= groomRadius; dx++) {
                for (int dz = -groomRadius; dz <= groomRadius; dz++) {
                    BlockPos base = soilPos.offset(dx, 0, dz);
                    if (!isInsideFarmRadius(farmAnchor, farmRadius, verticalRadius, base)) continue;
                    if (!isPlannedOrAdjacentSoil(farmBlueprint, farmAnchor, farmRadius, verticalRadius, base)) continue;
                    BlockPos top = base.above();
                    if (!groomSeen.add(top.asLong())) continue;
                    if (isRemovableWeed(level.getBlockState(top))) {
                        groomTargets.add(top.immutable());
                    }
                }
            }
        }

        return new FarmSnapshot(
                List.copyOf(harvestTargets),
                List.copyOf(plantTargets),
                List.copyOf(compatPlantTargets),
                List.copyOf(tillTargets),
                List.copyOf(hydratedTillTargets),
                List.copyOf(groomTargets),
                gameTime + FARM_TTL_TICKS
        );
    }

    private static List<BlockPos> iterateSoilCells(ServerLevel level, BlockPos farmAnchor, @Nullable FarmBlueprint farmBlueprint,
                                                   int farmRadius, int verticalRadius) {
        if (farmBlueprint != null && !farmBlueprint.isEmpty()) {
            return List.copyOf(farmBlueprint.soilCells());
        }

        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                farmAnchor.offset(-farmRadius, -verticalRadius, -farmRadius),
                farmAnchor.offset(farmRadius, verticalRadius, farmRadius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FarmBlock || level.getBlockState(pos.above()).getBlock() instanceof CropBlock) {
                cells.add(pos.immutable());
            }
        }
        return cells;
    }

    private static Iterable<BlockPos> harvestCandidatesNear(ServerLevel level, BlockPos cropPos) {
        ArrayList<BlockPos> candidates = new ArrayList<>(5);
        candidates.add(cropPos);
        BlockState state = level.getBlockState(cropPos);
        if (state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                candidates.add(cropPos.relative(dir));
            }
        }
        return candidates;
    }

    private static boolean isHarvestTargetValid(ServerLevel level, BlockPos pos, BlockState state, BlockPos farmAnchor,
                                                @Nullable FarmBlueprint farmBlueprint, int farmRadius, int verticalRadius) {
        if (!isInsideFarmRadius(farmAnchor, farmRadius, verticalRadius, pos)) return false;
        if (state.getBlock() instanceof CropBlock crop) {
            return isPlannedCropPos(farmBlueprint, farmAnchor, farmRadius, verticalRadius, pos) && crop.isMaxAge(state);
        }
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
            return isPlannedOrAdjacentSoil(farmBlueprint, farmAnchor, farmRadius, verticalRadius, pos.below()) && hasAdjacentStem(level, pos);
        }
        return false;
    }

    private static boolean hasAdjacentStem(ServerLevel level, BlockPos fruitPos) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockState adjacent = level.getBlockState(fruitPos.relative(dir));
            if (adjacent.getBlock() instanceof StemBlock || adjacent.getBlock() instanceof AttachedStemBlock) return true;
        }
        return false;
    }

    private static boolean isTillable(ServerLevel level, BlockPos pos, BlockPos farmAnchor, @Nullable FarmBlueprint farmBlueprint,
                                      int farmRadius, int verticalRadius) {
        if (!isPlannedSoil(farmBlueprint, farmAnchor, farmRadius, verticalRadius, pos)) return false;
        BlockState above = level.getBlockState(pos.above());
        if (!above.isAir() && !canClearTillObstruction(above)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FarmBlock) return false;
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
    }

    private static boolean canClearTillObstruction(BlockState state) {
        if (state.isAir()) return true;
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) return false;
        return isRemovableWeed(state);
    }

    private static boolean hasNearbyWater(ServerLevel level, BlockPos soilPos) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if ((dx * dx + dz * dz) > 16) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = soilPos.offset(dx, dy, dz);
                    if (level.getFluidState(p).is(FluidTags.WATER)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isRemovableWeed(BlockState state) {
        if (state.isAir()) return false;
        //? if >=1.21 {
        return state.is(Blocks.SHORT_GRASS)
        //?} else {
        /*return state.is(Blocks.GRASS)
        *///?}
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW)
                || FarmerRemovableWeedCompatRegistry.isRemovableWeed(state);
    }

    private static boolean isPlannedSoil(@Nullable FarmBlueprint farmBlueprint, BlockPos farmAnchor, int farmRadius, int verticalRadius, BlockPos pos) {
        if (!isInsideFarmRadius(farmAnchor, farmRadius, verticalRadius, pos)) return false;
        return farmBlueprint != null && !farmBlueprint.isEmpty() && farmBlueprint.containsSoil(pos);
    }

    private static boolean isPlannedOrAdjacentSoil(@Nullable FarmBlueprint farmBlueprint, BlockPos farmAnchor, int farmRadius, int verticalRadius, BlockPos pos) {
        if (isPlannedSoil(farmBlueprint, farmAnchor, farmRadius, verticalRadius, pos)) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (isPlannedSoil(farmBlueprint, farmAnchor, farmRadius, verticalRadius, pos.offset(dx, 0, dz))) return true;
            }
        }
        return false;
    }

    private static boolean isPlannedCropPos(@Nullable FarmBlueprint farmBlueprint, BlockPos farmAnchor, int farmRadius, int verticalRadius, BlockPos cropPos) {
        return isPlannedSoil(farmBlueprint, farmAnchor, farmRadius, verticalRadius, cropPos.below());
    }

    private static boolean isInsideFarmRadius(BlockPos farmAnchor, int farmRadius, int verticalRadius, BlockPos pos) {
        int dx = Math.abs(pos.getX() - farmAnchor.getX());
        int dz = Math.abs(pos.getZ() - farmAnchor.getZ());
        int dy = Math.abs(pos.getY() - farmAnchor.getY());
        return dx <= farmRadius && dz <= farmRadius && dy <= verticalRadius;
    }

    private static long blueprintSignature(@Nullable FarmBlueprint farmBlueprint) {
        if (farmBlueprint == null || farmBlueprint.isEmpty()) return 0L;
        long signature = farmBlueprint.anchor().asLong();
        signature = 31L * signature + farmBlueprint.plannerType().hashCode();
        signature = 31L * signature + farmBlueprint.soilCells().size();
        signature = 31L * signature + farmBlueprint.soilCells().hashCode();
        return signature;
    }

    private static boolean contains(FarmKey key, BlockPos pos) {
        BlockPos farmAnchor = BlockPos.of(key.anchorKey());
        return isInsideFarmRadius(farmAnchor, key.farmRadius(), key.verticalRadius(), pos);
    }

    private record ComposterKey(String dimensionId, long centerKey, int horizontalRadius, int verticalRadius) {}

    private record FarmKey(String dimensionId, long anchorKey, int farmRadius, int verticalRadius, int groomRadius, long blueprintSignature) {}

    private record ComposterSnapshot(List<BlockPos> composters, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable BlockPos nearestTo(VillagerEntityMCA villager) {
            return HarvestWorkIndex.nearestTo(villager, composters, pos -> true);
        }
    }

    static final class FarmSnapshot {
        static final FarmSnapshot EMPTY = new FarmSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Long.MIN_VALUE);

        private final List<BlockPos> harvestTargets;
        private final List<BlockPos> plantTargets;
        private final List<BlockPos> compatPlantTargets;
        private final List<BlockPos> tillTargets;
        private final List<BlockPos> hydratedTillTargets;
        private final List<BlockPos> groomTargets;
        private final long expiresAt;

        private FarmSnapshot(List<BlockPos> harvestTargets, List<BlockPos> plantTargets, List<BlockPos> compatPlantTargets,
                             List<BlockPos> tillTargets, List<BlockPos> hydratedTillTargets, List<BlockPos> groomTargets, long expiresAt) {
            this.harvestTargets = harvestTargets;
            this.plantTargets = plantTargets;
            this.compatPlantTargets = compatPlantTargets;
            this.tillTargets = tillTargets;
            this.hydratedTillTargets = hydratedTillTargets;
            this.groomTargets = groomTargets;
            this.expiresAt = expiresAt;
        }

        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable BlockPos nearestHarvestTarget(VillagerEntityMCA villager, Predicate<BlockPos> filter) {
            return nearestTo(villager, harvestTargets, filter);
        }

        @Nullable BlockPos nearestPlantTarget(VillagerEntityMCA villager, boolean includeCompatTargets, Predicate<BlockPos> filter) {
            BlockPos best = nearestTo(villager, plantTargets, filter);
            if (!includeCompatTargets) return best;
            BlockPos compat = nearestTo(villager, compatPlantTargets, filter);
            if (best == null) return compat;
            if (compat == null) return best;
            return villager.distanceToSqr(best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5)
                    <= villager.distanceToSqr(compat.getX() + 0.5, compat.getY() + 0.5, compat.getZ() + 0.5)
                    ? best : compat;
        }

        @Nullable BlockPos nearestTillTarget(VillagerEntityMCA villager, boolean preferHydrated, Predicate<BlockPos> filter) {
            BlockPos hydrated = preferHydrated ? nearestTo(villager, hydratedTillTargets, filter) : null;
            if (hydrated != null) return hydrated;
            return nearestTo(villager, tillTargets, filter);
        }

        @Nullable BlockPos nearestGroomTarget(VillagerEntityMCA villager, Predicate<BlockPos> filter) {
            return nearestTo(villager, groomTargets, filter);
        }
    }

    private static @Nullable BlockPos nearestTo(VillagerEntityMCA villager, List<BlockPos> candidates, Predicate<BlockPos> filter) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (!filter.test(candidate)) continue;
            double dist = villager.distanceToSqr(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }
}
