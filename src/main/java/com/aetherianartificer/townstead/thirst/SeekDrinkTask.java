package com.aetherianartificer.townstead.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstWasTakenBridge;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.hunger.VillagerEatingManager;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class SeekDrinkTask extends Behavior<VillagerEntityMCA> {

    private static final int SEARCH_RADIUS = 16;
    private static final int VERTICAL_RADIUS = 4;
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 600;

    private enum TargetType { NONE, GROUND_ITEM, CONTAINER, CROP }

    private TargetType targetType = TargetType.NONE;
    private BlockPos targetPos;
    private ItemEntity targetItem;
    private NearbyItemSources.ContainerSlot targetContainerSlot;
    private int cooldown;

    public SeekDrinkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        ThirstCompatBridge bridge = ThirstWasTakenBridge.INSTANCE;
        if (!bridge.isActive() || !TownsteadConfig.isVillagerThirstEnabled()) return false;
        if (VillagerEatingManager.isEating(villager) || VillagerDrinkingManager.isDrinking(villager)) return false;

        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;

        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        //? if neoforge {
        CompoundTag thirst = villager.getData(Townstead.THIRST_DATA);
        //?} else {
        /*CompoundTag thirst = villager.getPersistentData().getCompound("townstead_thirst");
        *///?}
        int t = ThirstData.getThirst(thirst);
        boolean drinkingMode = ThirstData.isDrinkingMode(thirst);
        if (t >= ThirstData.LUNCH_THRESHOLD && !drinkingMode) return false;

        long gameTime = level.getGameTime();
        long lastDrank = ThirstData.getLastDrankTime(thirst);
        long minInterval = (drinkingMode || t <= ThirstData.EMERGENCY_THRESHOLD) ? 20L : ThirstData.MIN_DRINK_INTERVAL;
        if ((gameTime - lastDrank) < minInterval) return false;

        if (TownsteadConfig.isSelfInventoryDrinkingEnabled() && tryDrinkFromInventory(villager, bridge)) {
            cooldown = (drinkingMode || t < ThirstData.ADEQUATE_THRESHOLD) ? 5 : 200;
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

        ThirstCompatBridge bridge = ThirstWasTakenBridge.INSTANCE;

        if (TownsteadConfig.isGroundItemThirstSourcingEnabled() && findGroundDrink(level, villager, bridge)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetItem, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        if (TownsteadConfig.isContainerThirstSourcingEnabled() && findContainerDrink(level, villager, bridge)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        if (TownsteadConfig.isCropThirstSourcingEnabled() && findMatureCrop(level, villager)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

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
                    pickUpAndDrink(villager, targetItem);
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
                    takeFromContainerAndDrink(villager);
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
                    harvestCropAndDrink(level, villager);
                    doStop(level, villager, gameTime);
                }
            }
            case NONE -> { }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetType == TargetType.NONE) return false;
        if (targetType == TargetType.GROUND_ITEM && (targetItem == null || targetItem.isRemoved())) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        return !brain.isPanicking() && villager.getLastHurtByMob() == null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetType = TargetType.NONE;
        targetPos = null;
        targetItem = null;
        targetContainerSlot = null;
        //? if neoforge {
        CompoundTag thirst = villager.getData(Townstead.THIRST_DATA);
        //?} else {
        /*CompoundTag thirst = villager.getPersistentData().getCompound("townstead_thirst");
        *///?}
        cooldown = (ThirstData.isDrinkingMode(thirst) || ThirstData.getThirst(thirst) < ThirstData.ADEQUATE_THRESHOLD) ? 5 : 200;
    }

    private boolean tryDrinkFromInventory(VillagerEntityMCA villager, ThirstCompatBridge bridge) {
        SimpleContainer inv = villager.getInventory();
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            int score = thirstScore(stack, bridge);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }

        if (best.isEmpty() || bestScore <= 0) return false;
        if (!VillagerDrinkingManager.startDrinking(villager, best)) return false;
        best.shrink(1);
        return true;
    }

    private boolean findGroundDrink(ServerLevel level, VillagerEntityMCA villager, ThirstCompatBridge bridge) {
        AABB searchBox = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> thirstScore(item.getItem(), bridge) > 0 && !item.isRemoved());
        if (items.isEmpty()) return false;

        ItemEntity best = null;
        int bestScore = Integer.MIN_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            int score = thirstScore(item.getItem(), bridge);
            double dist = villager.distanceToSqr(item);
            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                bestScore = score;
                bestDist = dist;
                best = item;
            }
        }

        if (best == null) return false;
        targetType = TargetType.GROUND_ITEM;
        targetItem = best;
        return true;
    }

    private boolean findContainerDrink(ServerLevel level, VillagerEntityMCA villager, ThirstCompatBridge bridge) {
        targetContainerSlot = NearbyItemSources.findBestNearbySlot(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                stack -> thirstScore(stack, bridge) > 0,
                stack -> thirstScore(stack, bridge));
        if (targetContainerSlot == null) return false;
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
        targetType = TargetType.CROP;
        targetPos = bestPos;
        return true;
    }

    private void pickUpAndDrink(VillagerEntityMCA villager, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (!VillagerDrinkingManager.startDrinking(villager, stack)) return;
        stack.shrink(1);
        if (stack.isEmpty()) itemEntity.discard();
    }

    private void takeFromContainerAndDrink(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (targetContainerSlot == null) return;
        ItemStack extracted = NearbyItemSources.extractOne(level, targetContainerSlot);
        if (extracted.isEmpty()) return;
        if (!VillagerDrinkingManager.startDrinking(villager, extracted)) {
            villager.getInventory().addItem(extracted);
        }
    }

    private void harvestCropAndDrink(ServerLevel level, VillagerEntityMCA villager) {
        if (targetPos == null) return;
        ThirstCompatBridge bridge = ThirstWasTakenBridge.INSTANCE;

        BlockState state = level.getBlockState(targetPos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;

        List<ItemStack> drops = CropBlock.getDrops(state, level, targetPos, null);
        level.destroyBlock(targetPos, false, villager);

        ItemStack bestDrink = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;

        for (ItemStack drop : drops) {
            int score = thirstScore(drop, bridge);
            if (score > bestScore) {
                bestScore = score;
                bestDrink = drop;
            }
        }

        boolean drank = false;
        for (ItemStack drop : drops) {
            if (!drank && drop == bestDrink && thirstScore(drop, bridge) > 0) {
                if (VillagerDrinkingManager.startDrinking(villager, drop)) {
                    drop.shrink(1);
                    drank = true;
                }
            }
            if (!drop.isEmpty()) {
                villager.getInventory().addItem(drop);
            }
        }

        if (drank) cooldown = 200;
    }

    private int thirstScore(ItemStack stack, ThirstCompatBridge bridge) {
        if (stack.isEmpty() || !bridge.itemRestoresThirst(stack)) return 0;
        int quenched = Math.max(0, bridge.quenched(stack));
        int hydration = Math.max(0, bridge.hydration(stack));
        int purity = bridge.isPurityWaterContainer(stack) ? Math.max(0, bridge.purity(stack)) : 0;
        return purity * 10_000 + quenched * 100 + hydration * 10 + (bridge.isDrink(stack) ? 1 : 0);
    }
}
