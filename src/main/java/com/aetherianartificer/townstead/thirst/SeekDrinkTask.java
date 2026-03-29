package com.aetherianartificer.townstead.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.ReachableTargetSelector;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.hunger.TargetReachabilityCache;
import com.aetherianartificer.townstead.hunger.VillagerSearchCadence;
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
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SeekDrinkTask extends Behavior<VillagerEntityMCA> {
    private static final String SEARCH_CADENCE_KEY = "drink_search";
    private static final String CLAIM_CATEGORY = "consumable";

    private static final int SEARCH_RADIUS = 48;
    private static final int VERTICAL_RADIUS = 8;
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 600;
    private static final int UNREACHABLE_TARGET_TTL_TICKS = 40;
    private static final int MAX_PATH_ATTEMPTS_PER_SEARCH = 5;

    private enum TargetType { NONE, GROUND_ITEM, CONTAINER }

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
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null || !TownsteadConfig.isVillagerThirstEnabled()) return false;
        if (VillagerEatingManager.isEating(villager) || VillagerDrinkingManager.isDrinking(villager)) return false;
        if (currentScheduleActivity(villager) == Activity.REST) return false;

        // Only block when fleeing from a mob — not during environmental panic
        // (thirst damage), otherwise villagers enter a death spiral.
        if (villager.getLastHurtByMob() != null) return false;

        // Don't interrupt an existing walk target unless critically thirsty
        if (villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            //? if neoforge {
            int quickCheck = ThirstData.getThirst(villager.getData(Townstead.THIRST_DATA));
            //?} else {
            /*int quickCheck = ThirstData.getThirst(villager.getPersistentData().getCompound("townstead_thirst"));
            *///?}
            if (quickCheck >= ThirstData.EMERGENCY_THRESHOLD) return false;
        }

        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!VillagerSearchCadence.isDue(level, villager, SEARCH_CADENCE_KEY)) return false;

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
            VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 20);
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

        ThirstCompatBridge bridge = ThirstBridgeResolver.get();

        if (TownsteadConfig.isGroundItemThirstSourcingEnabled() && findGroundDrink(level, villager, bridge)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetItem, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        if (TownsteadConfig.isContainerThirstSourcingEnabled() && findContainerDrink(level, villager, bridge)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        cooldown = 200;
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 40);
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
            case NONE -> { }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetType == TargetType.NONE) return false;
        if (targetType == TargetType.GROUND_ITEM && (targetItem == null || targetItem.isRemoved())) return false;
        if (villager.getLastHurtByMob() != null) return false;
        if (currentScheduleActivity(villager) == Activity.REST) return false;
        return true;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetType = TargetType.NONE;
        targetPos = null;
        targetItem = null;
        targetContainerSlot = null;
        ConsumableTargetClaims.releaseAll(villager.getUUID());
        //? if neoforge {
        CompoundTag thirst = villager.getData(Townstead.THIRST_DATA);
        //?} else {
        /*CompoundTag thirst = villager.getPersistentData().getCompound("townstead_thirst");
        *///?}
        cooldown = (ThirstData.isDrinkingMode(thirst) || ThirstData.getThirst(thirst) < ThirstData.ADEQUATE_THRESHOLD) ? 5 : 200;
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 20);
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
        ItemStack remainder = bridge.onDrinkConsumed(best);
        if (remainder.isEmpty()) {
            best.shrink(1);
        }
        return true;
    }

    private boolean findGroundDrink(ServerLevel level, VillagerEntityMCA villager, ThirstCompatBridge bridge) {
        AABB searchBox = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> thirstScore(item.getItem(), bridge) > 0 && !item.isRemoved());
        if (items.isEmpty()) return false;

        // Sort by thirst score descending, then distance ascending
        items.sort(Comparator
                .comparingInt((ItemEntity item) -> -thirstScore(item.getItem(), bridge))
                .thenComparingDouble(villager::distanceToSqr));

        // Pick the best reachable item
        ItemEntity chosen = ReachableTargetSelector.chooseReachable(
                level,
                villager,
                items.stream().filter(item -> !ConsumableTargetClaims.isClaimedByOtherItem(level, villager.getUUID(), CLAIM_CATEGORY, item))
                        .map(item -> new ReachableTargetSelector.Candidate<>(item, item.blockPosition()))
                        .toList(),
                CLOSE_ENOUGH,
                MAX_PATH_ATTEMPTS_PER_SEARCH,
                UNREACHABLE_TARGET_TTL_TICKS,
                candidate -> villager.distanceToSqr(candidate.pos().getX() + 0.5, candidate.pos().getY() + 0.5, candidate.pos().getZ() + 0.5)
        );
        if (chosen != null) {
            if (!ConsumableTargetClaims.tryClaimItem(level, villager.getUUID(), CLAIM_CATEGORY, chosen, level.getGameTime() + MAX_DURATION + 20L)) {
                return false;
            }
            targetType = TargetType.GROUND_ITEM;
            targetItem = chosen;
            return true;
        }
        return false;
    }

    private boolean findContainerDrink(ServerLevel level, VillagerEntityMCA villager, ThirstCompatBridge bridge) {
        List<ReachableTargetSelector.Candidate<NearbyItemSources.ContainerSlot>> candidates = new ArrayList<>();
        NearbyItemSources.collectMatchingSlots(
                level,
                villager,
                SEARCH_RADIUS,
                VERTICAL_RADIUS,
                stack -> thirstScore(stack, bridge) > 0,
                stack -> thirstScore(stack, bridge),
                villager.blockPosition(),
                slot -> {
                    if (!ConsumableTargetClaims.isClaimedByOtherSlot(level, villager.getUUID(), CLAIM_CATEGORY, slot)) {
                        candidates.add(new ReachableTargetSelector.Candidate<>(slot, slot.pos()));
                    }
                }
        );
        NearbyItemSources.ContainerSlot chosen = ReachableTargetSelector.chooseReachable(
                level, villager, candidates, CLOSE_ENOUGH, MAX_PATH_ATTEMPTS_PER_SEARCH,
                UNREACHABLE_TARGET_TTL_TICKS,
                candidate -> candidate.value().distanceSqr()
        );
        if (chosen == null) return false;
        if (!ConsumableTargetClaims.tryClaimSlot(level, villager.getUUID(), CLAIM_CATEGORY, chosen, level.getGameTime() + MAX_DURATION + 20L)) {
            return false;
        }
        targetContainerSlot = chosen;
        targetType = TargetType.CONTAINER;
        targetPos = chosen.pos();
        return true;
    }

    private void pickUpAndDrink(VillagerEntityMCA villager, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (!VillagerDrinkingManager.startDrinking(villager, stack)) return;
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        ItemStack remainder = bridge != null ? bridge.onDrinkConsumed(stack) : ItemStack.EMPTY;
        if (remainder.isEmpty()) {
            stack.shrink(1);
            if (stack.isEmpty()) itemEntity.discard();
        } else {
            // Container item (e.g. canteen) — pick it up into inventory
            itemEntity.discard();
            villager.getInventory().addItem(remainder);
        }
    }

    private void takeFromContainerAndDrink(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (targetContainerSlot == null) return;
        ItemStack extracted = NearbyItemSources.extractOne(level, targetContainerSlot);
        if (extracted.isEmpty()) return;
        if (!VillagerDrinkingManager.startDrinking(villager, extracted)) {
            villager.getInventory().addItem(extracted);
            return;
        }
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        ItemStack remainder = bridge != null ? bridge.onDrinkConsumed(extracted) : ItemStack.EMPTY;
        if (!remainder.isEmpty()) {
            villager.getInventory().addItem(remainder);
        }
    }

    private int thirstScore(ItemStack stack, ThirstCompatBridge bridge) {
        if (stack.isEmpty() || !bridge.itemRestoresThirst(stack)) return 0;
        int quenched = Math.max(0, bridge.quenched(stack));
        int hydration = Math.max(0, bridge.hydration(stack));
        int purity = bridge.isPurityWaterContainer(stack) ? Math.max(0, bridge.purity(stack)) : 0;
        return purity * 10_000 + quenched * 100 + hydration * 10 + (bridge.isDrink(stack) ? 1 : 0);
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }
}
