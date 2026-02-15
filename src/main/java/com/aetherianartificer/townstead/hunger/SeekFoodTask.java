package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

public class SeekFoodTask extends Behavior<VillagerEntityMCA> {

    private static final int SEARCH_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 4;
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 600; // ~30 seconds

    private enum TargetType { NONE, GROUND_ITEM, CONTAINER, CROP }

    private TargetType targetType = TargetType.NONE;
    private BlockPos targetPos;
    private ItemEntity targetItem;
    private Container targetContainer;
    private boolean targetIsItemHandler;
    private int targetSlot;
    private int cooldown;

    public SeekFoodTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (VillagerEatingManager.isEating(villager)) return false;

        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) {
            return false;
        }

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        int h = HungerData.getHunger(hunger);
        boolean eatingMode = HungerData.isEatingMode(hunger);
        if (h >= HungerData.LUNCH_THRESHOLD) return false;

        long gameTime = level.getGameTime();
        long lastAte = HungerData.getLastAteTime(hunger);
        long minEatInterval = (eatingMode || h < HungerData.EMERGENCY_THRESHOLD) ? 20L : HungerData.MIN_EAT_INTERVAL;
        if ((gameTime - lastAte) < minEatInterval) return false;

        // Check inventory first.
        if (TownsteadConfig.ENABLE_SELF_INVENTORY_EATING.get() && tryEatFromInventory(villager)) {
            cooldown = (eatingMode || h < HungerData.ADEQUATE_THRESHOLD) ? 5 : 200;
            return false;
        }

        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetType = TargetType.NONE;
        targetPos = null;
        targetItem = null;
        targetContainer = null;
        targetIsItemHandler = false;
        targetSlot = -1;

        // Priority 1: Ground items
        if (TownsteadConfig.ENABLE_GROUND_ITEM_SOURCING.get() && findGroundItem(level, villager)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetItem, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        // Priority 2: Containers
        if (TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() && findContainerFood(level, villager)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        // Priority 3: Mature crops
        if (TownsteadConfig.ENABLE_CROP_SOURCING.get() && findMatureCrop(level, villager)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        // Nothing found — give up
        cooldown = 200;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetType == TargetType.NONE) return;

        double distSq;
        switch (targetType) {
            case GROUND_ITEM -> {
                if (targetItem == null || targetItem.isRemoved()) {
                    doStop(level, villager, gameTime);
                    return;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(villager, targetItem, WALK_SPEED, CLOSE_ENOUGH);
                distSq = villager.distanceToSqr(targetItem);
                if (distSq <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                    pickUpAndEat(villager, targetItem);
                    doStop(level, villager, gameTime);
                }
            }
            case CONTAINER -> {
                if (targetPos == null) {
                    doStop(level, villager, gameTime);
                    return;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
                distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distSq <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                    takeFromContainerAndEat(villager);
                    doStop(level, villager, gameTime);
                }
            }
            case CROP -> {
                if (targetPos == null) {
                    doStop(level, villager, gameTime);
                    return;
                }
                BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
                distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distSq <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                    harvestCropAndEat(level, villager);
                    doStop(level, villager, gameTime);
                }
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetType == TargetType.NONE) return false;
        if (targetType == TargetType.GROUND_ITEM && (targetItem == null || targetItem.isRemoved())) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) {
            return false;
        }
        return true;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Don't clear WALK_TARGET/LOOK_TARGET — the brain manages them naturally,
        // and clearing them here would clobber targets set by other behaviors.
        targetType = TargetType.NONE;
        targetPos = null;
        targetItem = null;
        targetContainer = null;
        targetIsItemHandler = false;
        targetSlot = -1;
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        cooldown = (HungerData.isEatingMode(hunger) || HungerData.getHunger(hunger) < HungerData.ADEQUATE_THRESHOLD) ? 5 : 200;
    }

    // --- Inventory eating (starts vanilla item-use eating flow) ---

    private boolean tryEatFromInventory(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        ItemStack best = ItemStack.EMPTY;
        int bestNutrition = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null && food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
                best = stack;
            }
        }

        if (best.isEmpty()) return false;

        if (!VillagerEatingManager.startEating(villager, best)) return false;
        best.shrink(1);
        return true;
    }

    // --- Search methods ---

    private boolean findGroundItem(ServerLevel level, VillagerEntityMCA villager) {
        AABB searchBox = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> {
                    FoodProperties food = item.getItem().get(DataComponents.FOOD);
                    return food != null && food.nutrition() > 0 && !item.isRemoved();
                });

        if (items.isEmpty()) return false;

        // Pick closest
        ItemEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double dist = villager.distanceToSqr(item);
            if (dist < closestDist) {
                closestDist = dist;
                closest = item;
            }
        }

        if (closest == null) return false;
        targetType = TargetType.GROUND_ITEM;
        targetItem = closest;
        return true;
    }

    private boolean findContainerFood(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos center = villager.blockPosition();
        BlockPos bestPos = null;
        Container bestContainer = null;
        boolean bestIsItemHandler = false;
        int bestSlot = -1;
        int bestNutrition = 0;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SEARCH_RADIUS, -VERTICAL_RADIUS, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS))) {

            BlockEntity be = level.getBlockEntity(pos);
            if (TownsteadConfig.isProtectedStorage(level.getBlockState(pos))) continue;
            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    FoodProperties food = stack.get(DataComponents.FOOD);
                    if (food != null && food.nutrition() > 0) {
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        // Prefer closer containers, then higher nutrition
                        if (bestPos == null || dist < bestDist - 4.0 || (dist < bestDist + 4.0 && food.nutrition() > bestNutrition)) {
                            bestPos = pos.immutable();
                            bestContainer = container;
                            bestIsItemHandler = false;
                            bestSlot = i;
                            bestNutrition = food.nutrition();
                            bestDist = dist;
                        }
                    }
                }
            }

            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (itemHandler != null) {
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    ItemStack stack = itemHandler.getStackInSlot(i);
                    FoodProperties food = stack.get(DataComponents.FOOD);
                    if (food == null || food.nutrition() <= 0) continue;
                    double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    // Prefer closer containers, then higher nutrition
                    if (bestPos == null || dist < bestDist - 4.0 || (dist < bestDist + 4.0 && food.nutrition() > bestNutrition)) {
                        bestPos = pos.immutable();
                        bestContainer = null;
                        bestIsItemHandler = true;
                        bestSlot = i;
                        bestNutrition = food.nutrition();
                        bestDist = dist;
                    }
                }
            }
        }

        if (bestPos == null) return false;
        targetType = TargetType.CONTAINER;
        targetPos = bestPos;
        this.targetContainer = bestContainer;
        this.targetIsItemHandler = bestIsItemHandler;
        this.targetSlot = bestSlot;
        return true;
    }

    private boolean findMatureCrop(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos center = villager.blockPosition();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SEARCH_RADIUS, -VERTICAL_RADIUS, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS))) {

            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = pos.immutable();
                }
            }
        }

        if (bestPos == null) return false;
        targetType = TargetType.CROP;
        targetPos = bestPos;
        return true;
    }

    // --- Consumption methods ---

    private void pickUpAndEat(VillagerEntityMCA villager, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (!VillagerEatingManager.startEating(villager, stack)) return;
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        }
    }

    private void takeFromContainerAndEat(VillagerEntityMCA villager) {
        if (targetSlot < 0 || targetPos == null) return;

        if (targetIsItemHandler) {
            if (!(villager.level() instanceof ServerLevel level)) return;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, null);
            if (handler == null) return;

            ItemStack extracted = handler.extractItem(targetSlot, 1, false);
            if (extracted.isEmpty()) return;
            if (!VillagerEatingManager.startEating(villager, extracted)) {
                ItemStack remaining = handler.insertItem(targetSlot, extracted, false);
                if (!remaining.isEmpty()) villager.getInventory().addItem(remaining);
            }
            return;
        }

        if (targetContainer == null) return;
        ItemStack stack = targetContainer.getItem(targetSlot);
        if (!VillagerEatingManager.startEating(villager, stack)) return;
        stack.shrink(1);
        targetContainer.setChanged();
    }

    private void harvestCropAndEat(ServerLevel level, VillagerEntityMCA villager) {
        if (targetPos == null) return;

        BlockState state = level.getBlockState(targetPos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;

        // Break the crop and look for food in the drops
        List<ItemStack> drops = CropBlock.getDrops(state, level, targetPos, null);
        level.destroyBlock(targetPos, false, villager);

        boolean ate = false;

        for (ItemStack drop : drops) {
            FoodProperties food = drop.get(DataComponents.FOOD);
            if (food != null && !ate) {
                if (VillagerEatingManager.startEating(villager, drop)) {
                    drop.shrink(1);
                    ate = true;
                }
            }
            // Give remaining drops (seeds, extra food) to villager inventory
            if (!drop.isEmpty()) {
                villager.getInventory().addItem(drop);
            }
        }

        if (ate) cooldown = 200;
    }
}
