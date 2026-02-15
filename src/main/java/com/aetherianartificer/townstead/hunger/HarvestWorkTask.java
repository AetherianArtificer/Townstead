package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
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

    private enum ActionType { NONE, RETURN, HARVEST, PLANT, TILL, STOCK }

    private ActionType actionType = ActionType.NONE;
    private BlockPos targetPos;
    private BlockPos farmAnchor;
    private int actionCooldown;
    private long nextAcquireTick;
    private long nextTargetScanTick;

    private BlockPos cachedHarvestTarget;
    private BlockPos cachedPlantTarget;
    private BlockPos cachedTillTarget;
    private boolean cachedHasHoe;
    private boolean cachedHasSeed;
    private int cachedSeedCount;
    private long cachedInventoryTick;

    private long currentTargetKey = Long.MIN_VALUE;
    private double lastTargetDistSq = Double.MAX_VALUE;
    private int targetStuckTicks;

    private final Map<Long, Long> recentlyWorkedCells = new HashMap<>();
    private final Map<Long, Integer> targetRetries = new HashMap<>();
    private final Map<Long, Long> targetBlacklistUntil = new HashMap<>();

    private HungerData.FarmBlockedReason blockedReason = HungerData.FarmBlockedReason.NONE;

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
        if (anchor == null) return false;
        return townstead$hasPendingWork(level, villager, anchor);
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        actionType = ActionType.NONE;
        targetPos = null;
        farmAnchor = townstead$findNearestComposter(level, villager);
        actionCooldown = 0;
        nextAcquireTick = 0;
        nextTargetScanTick = 0;
        currentTargetKey = Long.MIN_VALUE;
        lastTargetDistSq = Double.MAX_VALUE;
        targetStuckTicks = 0;
        cachedInventoryTick = -1;
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
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }
        if (gameTime < nextAcquireTick) return;

        if (targetPos == null || !townstead$isTargetStillValid(level, gameTime)) {
            townstead$acquireTarget(level, villager, gameTime, true);
            if (targetPos == null) return;
        }

        float walkSpeed = actionType == ActionType.HARVEST ? WALK_SPEED_HARVEST : WALK_SPEED_NORMAL;
        BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, walkSpeed, CLOSE_ENOUGH);
        double distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq > (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
            townstead$trackPathProgress(level, villager, gameTime, distSq);
            return;
        }

        townstead$resetPathTracking();
        switch (actionType) {
            case RETURN -> {}
            case HARVEST -> townstead$doHarvest(level, villager, gameTime);
            case PLANT -> townstead$doPlant(level, villager, targetPos, gameTime);
            case TILL -> townstead$doTill(level, villager, targetPos, gameTime);
            case STOCK -> townstead$doStock(level, villager, false);
            default -> {}
        }

        actionCooldown = 10;
        townstead$clearTargetRetry(targetPos);
        townstead$acquireTarget(level, villager, gameTime, false);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!checkExtraStartConditions(level, villager)) return false;
        return actionType != ActionType.NONE && targetPos != null;
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
        cachedInventoryTick = -1;
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

        if (cachedSeedCount >= townstead$seedReserve() && cachedTillTarget != null) {
            actionType = ActionType.TILL;
            targetPos = cachedTillTarget;
            townstead$setBlockedReason(level, villager, HungerData.FarmBlockedReason.NONE);
            return;
        }

        if (townstead$isInventoryMostlyFull(villager.getInventory()) || townstead$hasStockableOutput(villager.getInventory())) {
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

    private boolean townstead$isTargetStillValid(ServerLevel level, long gameTime) {
        if (targetPos == null || farmAnchor == null) return false;
        if (!townstead$isInsideFarmRadius(targetPos)) return false;
        if (townstead$isBlacklisted(targetPos, gameTime)) return false;

        BlockState state = level.getBlockState(targetPos);
        return switch (actionType) {
            case RETURN -> true;
            case HARVEST -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
            case PLANT -> state.isAir() && level.getBlockState(targetPos.below()).getBlock() instanceof FarmBlock;
            case TILL -> townstead$isTillable(level, targetPos, gameTime);
            case STOCK -> true;
            default -> false;
        };
    }

    private BlockPos townstead$findNearestMatureCrop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : townstead$iterateFarmArea()) {
            if (townstead$isBlacklisted(pos, gameTime)) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;
            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = pos.immutable();
            }
        }
        return bestPos;
    }

    private BlockPos townstead$findNearestPlantSpot(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos soilPos : townstead$iterateFarmArea()) {
            if (townstead$isBlacklisted(soilPos, gameTime)) continue;
            if (!(level.getBlockState(soilPos).getBlock() instanceof FarmBlock)) continue;
            BlockPos above = soilPos.above();
            if (!townstead$isInsideFarmRadius(above)) continue;
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
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : townstead$iterateFarmArea()) {
            if (townstead$isBlacklisted(pos, gameTime)) continue;
            if (!townstead$isTillable(level, pos, gameTime)) continue;
            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = pos.immutable();
            }
        }
        return bestPos;
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
        if (level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState(), 3)) {
            villager.swing(villager.getDominantHand());
            townstead$markWorked(soilPos, gameTime);
        }
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

    private void townstead$doStock(ServerLevel level, VillagerEntityMCA villager, boolean endOfWork) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || farmAnchor == null) return;
        SimpleContainer inv = villager.getInventory();
        int keepFood = townstead$findBestFoodSlot(inv);

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
        }
    }

    private int townstead$findSeedSlot(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (townstead$isSeed(inv.getItem(i))) return i;
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
        if (!townstead$isInsideFarmRadius(pos)) return false;
        if (townstead$isRecentlyWorked(pos, gameTime)) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof FarmBlock) return false;
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
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
        if (townstead$findSeedSlot(inv) < 0) {
            NearbyItemSources.pullSingleToInventory(level, villager, farmRadius, VERTICAL_RADIUS,
                    this::townstead$isSeed, ItemStack::getCount, farmAnchor);
        }
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

    private Iterable<BlockPos> townstead$iterateFarmArea() {
        if (farmAnchor == null) return List.of();
        int farmRadius = townstead$farmRadius();
        return BlockPos.betweenClosed(
                farmAnchor.offset(-farmRadius, -VERTICAL_RADIUS, -farmRadius),
                farmAnchor.offset(farmRadius, VERTICAL_RADIUS, farmRadius));
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
        farmAnchor = anchor;
        try {
            if (townstead$findNearestMatureCrop(level, villager, level.getGameTime()) != null) return true;
            if (townstead$findSeedSlot(villager.getInventory()) >= 0
                    && townstead$findNearestPlantSpot(level, villager, level.getGameTime()) != null) return true;
            if (townstead$hasHoe(villager.getInventory())
                    && townstead$findNearestTillSpot(level, villager, level.getGameTime()) != null) return true;
            return townstead$isInventoryMostlyFull(villager.getInventory()) || townstead$hasStockableOutput(villager.getInventory());
        } finally {
            farmAnchor = prevAnchor;
        }
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
}
