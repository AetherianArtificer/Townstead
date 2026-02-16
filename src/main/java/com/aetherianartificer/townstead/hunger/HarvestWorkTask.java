package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.farming.FarmingPolicyData;
import com.aetherianartificer.townstead.hunger.farm.FarmBlueprint;
import com.aetherianartificer.townstead.hunger.farm.FarmPlanner;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HarvestWorkTask extends Behavior<VillagerEntityMCA> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/HarvestWorkTask");
    private static final int ANCHOR_SEARCH_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 3;
    private static final float WALK_SPEED_HARVEST = 0.66f;
    private static final float WALK_SPEED_NORMAL = 0.52f;
    private static final int CLOSE_ENOUGH = 1;
    private static final int MAX_DURATION = 1200;
    private static final int NATURAL_RETURN_PADDING = 8;
    private static final int TARGET_SCAN_INTERVAL = 20;
    private static final int TARGET_STUCK_TICKS = 60;
    private static final int TARGET_BLACKLIST_TICKS = 200;
    private static final int REQUEST_RANGE = 24;
    private static final int BLUEPRINT_REPLAN_INTERVAL = 1200;
    private static final String DEFAULT_PATTERN_ID = "starter_rows";
    private static final int STOCK_MIN_INTERVAL_TICKS = 400;

    private enum ActionType { NONE, RETURN, HARVEST, PLANT, TILL, FETCH_WATER, PLACE_WATER, STOCK }

    private ActionType actionType = ActionType.NONE;
    private BlockPos targetPos;
    private BlockPos farmAnchor;
    private FarmBlueprint farmBlueprint;
    private int actionCooldown;
    private long nextAcquireTick;
    private long nextTargetScanTick;
    private long nextBlueprintPlanTick;
    private long lastStockTick = Long.MIN_VALUE;

    private BlockPos cachedHarvestTarget;
    private BlockPos cachedPlantTarget;
    private BlockPos cachedTillTarget;
    private boolean cachedHasHoe;
    private boolean cachedHasSeed;
    private boolean cachedHasWaterBucket;
    private int cachedSeedCount;
    private long cachedInventoryTick;
    private long waterPlacementDay = Long.MIN_VALUE;
    private int waterPlacementsToday = 0;

    private long currentTargetKey = Long.MIN_VALUE;
    private double lastTargetDistSq = Double.MAX_VALUE;
    private int targetStuckTicks;

    private final Map<Long, Long> recentlyWorkedCells = new HashMap<>();
    private final Map<Long, Integer> targetRetries = new HashMap<>();
    private final Map<Long, Long> targetBlacklistUntil = new HashMap<>();

    private HungerData.FarmBlockedReason blockedReason = HungerData.FarmBlockedReason.NONE;
    private long nextRequestTick;

    public HarvestWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.ENABLE_FARM_ASSIST.get()) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (villager.getVillagerData().getProfession() != VillagerProfession.FARMER) return false;
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        if (townstead$getCurrentScheduleActivity(villager) != Activity.WORK) return false;
        BlockPos anchor = townstead$findNearestComposter(level, villager);
        return anchor != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        actionType = ActionType.NONE;
        targetPos = null;
        farmAnchor = townstead$findNearestComposter(level, villager);
        actionCooldown = 0;
        if (nextAcquireTick < gameTime) nextAcquireTick = 0;
        nextTargetScanTick = 0;
        nextBlueprintPlanTick = 0;
        currentTargetKey = Long.MIN_VALUE;
        lastTargetDistSq = Double.MAX_VALUE;
        targetStuckTicks = 0;
        cachedInventoryTick = -1;
        nextRequestTick = 0;
        farmBlueprint = null;
        townstead$refreshBlueprintIfNeeded(level, gameTime, true);
        townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
        townstead$acquireTarget(level, villager, gameTime, true);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (farmAnchor == null) {
            farmAnchor = townstead$findNearestComposter(level, villager);
            if (farmAnchor == null) {
                townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NO_VALID_TARGET);
                return;
            }
            nextBlueprintPlanTick = 0;
        }
        townstead$refreshBlueprintIfNeeded(level, gameTime, false);

        if (actionCooldown > 0) {
            actionCooldown--;
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }
        if (gameTime < nextAcquireTick) {
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }

        if (targetPos == null || !townstead$isTargetStillValid(level, villager, gameTime)) {
            townstead$acquireTarget(level, villager, gameTime, true);
            if (targetPos == null) {
                townstead$maybeAnnounceRequest(level, villager, gameTime);
                return;
            }
        }

        float walkSpeed = actionType == ActionType.HARVEST ? WALK_SPEED_HARVEST : WALK_SPEED_NORMAL;
        BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, walkSpeed, CLOSE_ENOUGH);
        double distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq > (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
            townstead$trackPathProgress(level, villager, gameTime, distSq);
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }

        townstead$resetPathTracking();
        switch (actionType) {
            case RETURN -> {}
            case HARVEST -> townstead$doHarvest(level, villager, gameTime);
            case PLANT -> townstead$doPlant(level, villager, targetPos, gameTime);
            case TILL -> townstead$doTill(level, villager, targetPos, gameTime);
            case FETCH_WATER -> townstead$doFetchWater(level, villager, targetPos);
            case PLACE_WATER -> townstead$doPlaceWater(level, villager, targetPos, gameTime);
            case STOCK -> {
                if (townstead$doStock(level, villager, false)) {
                    lastStockTick = gameTime;
                }
            }
            default -> {}
        }

        actionCooldown = 10;
        townstead$clearTargetRetry(targetPos);
        townstead$acquireTarget(level, villager, gameTime, false);
        townstead$maybeAnnounceRequest(level, villager, gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (farmAnchor != null
                && TownsteadConfig.ENABLE_CONTAINER_SOURCING.get()
                && townstead$getCurrentScheduleActivity(villager) != Activity.WORK) {
            townstead$doStock(level, villager, true);
        }
        actionType = ActionType.NONE;
        targetPos = null;
        farmAnchor = null;
        actionCooldown = 0;
        nextAcquireTick = 0;
        nextTargetScanTick = 0;
        nextBlueprintPlanTick = 0;
        cachedInventoryTick = -1;
        nextRequestTick = 0;
        farmBlueprint = null;
        waterPlacementDay = Long.MIN_VALUE;
        waterPlacementsToday = 0;
        recentlyWorkedCells.clear();
        targetRetries.clear();
        targetBlacklistUntil.clear();
        townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
    }

    private void townstead$acquireTarget(ServerLevel level, VillagerEntityMCA villager, long gameTime, boolean forceScan) {
        if (farmAnchor == null) {
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NO_VALID_TARGET);
            return;
        }

        townstead$restockBasics(level, villager);
        townstead$refreshBlueprintIfNeeded(level, gameTime, false);
        townstead$refreshInventoryCache(villager, gameTime);
        if (forceScan || gameTime >= nextTargetScanTick) {
            townstead$refreshTargetCache(level, villager, gameTime);
            nextTargetScanTick = gameTime + TARGET_SCAN_INTERVAL;
        }

        int farmRadius = townstead$farmRadius();
        double returnDistSq = villager.distanceToSqr(farmAnchor.getX() + 0.5, farmAnchor.getY() + 0.5, farmAnchor.getZ() + 0.5);
        if (returnDistSq > (double) (farmRadius + NATURAL_RETURN_PADDING) * (farmRadius + NATURAL_RETURN_PADDING)) {
            actionType = ActionType.RETURN;
            targetPos = farmAnchor;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.OUT_OF_SCOPE);
            return;
        }

        if (cachedHarvestTarget != null) {
            actionType = ActionType.HARVEST;
            targetPos = cachedHarvestTarget;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
            return;
        }

        if (cachedHasSeed && cachedPlantTarget != null) {
            actionType = ActionType.PLANT;
            targetPos = cachedPlantTarget;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
            return;
        }

        if (!cachedHasSeed) {
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NO_SEEDS);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks();
            return;
        }

        if (!cachedHasHoe) {
            actionType = ActionType.NONE;
            targetPos = null;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NO_TOOL);
            nextAcquireTick = gameTime + townstead$idleBackoffTicks();
            return;
        }

        if (townstead$needsIrrigation(level, gameTime) && townstead$canAttemptWaterPlacement(level, gameTime)) {
            if (!cachedHasWaterBucket) {
                BlockPos waterSource = townstead$findNearestWaterSource(level, villager, gameTime);
                if (waterSource != null && townstead$findEmptyBucketSlot(villager.getInventory()) >= 0) {
                    actionType = ActionType.FETCH_WATER;
                    targetPos = waterSource;
                    townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
                    return;
                }
            } else {
                BlockPos waterPos = townstead$findNearestWaterPlacementSpot(level, villager, gameTime);
                if (waterPos != null) {
                    actionType = ActionType.PLACE_WATER;
                    targetPos = waterPos;
                    townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
                    return;
                }
            }
        }

        if (cachedSeedCount >= townstead$seedReserve() && cachedTillTarget != null) {
            actionType = ActionType.TILL;
            targetPos = cachedTillTarget;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
            return;
        }

        if (townstead$isInventoryMostlyFull(villager.getInventory())
                || (townstead$hasStockableOutput(villager.getInventory()) && (gameTime - lastStockTick) >= STOCK_MIN_INTERVAL_TICKS)) {
            actionType = ActionType.STOCK;
            targetPos = farmAnchor;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
            return;
        }

        actionType = ActionType.NONE;
        targetPos = null;
        townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NO_VALID_TARGET);
        nextAcquireTick = gameTime + townstead$idleBackoffTicks();
    }

    private void townstead$refreshInventoryCache(VillagerEntityMCA villager, long gameTime) {
        if (cachedInventoryTick == gameTime) return;
        SimpleContainer inv = villager.getInventory();
        cachedHasHoe = townstead$hasHoe(inv);
        cachedSeedCount = townstead$countSeeds(inv);
        cachedHasSeed = cachedSeedCount > 0;
        cachedHasWaterBucket = townstead$findWaterBucketSlot(inv) >= 0;
        cachedInventoryTick = gameTime;
    }

    private void townstead$refreshTargetCache(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        cachedHarvestTarget = townstead$findNearestMatureCrop(level, villager, gameTime);
        cachedPlantTarget = townstead$findNearestPlantSpot(level, villager, gameTime);
        cachedTillTarget = townstead$findNearestTillSpot(level, villager, gameTime);
    }

    private void townstead$trackPathProgress(ServerLevel level, VillagerEntityMCA villager, long gameTime, double distSq) {
        long key = targetPos.asLong();
        if (currentTargetKey != key) {
            currentTargetKey = key;
            lastTargetDistSq = distSq;
            targetStuckTicks = 0;
            return;
        }

        if (distSq >= (lastTargetDistSq - 0.05d)) {
            targetStuckTicks++;
        } else {
            targetStuckTicks = 0;
            lastTargetDistSq = distSq;
        }

        if (targetStuckTicks < TARGET_STUCK_TICKS) return;
        int retry = targetRetries.getOrDefault(key, 0) + 1;
        if (retry >= townstead$pathfailMaxRetries()) {
            targetRetries.remove(key);
            targetBlacklistUntil.put(key, gameTime + TARGET_BLACKLIST_TICKS);
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.UNREACHABLE);
        } else {
            targetRetries.put(key, retry);
        }
        actionType = ActionType.NONE;
        targetPos = null;
        nextAcquireTick = gameTime + townstead$idleBackoffTicks();
        townstead$resetPathTracking();
    }

    private void townstead$resetPathTracking() {
        currentTargetKey = Long.MIN_VALUE;
        lastTargetDistSq = Double.MAX_VALUE;
        targetStuckTicks = 0;
    }

    private void townstead$clearTargetRetry(BlockPos pos) {
        if (pos == null) return;
        long key = pos.asLong();
        targetRetries.remove(key);
        targetBlacklistUntil.remove(key);
    }

    private boolean townstead$isTargetStillValid(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null || farmAnchor == null) return false;
        if (townstead$isBlacklisted(targetPos, gameTime)) return false;

        BlockState state = level.getBlockState(targetPos);
        return switch (actionType) {
            case RETURN -> townstead$isInsideFarmRadius(targetPos);
            case HARVEST -> townstead$isPlannedCropPos(targetPos)
                    && state.getBlock() instanceof CropBlock crop
                    && crop.isMaxAge(state);
            case PLANT -> townstead$isPlannedCropPos(targetPos)
                    && state.isAir()
                    && level.getBlockState(targetPos.below()).getBlock() instanceof FarmBlock;
            case TILL -> townstead$isPlannedSoil(targetPos) && townstead$isTillable(level, targetPos, gameTime);
            case FETCH_WATER -> level.getFluidState(targetPos).is(FluidTags.WATER)
                    && townstead$findEmptyBucketSlot(villager.getInventory()) >= 0;
            case PLACE_WATER -> townstead$canPlaceWaterAt(level, targetPos) && townstead$hasWaterPlacementBudget(level.getGameTime());
            case STOCK -> townstead$isInsideFarmRadius(targetPos);
            default -> false;
        };
    }

    private BlockPos townstead$findNearestMatureCrop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos soilPos : townstead$iterateSoilCells(level)) {
            BlockPos cropPos = soilPos.above();
            if (townstead$isBlacklisted(cropPos, gameTime)) continue;
            BlockState state = level.getBlockState(cropPos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;
            double dist = villager.distanceToSqr(cropPos.getX() + 0.5, cropPos.getY() + 0.5, cropPos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = cropPos.immutable();
            }
        }
        return bestPos;
    }

    private BlockPos townstead$findNearestPlantSpot(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos soilPos : townstead$iterateSoilCells(level)) {
            if (!(level.getBlockState(soilPos).getBlock() instanceof FarmBlock)) continue;
            BlockPos above = soilPos.above();
            if (townstead$isBlacklisted(above, gameTime)) continue;
            if (!level.getBlockState(above).isAir()) continue;
            double dist = villager.distanceToSqr(above.getX() + 0.5, above.getY() + 0.5, above.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = above.immutable();
            }
        }
        return bestPos;
    }

    private BlockPos townstead$findNearestTillSpot(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos bestHydrated = null;
        double bestHydratedDist = Double.MAX_VALUE;
        BlockPos bestAny = null;
        double bestAnyDist = Double.MAX_VALUE;

        for (BlockPos soilPos : townstead$iterateSoilCells(level)) {
            if (townstead$isBlacklisted(soilPos, gameTime)) continue;
            if (!townstead$isTillable(level, soilPos, gameTime)) continue;
            double dist = villager.distanceToSqr(soilPos.getX() + 0.5, soilPos.getY() + 0.5, soilPos.getZ() + 0.5);
            if (dist < bestAnyDist) {
                bestAnyDist = dist;
                bestAny = soilPos.immutable();
            }
            if (townstead$hasNearbyWater(level, soilPos) && dist < bestHydratedDist) {
                bestHydratedDist = dist;
                bestHydrated = soilPos.immutable();
            }
        }
        if (bestHydrated != null) return bestHydrated;
        return bestAny;
    }

    private void townstead$doHarvest(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetPos == null) return;
        BlockState state = level.getBlockState(targetPos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;

        List<ItemStack> drops = CropBlock.getDrops(state, level, targetPos, null);
        level.destroyBlock(targetPos, false, villager);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) villager.getInventory().addItem(drop);
        }
        villager.swing(villager.getDominantHand());
        townstead$markWorked(targetPos, gameTime);

        if (townstead$findSeedSlot(villager.getInventory()) >= 0) {
            townstead$doPlant(level, villager, targetPos, gameTime);
        }
    }

    private void townstead$doPlant(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, long gameTime) {
        int slot = townstead$findSeedSlot(villager.getInventory());
        if (slot < 0) return;
        ItemStack seed = villager.getInventory().getItem(slot);
        if (!(seed.getItem() instanceof BlockItem blockItem)) {
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.UNSUPPORTED_CROP);
            return;
        }
        BlockState place = blockItem.getBlock().defaultBlockState();
        if (!place.canSurvive(level, pos)) return;
        if (!level.getBlockState(pos).isAir()) return;
        if (level.setBlock(pos, place, 3)) {
            seed.shrink(1);
            villager.swing(villager.getDominantHand());
            townstead$markWorked(pos, gameTime);
        }
    }

    private void townstead$doTill(ServerLevel level, VillagerEntityMCA villager, BlockPos soilPos, long gameTime) {
        if (!townstead$hasHoe(villager.getInventory())) return;
        if (!townstead$isTillable(level, soilPos, gameTime)) return;
        BlockPos abovePos = soilPos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (!aboveState.isAir()) {
            if (!townstead$canClearTillObstruction(aboveState)) return;
            level.destroyBlock(abovePos, false, villager);
            if (!level.getBlockState(abovePos).isAir()) return;
        }
        if (level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState(), 3)) {
            villager.swing(villager.getDominantHand());
            townstead$markWorked(soilPos, gameTime);
        }
    }

    private void townstead$doPlaceWater(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, long gameTime) {
        if (!townstead$canAttemptWaterPlacement(level, gameTime)) return;
        if (!townstead$canPlaceWaterAt(level, pos)) return;
        int slot = townstead$findWaterBucketSlot(villager.getInventory());
        if (slot < 0) return;

        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        if (!aboveState.isAir()) {
            if (!townstead$canClearWaterPlacementObstruction(aboveState)) return;
            level.destroyBlock(above, false, villager);
            if (!level.getBlockState(above).isAir()) return;
        }

        if (!level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3)) return;

        ItemStack bucket = villager.getInventory().getItem(slot);
        bucket.shrink(1);
        villager.getInventory().addItem(new ItemStack(Items.BUCKET));
        villager.swing(villager.getDominantHand());
        townstead$recordWaterPlacement(gameTime);
        townstead$markWorked(pos, gameTime);
        cachedInventoryTick = -1;
    }

    private void townstead$doFetchWater(ServerLevel level, VillagerEntityMCA villager, BlockPos sourcePos) {
        if (!level.getFluidState(sourcePos).is(FluidTags.WATER)) return;
        int slot = townstead$findEmptyBucketSlot(villager.getInventory());
        if (slot < 0) return;
        ItemStack bucket = villager.getInventory().getItem(slot);
        if (!bucket.is(Items.BUCKET)) return;
        bucket.shrink(1);
        villager.getInventory().addItem(new ItemStack(Items.WATER_BUCKET));
        villager.swing(villager.getDominantHand());
        cachedInventoryTick = -1;
    }

    private void townstead$markWorked(BlockPos pos, long gameTime) {
        recentlyWorkedCells.put(pos.asLong(), gameTime);
    }

    private boolean townstead$isRecentlyWorked(BlockPos pos, long gameTime) {
        Long last = recentlyWorkedCells.get(pos.asLong());
        if (last == null) return false;
        return (gameTime - last) < townstead$cellCooldownTicks();
    }

    private boolean townstead$isBlacklisted(BlockPos pos, long gameTime) {
        Long until = targetBlacklistUntil.get(pos.asLong());
        if (until == null) return false;
        if (gameTime >= until) {
            targetBlacklistUntil.remove(pos.asLong());
            return false;
        }
        return true;
    }

    private boolean townstead$doStock(ServerLevel level, VillagerEntityMCA villager, boolean endOfWork) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || farmAnchor == null) return false;
        SimpleContainer inv = villager.getInventory();
        int keepFood = townstead$findBestFoodSlot(inv);
        boolean movedAny = false;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!endOfWork && i == keepFood) continue;
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (!endOfWork) {
                if (stack.getItem() instanceof HoeItem) continue;
                if (townstead$isSeed(stack)) continue;
            }

            ItemStack moving = stack.copy();
            boolean stored = NearbyItemSources.insertIntoNearbyStorage(level, villager, moving, townstead$farmRadius(), VERTICAL_RADIUS, farmAnchor);
            if (!stored && moving.getCount() == stack.getCount()) continue;
            stack.setCount(moving.getCount());
            movedAny = true;
        }
        return movedAny;
    }

    private int townstead$findSeedSlot(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (townstead$isSeed(inv.getItem(i))) return i;
        }
        return -1;
    }

    private int townstead$findWaterBucketSlot(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.WATER_BUCKET)) return i;
        }
        return -1;
    }

    private int townstead$findEmptyBucketSlot(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.BUCKET)) return i;
        }
        return -1;
    }

    private int townstead$countSeeds(SimpleContainer inv) {
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (townstead$isSeed(stack)) count += stack.getCount();
        }
        return count;
    }

    private boolean townstead$isSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)) return true;
        if (stack.is(Tags.Items.SEEDS)) return true;
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        return blockItem.getBlock() instanceof CropBlock || blockItem.getBlock() instanceof StemBlock;
    }

    private boolean townstead$hasHoe(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof HoeItem) return true;
        }
        return false;
    }

    private boolean townstead$isTillable(ServerLevel level, BlockPos pos, long gameTime) {
        if (!townstead$isPlannedSoil(pos)) return false;
        if (townstead$isRecentlyWorked(pos, gameTime)) return false;
        BlockState above = level.getBlockState(pos.above());
        if (!above.isAir() && !townstead$canClearTillObstruction(above)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FarmBlock) return false;
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
    }

    private boolean townstead$hasHydrationCoverage(ServerLevel level) {
        int hydrated = 0;
        int total = 0;
        for (BlockPos soilPos : townstead$iterateSoilCells(level)) {
            if (!townstead$isPlannedSoil(soilPos)) continue;
            total++;
            if (townstead$hasNearbyWater(level, soilPos)) hydrated++;
        }
        if (total == 0) return true;
        int percent = (hydrated * 100) / Math.max(1, total);
        return percent >= townstead$hydrationMinPercent();
    }

    private boolean townstead$needsIrrigation(ServerLevel level, long gameTime) {
        if (!townstead$hasHydrationCoverage(level)) return true;
        return townstead$hasDryTillableCell(level, gameTime);
    }

    private boolean townstead$hasDryTillableCell(ServerLevel level, long gameTime) {
        for (BlockPos soilPos : townstead$iterateSoilCells(level)) {
            if (!townstead$isTillable(level, soilPos, gameTime)) continue;
            if (!townstead$hasNearbyWater(level, soilPos)) return true;
        }
        return false;
    }

    private boolean townstead$hasNearbyWater(ServerLevel level, BlockPos soilPos) {
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

    private boolean townstead$canAttemptWaterPlacement(ServerLevel level, long gameTime) {
        if (!TownsteadConfig.ENABLE_FARMER_WATER_PLACEMENT.get()) return false;
        return townstead$hasWaterPlacementBudget(gameTime);
    }

    private boolean townstead$hasWaterPlacementBudget(long gameTime) {
        long day = gameTime / 24000L;
        if (day != waterPlacementDay) {
            waterPlacementDay = day;
            waterPlacementsToday = 0;
        }
        return waterPlacementsToday < townstead$waterPlacementsPerDay();
    }

    private void townstead$recordWaterPlacement(long gameTime) {
        long day = gameTime / 24000L;
        if (day != waterPlacementDay) {
            waterPlacementDay = day;
            waterPlacementsToday = 0;
        }
        waterPlacementsToday++;
    }

    private BlockPos townstead$findNearestWaterPlacementSpot(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos bestPreferred = null;
        double bestPreferredDist = Double.MAX_VALUE;
        BlockPos bestAny = null;
        double bestAnyDist = Double.MAX_VALUE;
        for (BlockPos soilPos : townstead$iterateSoilCells(level)) {
            if (townstead$isBlacklisted(soilPos, gameTime)) continue;
            if (!townstead$canPlaceWaterAt(level, soilPos)) continue;
            double dist = villager.distanceToSqr(soilPos.getX() + 0.5, soilPos.getY() + 0.5, soilPos.getZ() + 0.5);
            if (dist < bestAnyDist) {
                bestAnyDist = dist;
                bestAny = soilPos.immutable();
            }
            if (townstead$isPreferredIrrigationCell(soilPos) && dist < bestPreferredDist) {
                bestPreferredDist = dist;
                bestPreferred = soilPos.immutable();
            }
        }
        if (bestPreferred != null) return bestPreferred;
        return bestAny;
    }

    private boolean townstead$canPlaceWaterAt(ServerLevel level, BlockPos soilPos) {
        if (!townstead$isPlannedSoil(soilPos)) return false;
        if (townstead$hasNearbyWater(level, soilPos)) return false;

        BlockState state = level.getBlockState(soilPos);
        boolean replaceableSoil = state.getBlock() instanceof FarmBlock
                || state.is(Blocks.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.COARSE_DIRT);
        if (!replaceableSoil) return false;

        BlockState above = level.getBlockState(soilPos.above());
        if (above.isAir()) return true;
        return townstead$canClearWaterPlacementObstruction(above);
    }

    private boolean townstead$isPreferredIrrigationCell(BlockPos pos) {
        if (farmAnchor == null) return false;
        // Sparse regular lattice for cleaner irrigation layout.
        return Math.floorMod(pos.getX() - farmAnchor.getX(), 8) == 0
                && Math.floorMod(pos.getZ() - farmAnchor.getZ(), 8) == 0;
    }

    private boolean townstead$canClearTillObstruction(BlockState state) {
        if (state.isAir()) return true;
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) return false;
        if (state.getBlock() instanceof BushBlock) return true;
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW);
    }

    private boolean townstead$canClearWaterPlacementObstruction(BlockState state) {
        if (state.isAir()) return true;
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) return true;
        if (state.getBlock() instanceof BushBlock) return true;
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SNOW);
    }

    private boolean townstead$hasStockableOutput(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof HoeItem) continue;
            if (townstead$isSeed(s)) continue;
            return true;
        }
        return false;
    }

    private boolean townstead$isInventoryMostlyFull(SimpleContainer inv) {
        int used = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) used++;
        }
        return used >= Math.max(1, (int) Math.floor(inv.getContainerSize() * 0.7));
    }

    private int townstead$findBestFoodSlot(SimpleContainer inv) {
        int best = -1;
        int nutrition = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            FoodProperties food = inv.getItem(i).get(DataComponents.FOOD);
            if (food == null || food.nutrition() <= 0) continue;
            if (food.nutrition() > nutrition) {
                nutrition = food.nutrition();
                best = i;
            }
        }
        return best;
    }

    private void townstead$restockBasics(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || farmAnchor == null) return;
        SimpleContainer inv = villager.getInventory();
        int farmRadius = townstead$farmRadius();
        if (!townstead$hasHoe(inv)) {
            NearbyItemSources.pullSingleToInventory(level, villager, farmRadius, VERTICAL_RADIUS,
                    s -> s.getItem() instanceof HoeItem, s -> 1, farmAnchor);
        }
        int desiredSeedCount = Math.max(1, townstead$seedReserve());
        if (townstead$countSeeds(inv) < desiredSeedCount) {
            NearbyItemSources.pullSingleToInventory(level, villager, farmRadius, VERTICAL_RADIUS,
                    this::townstead$isSeed, ItemStack::getCount, farmAnchor);
        }
        if (TownsteadConfig.ENABLE_FARMER_WATER_PLACEMENT.get() && townstead$findWaterBucketSlot(inv) < 0) {
            NearbyItemSources.pullSingleToInventory(level, villager, farmRadius, VERTICAL_RADIUS,
                    s -> s.is(Items.WATER_BUCKET), s -> 1, farmAnchor);
            if (townstead$findWaterBucketSlot(inv) < 0 && townstead$findEmptyBucketSlot(inv) < 0) {
                NearbyItemSources.pullSingleToInventory(level, villager, farmRadius, VERTICAL_RADIUS,
                        s -> s.is(Items.BUCKET), s -> 1, farmAnchor);
            }
        }
    }

    private BlockPos townstead$findNearestWaterSource(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (farmAnchor == null) return null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int radius = Math.max(townstead$farmRadius(), townstead$waterSourceSearchRadius());
        int vertical = townstead$waterSourceVerticalRadius();
        for (BlockPos pos : BlockPos.betweenClosed(
                farmAnchor.offset(-radius, -vertical, -radius),
                farmAnchor.offset(radius, vertical, radius))) {
            if (townstead$isBlacklisted(pos, gameTime)) continue;
            if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;
            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    private BlockPos townstead$findNearestComposter(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos center = villager.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-ANCHOR_SEARCH_RADIUS, -VERTICAL_RADIUS, -ANCHOR_SEARCH_RADIUS),
                center.offset(ANCHOR_SEARCH_RADIUS, VERTICAL_RADIUS, ANCHOR_SEARCH_RADIUS))) {
            if (!(level.getBlockState(pos).getBlock() instanceof ComposterBlock)) continue;
            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    private Iterable<BlockPos> townstead$iterateSoilCells(ServerLevel level) {
        if (farmAnchor == null) return List.of();
        if (farmBlueprint != null && !farmBlueprint.isEmpty()) return farmBlueprint.soilCells();

        // Conservative fallback: existing farm cells only (no random dirt expansion).
        List<BlockPos> cells = new java.util.ArrayList<>();
        int farmRadius = townstead$farmRadius();
        for (BlockPos pos : BlockPos.betweenClosed(
                farmAnchor.offset(-farmRadius, -VERTICAL_RADIUS, -farmRadius),
                farmAnchor.offset(farmRadius, VERTICAL_RADIUS, farmRadius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FarmBlock
                    || level.getBlockState(pos.above()).getBlock() instanceof CropBlock) {
                cells.add(pos.immutable());
            }
        }
        return cells;
    }

    private boolean townstead$isPlannedSoil(BlockPos pos) {
        if (!townstead$isInsideFarmRadius(pos)) return false;
        if (farmBlueprint == null || farmBlueprint.isEmpty()) return false;
        return farmBlueprint.containsSoil(pos);
    }

    private boolean townstead$isPlannedCropPos(BlockPos cropPos) {
        return townstead$isPlannedSoil(cropPos.below());
    }

    private boolean townstead$isInsideFarmRadius(BlockPos pos) {
        if (farmAnchor == null) return false;
        int farmRadius = townstead$farmRadius();
        int dx = Math.abs(pos.getX() - farmAnchor.getX());
        int dz = Math.abs(pos.getZ() - farmAnchor.getZ());
        int dy = Math.abs(pos.getY() - farmAnchor.getY());
        return dx <= farmRadius && dz <= farmRadius && dy <= VERTICAL_RADIUS;
    }

    private Activity townstead$getCurrentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private boolean townstead$hasPendingWork(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        BlockPos prevAnchor = farmAnchor;
        FarmBlueprint prevBlueprint = farmBlueprint;
        long prevNextBlueprintPlanTick = nextBlueprintPlanTick;
        farmAnchor = anchor;
        try {
            SimpleContainer inv = villager.getInventory();
            boolean hasSeeds = townstead$findSeedSlot(inv) >= 0;
            boolean hasHoe = townstead$hasHoe(inv);
            townstead$refreshBlueprintIfNeeded(level, level.getGameTime(), true);
            if (townstead$findNearestMatureCrop(level, villager, level.getGameTime()) != null) return true;
            BlockPos plantSpot = townstead$findNearestPlantSpot(level, villager, level.getGameTime());
            if (hasSeeds && plantSpot != null) return true;
            if (!hasSeeds && TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() && plantSpot != null) return true;

            BlockPos tillSpot = townstead$findNearestTillSpot(level, villager, level.getGameTime());
            if (hasHoe && tillSpot != null) return true;
            if (!hasHoe && TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() && tillSpot != null) return true;

            return townstead$isInventoryMostlyFull(inv);
        } finally {
            farmAnchor = prevAnchor;
            farmBlueprint = prevBlueprint;
            nextBlueprintPlanTick = prevNextBlueprintPlanTick;
        }
    }

    private void townstead$refreshBlueprintIfNeeded(ServerLevel level, long gameTime, boolean force) {
        if (farmAnchor == null) {
            farmBlueprint = null;
            nextBlueprintPlanTick = 0;
            return;
        }
        boolean anchorChanged = farmBlueprint == null || !farmAnchor.equals(farmBlueprint.anchor());
        if (!force && !anchorChanged && gameTime < nextBlueprintPlanTick) return;

        FarmingPolicyData.ResolvedFarmingPolicy policy = FarmingPolicyData.get(level).resolveForAnchor(farmAnchor);
        String patternId = policy.patternId();
        int tier = policy.tier();
        int maxClusters = townstead$maxClustersForTier(tier);
        int maxPlots = townstead$maxPlotsForTier(tier);

        farmBlueprint = switch (patternId) {
            case DEFAULT_PATTERN_ID -> FarmPlanner.planStarterRows(
                    level,
                    farmAnchor,
                    townstead$farmRadius(),
                    VERTICAL_RADIUS,
                    maxClusters,
                    maxPlots
            );
            default -> FarmPlanner.planStarterRows(
                    level,
                    farmAnchor,
                    townstead$farmRadius(),
                    VERTICAL_RADIUS,
                    maxClusters,
                    maxPlots
            );
        };

        if (TownsteadConfig.DEBUG_FARMER_AI.get()) {
            LOGGER.info(
                    "Farmer {} blueprint plan: pattern={}, tier={}, source={}, cells={}",
                    farmAnchor,
                    patternId,
                    tier,
                    policy.source(),
                    farmBlueprint == null ? 0 : farmBlueprint.soilCells().size()
            );
        }
        nextBlueprintPlanTick = gameTime + BLUEPRINT_REPLAN_INTERVAL;
    }

    private void townstead$setBlockedReason(ServerLevel level, VillagerEntityMCA villager, HungerData.FarmBlockedReason reason) {
        if (blockedReason == reason) return;
        HungerData.FarmBlockedReason previous = blockedReason;
        blockedReason = reason;

        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        if (HungerData.getFarmBlockedReason(hunger) != reason) {
            HungerData.setFarmBlockedReason(hunger, reason);
            villager.setData(Townstead.HUNGER_DATA, hunger);
        }
        PacketDistributor.sendToPlayersTrackingEntity(villager, new FarmStatusSyncPayload(villager.getId(), reason.id()));
        if (reason == HungerData.FarmBlockedReason.NONE) {
            nextRequestTick = 0;
        } else {
            // Delay first request slightly so transient states do not spam chat.
            long soonest = level.getGameTime() + 40;
            if (nextRequestTick < soonest) nextRequestTick = soonest;
        }

        if (TownsteadConfig.DEBUG_FARMER_AI.get()) {
            LOGGER.info("Farmer {} blocked state: {} -> {}", villager.getUUID(), previous.id(), reason.id());
        }
    }

    private int townstead$farmRadius() {
        return TownsteadConfig.FARMER_FARM_RADIUS.get();
    }

    private int townstead$cellCooldownTicks() {
        return TownsteadConfig.ENABLE_FARMER_STABILITY_V2.get() ? TownsteadConfig.FARMER_CELL_COOLDOWN_TICKS.get() : 0;
    }

    private int townstead$pathfailMaxRetries() {
        return TownsteadConfig.ENABLE_FARMER_STABILITY_V2.get() ? TownsteadConfig.FARMER_PATHFAIL_MAX_RETRIES.get() : 10;
    }

    private int townstead$idleBackoffTicks() {
        return TownsteadConfig.ENABLE_FARMER_STABILITY_V2.get() ? TownsteadConfig.FARMER_IDLE_BACKOFF_TICKS.get() : 20;
    }

    private int townstead$seedReserve() {
        return TownsteadConfig.ENABLE_FARMER_STABILITY_V2.get() ? TownsteadConfig.FARMER_SEED_RESERVE.get() : 0;
    }

    private int townstead$maxPlots() {
        return TownsteadConfig.FARMER_MAX_PLOTS.get();
    }

    private int townstead$maxClusters() {
        return TownsteadConfig.FARMER_MAX_CLUSTERS.get();
    }

    private int townstead$waterPlacementsPerDay() {
        return Math.max(0, TownsteadConfig.FARMER_WATER_PLACEMENTS_PER_DAY.get());
    }

    private int townstead$hydrationMinPercent() {
        return Math.max(0, Math.min(100, TownsteadConfig.FARMER_HYDRATION_MIN_PERCENT.get()));
    }

    private int townstead$waterSourceSearchRadius() {
        return Math.max(8, TownsteadConfig.FARMER_WATER_SOURCE_SEARCH_RADIUS.get());
    }

    private int townstead$waterSourceVerticalRadius() {
        return Math.max(2, TownsteadConfig.FARMER_WATER_SOURCE_VERTICAL_RADIUS.get());
    }

    private int townstead$maxClustersForTier(int tier) {
        int cap = Math.max(1, townstead$maxClusters());
        int normalized = Math.max(1, Math.min(tier, 5));
        int target = switch (normalized) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> cap;
        };
        return Math.min(cap, target);
    }

    private int townstead$maxPlotsForTier(int tier) {
        int cap = Math.max(16, townstead$maxPlots());
        int normalized = Math.max(1, Math.min(tier, 5));
        int target = switch (normalized) {
            case 1 -> Math.max(24, cap / 3);
            case 2 -> Math.max(32, cap / 2);
            case 3 -> Math.max(48, (cap * 2) / 3);
            case 4 -> Math.max(64, (cap * 5) / 6);
            default -> cap;
        };
        return Math.min(cap, target);
    }

    private void townstead$maybeAnnounceRequest(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.ENABLE_FARMER_REQUEST_CHAT.get()) return;
        if (blockedReason == HungerData.FarmBlockedReason.NONE) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) {
            nextRequestTick = gameTime + 200;
            return;
        }

        String key = switch (blockedReason) {
            case NO_SEEDS -> "dialogue.chat.farm_request.no_seeds/" + (1 + level.random.nextInt(6));
            case NO_TOOL -> "dialogue.chat.farm_request.no_tool/" + (1 + level.random.nextInt(6));
            case NO_WATER_PLAN -> "dialogue.chat.farm_request.no_water_plan/" + (1 + level.random.nextInt(4));
            case UNREACHABLE -> "dialogue.chat.farm_request.unreachable/" + (1 + level.random.nextInt(6));
            case OUT_OF_SCOPE -> "dialogue.chat.farm_request.out_of_scope/" + (1 + level.random.nextInt(4));
            case NO_VALID_TARGET, UNSUPPORTED_CROP -> null;
            default -> null;
        };
        if (key == null) return;

        villager.sendChatToAllAround(key);
        // Hook for MCA ChatAI/LLM context and future prompt conditioning.
        villager.getLongTermMemory().remember("townstead.farm_request.any");
        villager.getLongTermMemory().remember("townstead.farm_request." + blockedReason.id());
        nextRequestTick = gameTime + TownsteadConfig.FARMER_REQUEST_INTERVAL_TICKS.get();
    }
}
