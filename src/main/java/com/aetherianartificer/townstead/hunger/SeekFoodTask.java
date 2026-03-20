package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.thirst.VillagerDrinkingManager;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SeekFoodTask extends Behavior<VillagerEntityMCA> {

    private static final int SEARCH_RADIUS = 48;
    private static final int VERTICAL_RADIUS = 8;
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 600; // ~30 seconds

    private enum TargetType { NONE, GROUND_ITEM, CONTAINER, CROP }

    private TargetType targetType = TargetType.NONE;
    private BlockPos targetPos;
    private ItemEntity targetItem;
    private NearbyItemSources.ContainerSlot targetContainerSlot;
    private int cooldown;

    public SeekFoodTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (VillagerEatingManager.isEating(villager) || VillagerDrinkingManager.isDrinking(villager)) return false;

        // Only block when fleeing from a mob — not during environmental panic
        // (thirst/hunger damage), otherwise villagers enter a death spiral.
        if (villager.getLastHurtByMob() != null) return false;

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        //? if neoforge {
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag hunger = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
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
        targetContainerSlot = null;

        // Search ground items and containers together, scored by nutrition (desc)
        // then distance (asc). Pick the best reachable one.
        if (findBestFoodSource(level, villager)) {
            if (targetType == TargetType.GROUND_ITEM) {
                BehaviorUtils.setWalkAndLookTargetMemories(villager, targetItem, WALK_SPEED, CLOSE_ENOUGH);
            } else {
                BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
            }
            return;
        }

        // Absolute last resort: eat raw crops off the stalk
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
        if (VillagerDrinkingManager.isDrinking(villager)) return false;
        if (villager.getLastHurtByMob() != null) return false;
        return true;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Don't clear WALK_TARGET/LOOK_TARGET — the brain manages them naturally,
        // and clearing them here would clobber targets set by other behaviors.
        targetType = TargetType.NONE;
        targetPos = null;
        targetItem = null;
        targetContainerSlot = null;
        //? if neoforge {
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag hunger = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        cooldown = (HungerData.isEatingMode(hunger) || HungerData.getHunger(hunger) < HungerData.ADEQUATE_THRESHOLD) ? 5 : 200;
    }

    // --- Inventory eating (starts vanilla item-use eating flow) ---

    private boolean tryEatFromInventory(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        ItemStack best = ItemStack.EMPTY;
        int bestNutrition = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            //? if >=1.21 {
            FoodProperties food = stack.get(DataComponents.FOOD);
            //?} else {
            /*FoodProperties food = stack.getFoodProperties(null);
            *///?}
            //? if >=1.21 {
            if (food != null && food.nutrition() > bestNutrition) {
                bestNutrition = food.nutrition();
            //?} else {
            /*if (food != null && food.getNutrition() > bestNutrition) {
                bestNutrition = food.getNutrition();
            *///?}
                best = stack;
            }
        }

        if (best.isEmpty()) return false;

        if (!VillagerEatingManager.startEating(villager, best)) return false;
        best.shrink(1);
        return true;
    }

    // --- Search methods ---

    /**
     * Candidate from any food source, scored by nutrition and distance.
     */
    private record FoodCandidate(TargetType type, int nutrition, double distSq,
                                  ItemEntity item, NearbyItemSources.ContainerSlot slot, BlockPos pos) {
        /**
         * Score: nutrition is primary (higher = better), distance is secondary (lower = better).
         * A cooked steak (nutrition 8) at 15 blocks beats raw corn (nutrition 1) at 3 blocks.
         */
        double score() {
            // Nutrition dominates: multiply by 100 so even 1 nutrition difference outweighs distance
            return nutrition * 100.0 - distSq;
        }
    }

    private boolean findBestFoodSource(ServerLevel level, VillagerEntityMCA villager) {
        List<FoodCandidate> candidates = new ArrayList<>();

        // Gather ground items
        if (TownsteadConfig.ENABLE_GROUND_ITEM_SOURCING.get()) {
            AABB searchBox = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, searchBox)) {
                int n = getNutrition(item.getItem());
                if (n > 0 && !item.isRemoved()) {
                    candidates.add(new FoodCandidate(TargetType.GROUND_ITEM, n,
                            villager.distanceToSqr(item), item, null, null));
                }
            }
        }

        // Gather container slots
        if (TownsteadConfig.ENABLE_CONTAINER_SOURCING.get()) {
            NearbyItemSources.ContainerSlot slot = NearbyItemSources.findBestNearbySlot(
                    level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                    stack -> getNutrition(stack) > 0,
                    SeekFoodTask::getNutrition);
            if (slot != null) {
                int n = slot.score();
                double dist = villager.distanceToSqr(
                        slot.pos().getX() + 0.5, slot.pos().getY() + 0.5, slot.pos().getZ() + 0.5);
                candidates.add(new FoodCandidate(TargetType.CONTAINER, n, dist, null, slot, slot.pos()));
            }
        }

        if (candidates.isEmpty()) return false;

        // Sort by score descending (best first)
        candidates.sort(Comparator.comparingDouble(c -> -c.score()));

        // Pick the first reachable candidate
        for (FoodCandidate c : candidates) {
            BlockPos pathTarget = c.type == TargetType.GROUND_ITEM
                    ? c.item.blockPosition() : c.pos;
            Path path = villager.getNavigation().createPath(pathTarget, CLOSE_ENOUGH);
            if (path != null && path.canReach()) {
                if (c.type == TargetType.GROUND_ITEM) {
                    targetType = TargetType.GROUND_ITEM;
                    targetItem = c.item;
                } else {
                    targetType = TargetType.CONTAINER;
                    targetContainerSlot = c.slot;
                    targetPos = c.pos;
                }
                return true;
            }
        }

        return false;
    }

    private boolean findGroundItem(ServerLevel level, VillagerEntityMCA villager) {
        AABB searchBox = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> {
                    //? if >=1.21 {
                    FoodProperties food = item.getItem().get(DataComponents.FOOD);
                    //?} else {
                    /*FoodProperties food = item.getItem().getFoodProperties(null);
                    *///?}
                    //? if >=1.21 {
                    return food != null && food.nutrition() > 0 && !item.isRemoved();
                    //?} else {
                    /*return food != null && food.getNutrition() > 0 && !item.isRemoved();
                    *///?}
                });

        if (items.isEmpty()) return false;

        // Sort by nutrition descending, then distance ascending as tiebreaker.
        // This ensures cooked foods are preferred over raw even if further away.
        items.sort(Comparator
                .comparingInt((ItemEntity item) -> -getNutrition(item.getItem()))
                .thenComparingDouble(villager::distanceToSqr));

        // Pick the best reachable item
        for (ItemEntity item : items) {
            Path path = villager.getNavigation().createPath(item.blockPosition(), CLOSE_ENOUGH);
            if (path != null && path.canReach()) {
                targetType = TargetType.GROUND_ITEM;
                targetItem = item;
                return true;
            }
        }

        return false;
    }

    private boolean findContainerFood(ServerLevel level, VillagerEntityMCA villager) {
        targetContainerSlot = NearbyItemSources.findBestNearbySlot(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                stack -> {
                    //? if >=1.21 {
                    FoodProperties food = stack.get(DataComponents.FOOD);
                    //?} else {
                    /*FoodProperties food = stack.getFoodProperties(null);
                    *///?}
                    //? if >=1.21 {
                    return food != null && food.nutrition() > 0;
                    //?} else {
                    /*return food != null && food.getNutrition() > 0;
                    *///?}
                },
                stack -> {
                    //? if >=1.21 {
                    FoodProperties food = stack.get(DataComponents.FOOD);
                    //?} else {
                    /*FoodProperties food = stack.getFoodProperties(null);
                    *///?}
                    //? if >=1.21 {
                    return food != null ? food.nutrition() : 0;
                    //?} else {
                    /*return food != null ? food.getNutrition() : 0;
                    *///?}
                });
        if (targetContainerSlot == null) return false;
        // Check reachability
        Path path = villager.getNavigation().createPath(targetContainerSlot.pos(), CLOSE_ENOUGH);
        if (path == null || !path.canReach()) return false;
        targetType = TargetType.CONTAINER;
        targetPos = targetContainerSlot.pos();
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
        // Check reachability
        Path path = villager.getNavigation().createPath(bestPos, CLOSE_ENOUGH);
        if (path == null || !path.canReach()) return false;
        targetType = TargetType.CROP;
        targetPos = bestPos;
        return true;
    }

    private static int getNutrition(ItemStack stack) {
        //? if >=1.21 {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null ? food.nutrition() : 0;
        //?} else {
        /*FoodProperties food = stack.getFoodProperties(null);
        return food != null ? food.getNutrition() : 0;
        *///?}
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
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (targetContainerSlot == null) return;
        ItemStack extracted = NearbyItemSources.extractOne(level, targetContainerSlot);
        if (extracted.isEmpty()) return;
        if (!VillagerEatingManager.startEating(villager, extracted)) {
            villager.getInventory().addItem(extracted);
        }
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
            //? if >=1.21 {
            FoodProperties food = drop.get(DataComponents.FOOD);
            //?} else {
            /*FoodProperties food = drop.getFoodProperties(null);
            *///?}
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
