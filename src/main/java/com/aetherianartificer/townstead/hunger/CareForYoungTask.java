package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Adult villagers can feed nearby young villagers.
 * Parents have priority. Non-crabby villagers help if no parent is nearby.
 */
public class CareForYoungTask extends Behavior<VillagerEntityMCA> {
    private static final int SEARCH_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 4;
    private static final float WALK_SPEED = 0.75f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 1200;
    private static final int FEED_INTERVAL = 30;

    private enum Phase { NONE, ACQUIRE, FEED }
    private enum SourceType { NONE, GROUND_ITEM, CONTAINER, CROP }

    private Phase phase = Phase.NONE;
    private SourceType sourceType = SourceType.NONE;

    private VillagerEntityMCA childTarget;
    private BlockPos sourcePos;
    private ItemEntity sourceItem;
    private Container sourceContainer;
    private boolean sourceIsItemHandler;
    private int sourceSlot;
    private int cooldown;
    private long nextFeedTick;

    public CareForYoungTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA caregiver) {
        if (!TownsteadConfig.ENABLE_FEEDING_YOUNG.get()) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        if (!townstead$isEligibleCaregiver(caregiver)) return false;

        Optional<VillagerEntityMCA> optChild = townstead$findNearestNeedyYoung(level, caregiver);
        if (optChild.isEmpty()) return false;
        childTarget = optChild.get();

        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        phase = Phase.NONE;
        sourceType = SourceType.NONE;
        sourcePos = null;
        sourceItem = null;
        sourceContainer = null;
        sourceIsItemHandler = false;
        sourceSlot = -1;
        nextFeedTick = 0L;

        if (TownsteadConfig.ENABLE_SELF_INVENTORY_EATING.get() && townstead$hasFood(caregiver)) {
            phase = Phase.FEED;
            BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        phase = Phase.ACQUIRE;
        if (TownsteadConfig.ENABLE_GROUND_ITEM_SOURCING.get() && townstead$findGroundItem(level, caregiver)) {
            BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourceItem, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }
        if (TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() && townstead$findContainerFood(level, caregiver)) {
            BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourcePos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }
        if (TownsteadConfig.ENABLE_CROP_SOURCING.get() && townstead$findMatureCrop(level, caregiver)) {
            BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourcePos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }
        cooldown = 100;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        if (childTarget == null || !childTarget.isAlive() || !townstead$isYoungHungry(childTarget)) {
            doStop(level, caregiver, gameTime);
            return;
        }

        if (phase == Phase.ACQUIRE) {
            townstead$tickAcquire(level, caregiver, gameTime);
            return;
        }

        if (phase == Phase.FEED) {
            townstead$tickFeed(level, caregiver, gameTime);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        if (!townstead$isEligibleCaregiver(caregiver)) return false;
        if (childTarget == null || !childTarget.isAlive() || !townstead$isYoungHungry(childTarget)) return false;
        return true;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        phase = Phase.NONE;
        sourceType = SourceType.NONE;
        childTarget = null;
        sourcePos = null;
        sourceItem = null;
        sourceContainer = null;
        sourceIsItemHandler = false;
        sourceSlot = -1;
        nextFeedTick = 0L;
        cooldown = 80;
    }

    private void townstead$tickAcquire(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        if (sourceType == SourceType.GROUND_ITEM) {
            if (sourceItem == null || sourceItem.isRemoved()) {
                doStop(level, caregiver, gameTime);
                return;
            }
            if (caregiver.distanceToSqr(sourceItem) <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                ItemStack stack = sourceItem.getItem();
                ItemStack one = stack.copyWithCount(1);
                if (townstead$isFood(one)) {
                    stack.shrink(1);
                    if (stack.isEmpty()) sourceItem.discard();
                    caregiver.getInventory().addItem(one);
                    phase = Phase.FEED;
                    BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
                } else {
                    doStop(level, caregiver, gameTime);
                }
            }
            return;
        }

        if (sourceType == SourceType.CONTAINER) {
            if (sourcePos == null || sourceSlot < 0) {
                doStop(level, caregiver, gameTime);
                return;
            }
            if (caregiver.distanceToSqr(sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5)
                    <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                if (sourceIsItemHandler) {
                    IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, null);
                    if (itemHandler == null) {
                        doStop(level, caregiver, gameTime);
                        return;
                    }
                    ItemStack extracted = itemHandler.extractItem(sourceSlot, 1, false);
                    if (!extracted.isEmpty() && townstead$isFood(extracted)) {
                        caregiver.getInventory().addItem(extracted);
                        phase = Phase.FEED;
                        BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
                    } else {
                        doStop(level, caregiver, gameTime);
                    }
                } else {
                    if (sourceContainer == null) {
                        doStop(level, caregiver, gameTime);
                        return;
                    }
                    ItemStack stack = sourceContainer.getItem(sourceSlot);
                    ItemStack one = stack.copyWithCount(1);
                    if (townstead$isFood(one)) {
                        stack.shrink(1);
                        sourceContainer.setChanged();
                        caregiver.getInventory().addItem(one);
                        phase = Phase.FEED;
                        BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
                    } else {
                        doStop(level, caregiver, gameTime);
                    }
                }
            }
            return;
        }

        if (sourceType == SourceType.CROP) {
            if (sourcePos == null) {
                doStop(level, caregiver, gameTime);
                return;
            }
            if (caregiver.distanceToSqr(sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5)
                    <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                BlockState state = level.getBlockState(sourcePos);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                    List<ItemStack> drops = CropBlock.getDrops(state, level, sourcePos, null);
                    level.destroyBlock(sourcePos, false, caregiver);
                    for (ItemStack drop : drops) {
                        caregiver.getInventory().addItem(drop);
                    }
                }
                if (townstead$hasFood(caregiver)) {
                    phase = Phase.FEED;
                    BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
                } else {
                    doStop(level, caregiver, gameTime);
                }
            }
        }
    }

    private void townstead$tickFeed(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        if (childTarget == null) {
            doStop(level, caregiver, gameTime);
            return;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
        double distSq = caregiver.distanceToSqr(childTarget);
        if (distSq > (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) return;
        if (gameTime < nextFeedTick) return;

        ItemStack food = townstead$findBestFood(caregiver.getInventory());
        if (food.isEmpty()) {
            phase = Phase.ACQUIRE;
            if (TownsteadConfig.ENABLE_GROUND_ITEM_SOURCING.get() && townstead$findGroundItem(level, caregiver)) {
                BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourceItem, WALK_SPEED, CLOSE_ENOUGH);
                return;
            }
            if (TownsteadConfig.ENABLE_CONTAINER_SOURCING.get() && townstead$findContainerFood(level, caregiver)) {
                BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourcePos, WALK_SPEED, CLOSE_ENOUGH);
                return;
            }
            if (TownsteadConfig.ENABLE_CROP_SOURCING.get() && townstead$findMatureCrop(level, caregiver)) {
                BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourcePos, WALK_SPEED, CLOSE_ENOUGH);
                return;
            }
            doStop(level, caregiver, gameTime);
            return;
        }

        FoodProperties props = food.get(DataComponents.FOOD);
        if (props == null) {
            doStop(level, caregiver, gameTime);
            return;
        }

        food.shrink(1);
        caregiver.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        CompoundTag childHunger = childTarget.getData(Townstead.HUNGER_DATA);
        HungerData.applyFood(childHunger, props);
        HungerData.setLastAteTime(childHunger, level.getGameTime());
        childTarget.setData(Townstead.HUNGER_DATA, childHunger);

        nextFeedTick = gameTime + FEED_INTERVAL;
        if (!townstead$isYoungHungry(childTarget)) {
            doStop(level, caregiver, gameTime);
        }
    }

    private boolean townstead$isEligibleCaregiver(VillagerEntityMCA caregiver) {
        if (!townstead$isAdult(caregiver)) return false;

        VillagerBrain<?> brain = caregiver.getVillagerBrain();
        if (brain.isPanicking() || caregiver.getLastHurtByMob() != null) {
            return false;
        }
        return true;
    }

    private Optional<VillagerEntityMCA> townstead$findNearestNeedyYoung(ServerLevel level, VillagerEntityMCA caregiver) {
        AABB box = caregiver.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<VillagerEntityMCA> candidates = level.getEntitiesOfClass(VillagerEntityMCA.class, box,
                v -> v.isAlive() && v != caregiver && townstead$isYoungHungry(v));

        return candidates.stream()
                .filter(child -> townstead$mayCareFor(caregiver, child))
                .min(Comparator.comparingDouble(caregiver::distanceToSqr));
    }

    private boolean townstead$mayCareFor(VillagerEntityMCA caregiver, VillagerEntityMCA child) {
        if (townstead$isParentOf(caregiver, child)) return true;
        if (!TownsteadConfig.ENABLE_NON_PARENT_CAREGIVERS.get()) return false;
        if (caregiver.getVillagerBrain().getPersonality() == Personality.CRABBY) return false;
        return !townstead$parentsNearby(child);
    }

    private boolean townstead$parentsNearby(VillagerEntityMCA child) {
        Stream<Entity> parents = child.getRelationships().getParents();
        return parents.anyMatch(parent ->
                parent instanceof VillagerEntityMCA villager
                        && villager.isAlive()
                        && villager.level() == child.level()
                        && villager.distanceToSqr(child) <= (SEARCH_RADIUS * SEARCH_RADIUS));
    }

    private boolean townstead$isParentOf(VillagerEntityMCA caregiver, VillagerEntityMCA child) {
        return child.getRelationships().getParents()
                .anyMatch(parent -> parent.getUUID().equals(caregiver.getUUID()));
    }

    private boolean townstead$isAdult(VillagerEntityMCA villager) {
        AgeState ageState = AgeState.byCurrentAge(villager.getAge());
        return ageState == AgeState.ADULT || ageState == AgeState.TEEN;
    }

    private boolean townstead$isYoungHungry(VillagerEntityMCA villager) {
        AgeState ageState = AgeState.byCurrentAge(villager.getAge());
        if (!(ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD)) {
            return false;
        }
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        return HungerData.getHunger(hunger) < HungerData.ADEQUATE_THRESHOLD;
    }

    private boolean townstead$hasFood(VillagerEntityMCA villager) {
        return !townstead$findBestFood(villager.getInventory()).isEmpty();
    }

    private ItemStack townstead$findBestFood(SimpleContainer inv) {
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
        return best;
    }

    private boolean townstead$isFood(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null && food.nutrition() > 0;
    }

    private boolean townstead$findGroundItem(ServerLevel level, VillagerEntityMCA villager) {
        AABB searchBox = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> !item.isRemoved() && townstead$isFood(item.getItem()));
        if (items.isEmpty()) return false;

        sourceItem = items.stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);
        if (sourceItem == null) return false;
        sourceType = SourceType.GROUND_ITEM;
        return true;
    }

    private boolean townstead$findContainerFood(ServerLevel level, VillagerEntityMCA villager) {
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
                    if (food == null || food.nutrition() <= 0) continue;

                    double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
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

            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (itemHandler != null) {
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    ItemStack stack = itemHandler.getStackInSlot(i);
                    FoodProperties food = stack.get(DataComponents.FOOD);
                    if (food == null || food.nutrition() <= 0) continue;

                    double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
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
        sourceType = SourceType.CONTAINER;
        sourcePos = bestPos;
        sourceContainer = bestContainer;
        sourceIsItemHandler = bestIsItemHandler;
        sourceSlot = bestSlot;
        return true;
    }

    private boolean townstead$findMatureCrop(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos center = villager.blockPosition();
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-SEARCH_RADIUS, -VERTICAL_RADIUS, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;

            double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                bestPos = pos.immutable();
            }
        }

        if (bestPos == null) return false;
        sourceType = SourceType.CROP;
        sourcePos = bestPos;
        return true;
    }
}
