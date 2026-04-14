package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.compat.farming.FarmerCropCompatRegistry;
import com.aetherianartificer.townstead.compat.farming.FarmerRemovableWeedCompatRegistry;
import com.aetherianartificer.townstead.farming.CropProductResolver;
import com.aetherianartificer.townstead.farming.cellplan.PlannedCell;
import com.aetherianartificer.townstead.farming.cellplan.SeedAssignment;
import com.aetherianartificer.townstead.farming.cellplan.SoilType;
import com.aetherianartificer.townstead.hunger.farm.FarmBlueprint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
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

public final class HarvestWorkIndex {
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

    static List<BlockPos> nearbyComposters(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius) {
        ComposterKey key = new ComposterKey(level.dimension().location().toString(), villager.blockPosition().asLong(), horizontalRadius, verticalRadius);
        ComposterSnapshot snapshot = COMPOSTER_SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (snapshot == null || !snapshot.validAt(gameTime)) {
            if (snapshot == null || VillageAiBudget.tryConsume(level, "harvest-composter:" + key.centerKey() + ":" + horizontalRadius + ":" + verticalRadius, COMPOSTER_REFRESH_BUDGET_PER_TICK)) {
                snapshot = buildComposterSnapshot(level, villager.blockPosition(), horizontalRadius, verticalRadius, gameTime);
                COMPOSTER_SNAPSHOTS.put(key, snapshot);
            }
        }
        return snapshot == null ? List.of() : snapshot.byDistanceTo(villager);
    }

    static FarmSnapshot snapshot(ServerLevel level, BlockPos farmAnchor, @Nullable FarmBlueprint farmBlueprint) {
        if (farmBlueprint == null || farmBlueprint.isEmpty()) return FarmSnapshot.EMPTY;
        long planSig = farmBlueprint.cellPlan().signature();
        FarmKey key = new FarmKey(level.dimension().location().toString(), farmAnchor.asLong(), planSig);
        FarmSnapshot snapshot = FARM_SNAPSHOTS.get(key);
        long gameTime = level.getGameTime();
        if (snapshot == null || !snapshot.validAt(gameTime)) {
            if (snapshot == null || VillageAiBudget.tryConsume(level, "harvest-farm:" + farmAnchor.asLong() + ":" + planSig, FARM_REFRESH_BUDGET_PER_TICK)) {
                snapshot = buildFarmSnapshot(level, farmBlueprint, gameTime);
                FARM_SNAPSHOTS.put(key, snapshot);
            }
        }
        return snapshot == null ? FarmSnapshot.EMPTY : snapshot;
    }

    /**
     * Drop all cached farm snapshots in the given dimension. Used when a world edit
     * (till, plant, harvest) may have changed the per-cell state so the snapshot must rebuild.
     * Cheap; snapshots rebuild lazily on next {@link #snapshot} call.
     */
    public static void invalidate(ServerLevel level, BlockPos ignored) {
        if (level == null) return;
        String dimensionId = level.dimension().location().toString();
        FARM_SNAPSHOTS.keySet().removeIf(key -> key.dimensionId().equals(dimensionId));
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

    /**
     * Per-cell state machine. For each {@link PlannedCell}, compute exactly the actions that
     * cell currently needs based on live world state and the cell's assigned soil/seed.
     */
    private static FarmSnapshot buildFarmSnapshot(ServerLevel level, FarmBlueprint blueprint, long gameTime) {
        List<BlockPos> harvestTargets = new ArrayList<>();
        List<BlockPos> plantTargets = new ArrayList<>();
        List<BlockPos> tillTargets = new ArrayList<>();
        List<BlockPos> hydratedTillTargets = new ArrayList<>();
        List<BlockPos> waterTargets = new ArrayList<>();
        List<BlockPos> groomTargets = new ArrayList<>();
        Set<Long> groomSeen = new HashSet<>();

        for (PlannedCell cell : blueprint.cells()) {
            BlockPos soilPos = cell.soilPos();
            BlockPos cropPos = cell.cropPos();
            BlockState soilState = level.getBlockState(soilPos);
            BlockState cropState = level.getBlockState(cropPos);

            // WATER-painted cells: place water if missing, plant rice etc. if seed matches,
            // harvest if a crop is there. A crop already growing on the cell is sacred — never
            // destroy it with water placement even if the waterlog state got disturbed (e.g. player
            // bucketed it).
            if (cell.desiredSoil() == com.aetherianartificer.townstead.farming.cellplan.SoilType.WATER) {
                boolean hasCrop = soilState.getBlock() instanceof CropBlock
                        || soilState.getBlock() instanceof net.minecraft.world.level.block.BushBlock;
                boolean hasWater = level.getFluidState(soilPos).is(FluidTags.WATER);

                // Harvest first. For stacked water crops (FD rice: RiceBlock at soilPos, RicePaniclesBlock
                // at soilPos+1), harvest the upper block when it matures — breaking the base early
                // destroys the whole plant while the top is still growing.
                if (hasCrop) {
                    BlockState above = level.getBlockState(soilPos.above());
                    boolean upperIsCrop = above.getBlock() instanceof CropBlock || above.getBlock() instanceof net.minecraft.world.level.block.BushBlock;

                    if (upperIsCrop) {
                        if (above.getBlock() instanceof CropBlock upperCrop && upperCrop.isMaxAge(above)) {
                            harvestTargets.add(soilPos.above().immutable());
                        } else if (FarmerCropCompatRegistry.shouldPartialHarvest(above)) {
                            harvestTargets.add(soilPos.above().immutable());
                        }
                        // Upper crop not mature yet — leave the whole plant alone.
                    } else {
                        // No upper block — harvest the base if it's a vanilla CropBlock at max age,
                        // or a compat partial-harvest block. BushBlocks alone are treated as "still
                        // growing" (they normally spawn an upper block at max age).
                        if (soilState.getBlock() instanceof CropBlock crop && crop.isMaxAge(soilState)) {
                            harvestTargets.add(soilPos.immutable());
                        } else if (FarmerCropCompatRegistry.shouldPartialHarvest(soilState)) {
                            harvestTargets.add(soilPos.immutable());
                        }
                    }
                    // Crop present — don't try to place water or plant over it.
                    continue;
                }

                if (!hasWater) {
                    if (isWaterPlaceable(soilState)) {
                        waterTargets.add(soilPos.immutable());
                    } else if (com.aetherianartificer.townstead.TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                        org.slf4j.LoggerFactory.getLogger("townstead/HarvestWorkIndex").info(
                                "WATER cell {} dry but not placeable: block={}", soilPos, soilState.getBlock());
                    }
                    continue;
                }

                // Water is in place, no crop yet — try to plant the assigned seed.
                boolean seedAllowed = !SeedAssignment.NONE.equals(cell.seedAssignment())
                        && !SeedAssignment.AUTO.equals(cell.seedAssignment())
                        && cell.seedAssignment() != null;
                boolean plantable = FarmerCropCompatRegistry.isPlantableSpot(level, soilPos);
                boolean matches = seedMatchesSoil(level, cell);
                if (seedAllowed && plantable && matches) {
                    plantTargets.add(soilPos.immutable());
                } else if (com.aetherianartificer.townstead.TownsteadConfig.DEBUG_VILLAGER_AI.get() && seedAllowed) {
                    org.slf4j.LoggerFactory.getLogger("townstead/HarvestWorkIndex").info(
                            "WATER cell {} has water but no plant target: seed={}, blockHere={}, blockBelow={}, plantable={}, matches={}",
                            soilPos, cell.seedAssignment(),
                            level.getBlockState(soilPos).getBlock(),
                            level.getBlockState(soilPos.below()).getBlock(),
                            plantable, matches);
                }
                continue;
            }

            // 1. HARVEST — mature crop on this cell (vanilla) or adjacent melon/pumpkin fruit
            for (BlockPos candidate : harvestCandidatesNear(level, cropPos)) {
                BlockState candState = level.getBlockState(candidate);
                if (isHarvestTargetValid(level, candidate, candState, blueprint)) {
                    harvestTargets.add(candidate.immutable());
                }
            }

            boolean soilIsFarmland = soilState.getBlock() instanceof FarmBlock;
            boolean soilIsCompat = FarmerCropCompatRegistry.isCompatibleSoil(level, soilPos);

            // 2. TILL — current soil doesn't match what the plan wants, and the current block can be turned into the target.
            // Accepts dirt-types or plain farmland (farmland gets upgraded to rich_soil_farmland if RICH_SOIL_TILLED).
            boolean currentMatchesDesired = soilMatchesDesired(level, soilPos, soilState, soilIsFarmland, soilIsCompat, cell.desiredSoil());
            boolean currentIsReshapeable = isTillableDirt(soilState) || soilIsFarmland || soilIsCompat;
            if (!currentMatchesDesired && currentIsReshapeable && canClearTillObstruction(cropState)) {
                tillTargets.add(soilPos.immutable());
                if (hasNearbyWater(level, soilPos)) {
                    hydratedTillTargets.add(soilPos.immutable());
                }
            }

            // 3. PLANT — crop slot empty, seed not NONE, and either vanilla farmland OR a compat plantable spot (FD rice in water).
            // Also: the seed assignment (if specific) must be compatible with this cell's painted soil.
            // Wrong crops on the cell are not handled here — mature ones get picked up by the harvest
            // path above (any mature crop on a planned cell is a harvest target), after which the cell
            // goes empty and the planting path takes over. Growing crops are left alone.
            boolean seedAllowed = !SeedAssignment.NONE.equals(cell.seedAssignment());
            boolean matches = seedMatchesSoil(level, cell);
            boolean plantable = (soilIsFarmland && cropState.isAir() && level.getFluidState(cropPos).isEmpty())
                    || FarmerCropCompatRegistry.isPlantableSpot(level, cropPos);
            if (seedAllowed && plantable && matches) {
                plantTargets.add(cropPos.immutable());
            }

            // 5. GROOM — removable weeds in 1-block neighborhood of this cell
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos base = soilPos.offset(dx, 0, dz);
                    BlockPos top = base.above();
                    if (!groomSeen.add(top.asLong())) continue;
                    if (blueprint.isProtected(top)) continue;
                    if (isRemovableWeed(level.getBlockState(top))) {
                        groomTargets.add(top.immutable());
                    }
                }
            }
        }

        return new FarmSnapshot(
                List.copyOf(harvestTargets),
                List.copyOf(plantTargets),
                List.copyOf(tillTargets),
                List.copyOf(hydratedTillTargets),
                List.copyOf(waterTargets),
                List.copyOf(groomTargets),
                gameTime + FARM_TTL_TICKS
        );
    }

    /**
     * True if the current block already satisfies the cell's desired soil type.
     * Lets the state machine skip redundant tilling.
     */
    private static boolean soilMatchesDesired(ServerLevel level, BlockPos pos, BlockState soilState,
                                               boolean soilIsFarmland, boolean soilIsCompat, SoilType desired) {
        return switch (desired) {
            case FARMLAND -> soilIsFarmland
                    // Any FFB fertilized variant is "more than farmland" — don't treat those as matching plain FARMLAND.
                    && !FarmerCropCompatRegistry.isExistingSoil(SoilType.FERTILIZED_RICH, level, pos)
                    && !FarmerCropCompatRegistry.isExistingSoil(SoilType.FERTILIZED_HEALTHY, level, pos)
                    && !FarmerCropCompatRegistry.isExistingSoil(SoilType.FERTILIZED_STABLE, level, pos);
            // Tilled rich soil = compat AND farmland (FD rich_soil_farmland is a FarmBlock subclass).
            case RICH_SOIL_TILLED -> soilIsFarmland && soilIsCompat;
            // Untilled rich soil = compat AND NOT farmland (FD rich_soil is a dirt-type, not FarmBlock).
            case RICH_SOIL -> soilIsCompat && !soilIsFarmland;
            // Fertilized variants — delegate to the provider (direct block identity check).
            case FERTILIZED_RICH, FERTILIZED_HEALTHY, FERTILIZED_STABLE ->
                    FarmerCropCompatRegistry.isExistingSoil(desired, level, pos);
            default -> false;
        };
    }

    /**
     * True if the cell's assigned seed is compatible with its painted soil type.
     * AUTO / NONE / PROTECTED seeds skip the check (AUTO lets the farmer pick any compatible seed at plant time).
     */
    private static boolean seedMatchesSoil(ServerLevel level, PlannedCell cell) {
        String seed = cell.seedAssignment();
        if (seed == null || SeedAssignment.AUTO.equals(seed)) return true;
        ResourceLocation rl;
        try {
            //? if >=1.21 {
            rl = ResourceLocation.parse(seed);
            //?} else {
            /*rl = new ResourceLocation(seed);
            *///?}
        } catch (Exception e) { return true; }
        Item seedItem = BuiltInRegistries.ITEM.get(rl);
        if (seedItem == null) return true;
        java.util.Set<SoilType> compatible = CropProductResolver.get(level).getCompatibleSoils(seedItem);
        return compatible.contains(cell.desiredSoil());
    }

    private static Iterable<BlockPos> harvestCandidatesNear(ServerLevel level, BlockPos cropPos) {
        ArrayList<BlockPos> candidates = new ArrayList<>(6);
        candidates.add(cropPos);
        // Stacked crops: FD tomatoes grow a TomatoVineBlock (CropBlock) one block above the
        // BuddingTomatoBlock base. YH tea is a DoubleCropBlock with an upper half. Without scanning
        // up, the fruiting/perennial part is invisible to the farmer.
        candidates.add(cropPos.above());
        BlockState state = level.getBlockState(cropPos);
        if (state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock) {
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                candidates.add(cropPos.relative(dir));
            }
        }
        return candidates;
    }

    private static boolean isHarvestTargetValid(ServerLevel level, BlockPos pos, BlockState state, FarmBlueprint blueprint) {
        if (blueprint.isProtected(pos)) return false;
        if (state.getBlock() instanceof CropBlock crop) {
            // Walk down up to 2 blocks to find a planned soil — catches stacked crops like FD tomato
            // vines that sit one block above the budding base (which itself sits above the soil).
            if (findPlannedSoilBelow(blueprint, pos, 2) == null) return false;
            return crop.isMaxAge(state);
        }
        if (FarmerCropCompatRegistry.shouldPartialHarvest(state)) {
            return findPlannedSoilBelow(blueprint, pos, 2) != null;
        }
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
            return isPlannedOrAdjacentSoil(blueprint, pos.below()) && hasAdjacentStem(level, pos);
        }
        return false;
    }

    private static BlockPos findPlannedSoilBelow(FarmBlueprint blueprint, BlockPos pos, int maxDepth) {
        for (int dy = 1; dy <= maxDepth; dy++) {
            BlockPos candidate = pos.below(dy);
            if (blueprint.containsSoil(candidate)) return candidate;
        }
        return null;
    }

    private static boolean hasAdjacentStem(ServerLevel level, BlockPos fruitPos) {
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockState adjacent = level.getBlockState(fruitPos.relative(dir));
            if (adjacent.getBlock() instanceof StemBlock || adjacent.getBlock() instanceof AttachedStemBlock) return true;
        }
        return false;
    }

    private static boolean isWaterPlaceable(BlockState state) {
        // Player asked for water here — place it unless the block is physically impossible to remove
        // (bedrock, barriers, command blocks, etc.).
        if (state.isAir()) return true;
        // Destroy speed of -1 marks vanilla "unbreakable" blocks (bedrock, barrier, command_block, structure_block, ...).
        return state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) >= 0;
    }

    private static boolean isTillableDirt(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
    }

    private static boolean canClearTillObstruction(BlockState state) {
        if (state.isAir()) return true;
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) return false;
        if (state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) return true;
        if (state.is(net.minecraft.tags.BlockTags.LOGS)) return true;
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

    private static boolean isPlannedOrAdjacentSoil(FarmBlueprint blueprint, BlockPos pos) {
        if (blueprint.containsSoil(pos)) return true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (blueprint.containsSoil(pos.offset(dx, 0, dz))) return true;
            }
        }
        return false;
    }

    private record ComposterKey(String dimensionId, long centerKey, int horizontalRadius, int verticalRadius) {}

    private record FarmKey(String dimensionId, long anchorKey, long cellPlanSignature) {}

    private record ComposterSnapshot(List<BlockPos> composters, long expiresAt) {
        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable BlockPos nearestTo(VillagerEntityMCA villager) {
            return HarvestWorkIndex.nearestTo(villager, composters, pos -> true);
        }

        List<BlockPos> byDistanceTo(VillagerEntityMCA villager) {
            ArrayList<BlockPos> ordered = new ArrayList<>(composters);
            ordered.sort((a, b) -> Double.compare(
                    villager.distanceToSqr(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5),
                    villager.distanceToSqr(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5)
            ));
            return List.copyOf(ordered);
        }
    }

    static final class FarmSnapshot {
        static final FarmSnapshot EMPTY = new FarmSnapshot(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Long.MIN_VALUE);

        private final List<BlockPos> harvestTargets;
        private final List<BlockPos> plantTargets;
        private final List<BlockPos> tillTargets;
        private final List<BlockPos> hydratedTillTargets;
        private final List<BlockPos> waterTargets;
        private final List<BlockPos> groomTargets;
        private final long expiresAt;

        private FarmSnapshot(List<BlockPos> harvestTargets, List<BlockPos> plantTargets,
                             List<BlockPos> tillTargets, List<BlockPos> hydratedTillTargets,
                             List<BlockPos> waterTargets, List<BlockPos> groomTargets, long expiresAt) {
            this.harvestTargets = harvestTargets;
            this.plantTargets = plantTargets;
            this.tillTargets = tillTargets;
            this.hydratedTillTargets = hydratedTillTargets;
            this.waterTargets = waterTargets;
            this.groomTargets = groomTargets;
            this.expiresAt = expiresAt;
        }

        boolean validAt(long gameTime) {
            return gameTime <= expiresAt;
        }

        @Nullable BlockPos nearestHarvestTarget(VillagerEntityMCA villager, Predicate<BlockPos> filter) {
            return nearestTo(villager, harvestTargets, filter);
        }

        @Nullable BlockPos nearestPlantTarget(VillagerEntityMCA villager, Predicate<BlockPos> filter) {
            return nearestTo(villager, plantTargets, filter);
        }

        @Nullable BlockPos nearestTillTarget(VillagerEntityMCA villager, boolean preferHydrated, Predicate<BlockPos> filter) {
            BlockPos hydrated = preferHydrated ? nearestTo(villager, hydratedTillTargets, filter) : null;
            if (hydrated != null) return hydrated;
            return nearestTo(villager, tillTargets, filter);
        }

        @Nullable BlockPos nearestWaterTarget(VillagerEntityMCA villager, Predicate<BlockPos> filter) {
            return nearestTo(villager, waterTargets, filter);
        }

        boolean hasWaterTargets() {
            return !waterTargets.isEmpty();
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
