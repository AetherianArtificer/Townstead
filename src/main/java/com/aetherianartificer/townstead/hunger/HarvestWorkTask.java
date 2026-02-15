package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

public class HarvestWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int ANCHOR_SEARCH_RADIUS = 24;
    private static final int FARM_RADIUS = 12;
    private static final int VERTICAL_RADIUS = 3;
    private static final float WALK_SPEED_HARVEST = 0.66f;
    private static final float WALK_SPEED_NORMAL = 0.52f;
    private static final int CLOSE_ENOUGH = 1;
    private static final int MAX_DURATION = 1200;
    private static final int NATURAL_RETURN_DISTANCE = FARM_RADIUS + 8;

    private enum ActionType { NONE, RETURN, HARVEST, PLANT, TILL, STOCK }

    private ActionType actionType = ActionType.NONE;
    private BlockPos targetPos;
    private BlockPos farmAnchor;
    private int actionCooldown;

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
        townstead$acquireTarget(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (farmAnchor == null) {
            farmAnchor = townstead$findNearestComposter(level, villager);
            if (farmAnchor == null) return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
            return;
        }

        if (targetPos == null || !townstead$isTargetStillValid(level)) {
            townstead$acquireTarget(level, villager);
            if (targetPos == null) return;
        }

        float walkSpeed = actionType == ActionType.HARVEST ? WALK_SPEED_HARVEST : WALK_SPEED_NORMAL;
        BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, walkSpeed, CLOSE_ENOUGH);
        double distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq > (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) return;

        switch (actionType) {
            case RETURN -> {}
            case HARVEST -> townstead$doHarvest(level, villager);
            case PLANT -> townstead$doPlant(level, villager, targetPos);
            case TILL -> townstead$doTill(level, villager, targetPos);
            case STOCK -> townstead$doStock(level, villager, false);
            default -> {}
        }

        actionCooldown = 10;
        townstead$acquireTarget(level, villager);
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
            // End-of-work unload for downstream production chains (baker/chef/etc).
            townstead$doStock(level, villager, true);
        }
        actionType = ActionType.NONE;
        targetPos = null;
        farmAnchor = null;
        actionCooldown = 0;
    }

    private void townstead$acquireTarget(ServerLevel level, VillagerEntityMCA villager) {
        if (farmAnchor == null) {
            actionType = ActionType.NONE;
            targetPos = null;
            return;
        }

        townstead$restockBasics(level, villager);

        double returnDistSq = villager.distanceToSqr(farmAnchor.getX() + 0.5, farmAnchor.getY() + 0.5, farmAnchor.getZ() + 0.5);
        if (returnDistSq > (double) NATURAL_RETURN_DISTANCE * NATURAL_RETURN_DISTANCE) {
            actionType = ActionType.RETURN;
            targetPos = farmAnchor;
            return;
        }

        // Priority: harvest -> replant/sow -> till -> stock.
        BlockPos harvest = townstead$findNearestMatureCrop(level, villager);
        if (harvest != null) {
            actionType = ActionType.HARVEST;
            targetPos = harvest;
            return;
        }

        if (townstead$findSeedSlot(villager.getInventory()) >= 0) {
            BlockPos plant = townstead$findNearestPlantSpot(level, villager);
            if (plant != null) {
                actionType = ActionType.PLANT;
                targetPos = plant;
                return;
            }
        }

        if (townstead$hasHoe(villager.getInventory())) {
            BlockPos till = townstead$findNearestTillSpot(level, villager);
            if (till != null) {
                actionType = ActionType.TILL;
                targetPos = till;
                return;
            }
        }

        if (townstead$isInventoryMostlyFull(villager.getInventory()) || townstead$hasStockableOutput(villager.getInventory())) {
            actionType = ActionType.STOCK;
            targetPos = farmAnchor;
            return;
        }

        actionType = ActionType.NONE;
        targetPos = null;
    }

    private boolean townstead$isTargetStillValid(ServerLevel level) {
        if (targetPos == null || farmAnchor == null) return false;
        if (!townstead$isInsideFarmRadius(targetPos)) return false;

        BlockState state = level.getBlockState(targetPos);
        return switch (actionType) {
            case RETURN -> true;
            case HARVEST -> state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
            case PLANT -> state.isAir() && level.getBlockState(targetPos.below()).getBlock() instanceof FarmBlock;
            case TILL -> townstead$isTillable(level, targetPos);
            case STOCK -> true;
            default -> false;
        };
    }

    private BlockPos townstead$findNearestMatureCrop(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : townstead$iterateFarmArea()) {
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

    private BlockPos townstead$findNearestPlantSpot(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos soilPos : townstead$iterateFarmArea()) {
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

    private BlockPos townstead$findNearestTillSpot(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : townstead$iterateFarmArea()) {
            if (!townstead$isTillable(level, pos)) continue;
            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = pos.immutable();
            }
        }
        return bestPos;
    }

    private void townstead$doHarvest(ServerLevel level, VillagerEntityMCA villager) {
        if (targetPos == null) return;
        BlockState state = level.getBlockState(targetPos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;

        List<ItemStack> drops = CropBlock.getDrops(state, level, targetPos, null);
        level.destroyBlock(targetPos, false, villager);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) villager.getInventory().addItem(drop);
        }
        villager.swing(villager.getDominantHand());

        if (townstead$findSeedSlot(villager.getInventory()) >= 0) {
            townstead$doPlant(level, villager, targetPos);
        }
    }

    private void townstead$doPlant(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        int slot = townstead$findSeedSlot(villager.getInventory());
        if (slot < 0) return;
        ItemStack seed = villager.getInventory().getItem(slot);
        if (!(seed.getItem() instanceof BlockItem blockItem)) return;
        BlockState place = blockItem.getBlock().defaultBlockState();
        if (!place.canSurvive(level, pos)) return;
        if (!level.getBlockState(pos).isAir()) return;
        if (level.setBlock(pos, place, 3)) {
            seed.shrink(1);
            villager.swing(villager.getDominantHand());
        }
    }

    private void townstead$doTill(ServerLevel level, VillagerEntityMCA villager, BlockPos soilPos) {
        if (!townstead$hasHoe(villager.getInventory())) return;
        if (!townstead$isTillable(level, soilPos)) return;
        if (level.setBlock(soilPos, Blocks.FARMLAND.defaultBlockState(), 3)) {
            villager.swing(villager.getDominantHand());
        }
    }

    private void townstead$doStock(ServerLevel level, VillagerEntityMCA villager, boolean endOfWork) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() || farmAnchor == null) return;
        SimpleContainer inv = villager.getInventory();
        int keepFood = townstead$findBestFoodSlot(inv);

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!endOfWork && i == keepFood) continue;
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            // End-of-work keeps only one food stack; everything else can be shared to production chains.
            if (!endOfWork) {
                if (stack.getItem() instanceof HoeItem) continue;
                if (townstead$isSeed(stack)) continue;
            }

            ItemStack moving = stack.copy();
            boolean stored = NearbyItemSources.insertIntoNearbyStorage(level, villager, moving, FARM_RADIUS, VERTICAL_RADIUS, farmAnchor);
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

    private boolean townstead$isTillable(ServerLevel level, BlockPos pos) {
        if (!townstead$isInsideFarmRadius(pos)) return false;
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
        if (!townstead$hasHoe(inv)) {
            NearbyItemSources.pullSingleToInventory(level, villager, FARM_RADIUS, VERTICAL_RADIUS,
                    s -> s.getItem() instanceof HoeItem, s -> 1, farmAnchor);
        }
        if (townstead$findSeedSlot(inv) < 0) {
            NearbyItemSources.pullSingleToInventory(level, villager, FARM_RADIUS, VERTICAL_RADIUS,
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
        return BlockPos.betweenClosed(
                farmAnchor.offset(-FARM_RADIUS, -VERTICAL_RADIUS, -FARM_RADIUS),
                farmAnchor.offset(FARM_RADIUS, VERTICAL_RADIUS, FARM_RADIUS));
    }

    private boolean townstead$isInsideFarmRadius(BlockPos pos) {
        if (farmAnchor == null) return false;
        int dx = Math.abs(pos.getX() - farmAnchor.getX());
        int dz = Math.abs(pos.getZ() - farmAnchor.getZ());
        int dy = Math.abs(pos.getY() - farmAnchor.getY());
        return dx <= FARM_RADIUS && dz <= FARM_RADIUS && dy <= VERTICAL_RADIUS;
    }

    private Activity townstead$getCurrentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private boolean townstead$hasPendingWork(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        BlockPos prevAnchor = farmAnchor;
        farmAnchor = anchor;
        try {
            if (townstead$findNearestMatureCrop(level, villager) != null) return true;
            if (townstead$findSeedSlot(villager.getInventory()) >= 0
                    && townstead$findNearestPlantSpot(level, villager) != null) return true;
            if (townstead$hasHoe(villager.getInventory())
                    && townstead$findNearestTillSpot(level, villager) != null) return true;
            return townstead$isInventoryMostlyFull(villager.getInventory()) || townstead$hasStockableOutput(villager.getInventory());
        } finally {
            farmAnchor = prevAnchor;
        }
    }
}
