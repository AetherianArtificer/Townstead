package com.aetherianartificer.townstead.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.ReachableTargetSelector;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.hunger.VillagerEatingManager;
import com.aetherianartificer.townstead.hunger.VillagerSearchCadence;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
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
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Adult villagers can bring drinks to nearby thirsty young villagers.
 * Parents have priority. Non-crabby villagers help if no parent is nearby.
 * Mirrors CareForYoungTask but for the thirst system.
 */
public class HydrateYoungTask extends Behavior<VillagerEntityMCA> {
    private static final String SEARCH_CADENCE_KEY = "care_drink_search";
    private static final String CLAIM_CATEGORY = "consumable";
    private static final int SEARCH_RADIUS = 48;
    private static final int VERTICAL_RADIUS = 8;
    private static final float WALK_SPEED = 0.75f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 1200;
    private static final int FEED_INTERVAL = 30;
    private static final int UNREACHABLE_TARGET_TTL_TICKS = 40;
    private static final int MAX_PATH_ATTEMPTS_PER_SEARCH = 5;

    private enum Phase { NONE, ACQUIRE, HYDRATE }
    private enum SourceType { NONE, GROUND_ITEM, CONTAINER }

    private Phase phase = Phase.NONE;
    private SourceType sourceType = SourceType.NONE;

    private VillagerEntityMCA childTarget;
    private BlockPos sourcePos;
    private ItemEntity sourceItem;
    private NearbyItemSources.ContainerSlot sourceContainerSlot;
    private int cooldown;
    private long nextHydrateTick;

    public HydrateYoungTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA caregiver) {
        if (!TownsteadConfig.isHydratingYoungEnabled()) return false;
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null || !TownsteadConfig.isVillagerThirstEnabled()) return false;
        if (currentScheduleActivity(caregiver) == Activity.REST) return false;
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!VillagerSearchCadence.isDue(level, caregiver, SEARCH_CADENCE_KEY)) return false;

        if (!isEligibleCaregiver(caregiver)) return false;

        Optional<VillagerEntityMCA> optChild = findNearestThirstyYoung(level, caregiver);
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
        sourceContainerSlot = null;
        nextHydrateTick = 0L;

        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null) return;

        if (TownsteadConfig.isSelfInventoryDrinkingEnabled() && hasDrink(caregiver, bridge)) {
            phase = Phase.HYDRATE;
            BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }

        phase = Phase.ACQUIRE;
        if (TownsteadConfig.isGroundItemThirstSourcingEnabled() && findGroundDrink(level, caregiver, bridge)) {
            BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourceItem, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }
        if (TownsteadConfig.isContainerThirstSourcingEnabled() && findContainerDrink(level, caregiver, bridge)) {
            BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourcePos, WALK_SPEED, CLOSE_ENOUGH);
            return;
        }
        cooldown = 100;
        VillagerSearchCadence.schedule(level, caregiver, SEARCH_CADENCE_KEY, cooldown, 30);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        if (childTarget == null || !childTarget.isAlive() || !isYoungThirsty(childTarget)) {
            doStop(level, caregiver, gameTime);
            return;
        }

        if (phase == Phase.ACQUIRE) {
            tickAcquire(level, caregiver, gameTime);
            return;
        }

        if (phase == Phase.HYDRATE) {
            tickHydrate(level, caregiver, gameTime);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        if (!isEligibleCaregiver(caregiver)) return false;
        if (childTarget == null || !childTarget.isAlive() || !isYoungThirsty(childTarget)) return false;
        if (currentScheduleActivity(caregiver) == Activity.REST) return false;
        return true;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        phase = Phase.NONE;
        sourceType = SourceType.NONE;
        childTarget = null;
        sourcePos = null;
        sourceItem = null;
        sourceContainerSlot = null;
        nextHydrateTick = 0L;
        cooldown = 80;
        ConsumableTargetClaims.releaseAll(caregiver.getUUID());
        VillagerSearchCadence.schedule(level, caregiver, SEARCH_CADENCE_KEY, cooldown, 20);
    }

    private void tickAcquire(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null) {
            doStop(level, caregiver, gameTime);
            return;
        }

        if (sourceType == SourceType.GROUND_ITEM) {
            if (sourceItem == null || sourceItem.isRemoved()) {
                doStop(level, caregiver, gameTime);
                return;
            }
            if (caregiver.distanceToSqr(sourceItem) <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                ItemStack stack = sourceItem.getItem();
                //? if >=1.21 {
                ItemStack one = stack.copyWithCount(1);
                //?} else {
                /*ItemStack one = stack.copy(); one.setCount(1);
                *///?}
                if (bridge.itemRestoresThirst(one)) {
                    stack.shrink(1);
                    if (stack.isEmpty()) sourceItem.discard();
                    caregiver.getInventory().addItem(one);
                    phase = Phase.HYDRATE;
                    BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
                } else {
                    doStop(level, caregiver, gameTime);
                }
            }
            return;
        }

        if (sourceType == SourceType.CONTAINER) {
            if (sourcePos == null || sourceContainerSlot == null) {
                doStop(level, caregiver, gameTime);
                return;
            }
            if (caregiver.distanceToSqr(sourcePos.getX() + 0.5, sourcePos.getY() + 0.5, sourcePos.getZ() + 0.5)
                    <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                ItemStack extracted = NearbyItemSources.extractOne(level, sourceContainerSlot);
                if (!extracted.isEmpty() && bridge.itemRestoresThirst(extracted)) {
                    caregiver.getInventory().addItem(extracted);
                    phase = Phase.HYDRATE;
                    BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
                } else {
                    doStop(level, caregiver, gameTime);
                }
            }
        }
    }

    private void tickHydrate(ServerLevel level, VillagerEntityMCA caregiver, long gameTime) {
        if (childTarget == null) {
            doStop(level, caregiver, gameTime);
            return;
        }

        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null) {
            doStop(level, caregiver, gameTime);
            return;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(caregiver, childTarget, WALK_SPEED, CLOSE_ENOUGH);
        double distSq = caregiver.distanceToSqr(childTarget);
        if (distSq > (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) return;
        if (gameTime < nextHydrateTick) return;

        ItemStack drink = findBestDrink(caregiver.getInventory(), bridge);
        if (drink.isEmpty()) {
            phase = Phase.ACQUIRE;
            if (TownsteadConfig.isGroundItemThirstSourcingEnabled() && findGroundDrink(level, caregiver, bridge)) {
                BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourceItem, WALK_SPEED, CLOSE_ENOUGH);
                return;
            }
            if (TownsteadConfig.isContainerThirstSourcingEnabled() && findContainerDrink(level, caregiver, bridge)) {
                BehaviorUtils.setWalkAndLookTargetMemories(caregiver, sourcePos, WALK_SPEED, CLOSE_ENOUGH);
                return;
            }
            doStop(level, caregiver, gameTime);
            return;
        }

        int hydration = bridge.hydration(drink);
        int quenched = bridge.quenched(drink);

        caregiver.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        //? if neoforge {
        CompoundTag childThirst = childTarget.getData(Townstead.THIRST_DATA);
        //?} else {
        /*CompoundTag childThirst = childTarget.getPersistentData().getCompound("townstead_thirst");
        *///?}
        ThirstData.applyDrink(childThirst, hydration, quenched, bridge.extraHydrationToQuenched());
        ThirstData.setLastDrankTime(childThirst, level.getGameTime());
        //? if neoforge {
        childTarget.setData(Townstead.THIRST_DATA, childThirst);
        //?} else {
        /*childTarget.getPersistentData().put("townstead_thirst", childThirst);
        *///?}

        ItemStack remainder = bridge.onDrinkConsumed(drink);
        if (remainder.isEmpty()) {
            drink.shrink(1);
        } else if (remainder != drink) {
            drink.shrink(1);
            caregiver.getInventory().addItem(remainder);
        }

        nextHydrateTick = gameTime + FEED_INTERVAL;
        if (!isYoungThirsty(childTarget)) {
            doStop(level, caregiver, gameTime);
        }
    }

    private boolean isEligibleCaregiver(VillagerEntityMCA caregiver) {
        if (!isAdult(caregiver)) return false;

        VillagerBrain<?> brain = caregiver.getVillagerBrain();
        if (brain.isPanicking() || caregiver.getLastHurtByMob() != null) {
            return false;
        }
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            //? if neoforge {
            CompoundTag fatigueTag = caregiver.getData(Townstead.FATIGUE_DATA);
            //?} else {
            /*CompoundTag fatigueTag = caregiver.getPersistentData().getCompound("townstead_fatigue");
            *///?}
            if (FatigueData.getFatigue(fatigueTag) >= FatigueData.COLLAPSE_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    private Optional<VillagerEntityMCA> findNearestThirstyYoung(ServerLevel level, VillagerEntityMCA caregiver) {
        AABB box = caregiver.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<VillagerEntityMCA> candidates = level.getEntitiesOfClass(VillagerEntityMCA.class, box,
                v -> v.isAlive() && v != caregiver && isYoungThirsty(v));

        return candidates.stream()
                .filter(child -> mayCareFor(caregiver, child))
                .min(Comparator.comparingDouble(caregiver::distanceToSqr));
    }

    private boolean mayCareFor(VillagerEntityMCA caregiver, VillagerEntityMCA child) {
        if (isParentOf(caregiver, child)) return true;
        if (!TownsteadConfig.ENABLE_NON_PARENT_CAREGIVERS.get()) return false;
        //? if neoforge {
        if (caregiver.getVillagerBrain().getPersonality() == Personality.CRABBY) return false;
        //?} else {
        /*if (caregiver.getVillagerBrain().getPersonality() == Personality.GRUMPY) return false;
        *///?}
        return !parentsNearby(child);
    }

    private boolean parentsNearby(VillagerEntityMCA child) {
        Stream<Entity> parents = child.getRelationships().getParents();
        return parents.anyMatch(parent ->
                parent instanceof VillagerEntityMCA villager
                        && villager.isAlive()
                        && villager.level() == child.level()
                        && villager.distanceToSqr(child) <= (SEARCH_RADIUS * SEARCH_RADIUS));
    }

    private boolean isParentOf(VillagerEntityMCA caregiver, VillagerEntityMCA child) {
        return child.getRelationships().getParents()
                .anyMatch(parent -> parent.getUUID().equals(caregiver.getUUID()));
    }

    private boolean isAdult(VillagerEntityMCA villager) {
        AgeState ageState = AgeState.byCurrentAge(villager.getAge());
        return ageState == AgeState.ADULT || ageState == AgeState.TEEN;
    }

    private boolean isYoungThirsty(VillagerEntityMCA villager) {
        AgeState ageState = AgeState.byCurrentAge(villager.getAge());
        if (!(ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD)) {
            return false;
        }
        //? if neoforge {
        CompoundTag thirst = villager.getData(Townstead.THIRST_DATA);
        //?} else {
        /*CompoundTag thirst = villager.getPersistentData().getCompound("townstead_thirst");
        *///?}
        return ThirstData.getThirst(thirst) < ThirstData.ADEQUATE_THRESHOLD;
    }

    private boolean hasDrink(VillagerEntityMCA villager, ThirstCompatBridge bridge) {
        return !findBestDrink(villager.getInventory(), bridge).isEmpty();
    }

    private ItemStack findBestDrink(SimpleContainer inventory, ThirstCompatBridge bridge) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            int score = thirstScore(stack, bridge);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        return best;
    }

    private int thirstScore(ItemStack stack, ThirstCompatBridge bridge) {
        if (stack.isEmpty() || !bridge.itemRestoresThirst(stack)) return 0;
        int quenched = Math.max(0, bridge.quenched(stack));
        int hydration = Math.max(0, bridge.hydration(stack));
        int purity = bridge.isPurityWaterContainer(stack) ? Math.max(0, bridge.purity(stack)) : 0;
        return purity * 10_000 + quenched * 100 + hydration * 10 + (bridge.isDrink(stack) ? 1 : 0);
    }

    private boolean findGroundDrink(ServerLevel level, VillagerEntityMCA villager, ThirstCompatBridge bridge) {
        AABB searchBox = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> thirstScore(item.getItem(), bridge) > 0 && !item.isRemoved());
        if (items.isEmpty()) return false;

        items.sort(Comparator
                .comparingInt((ItemEntity item) -> -thirstScore(item.getItem(), bridge))
                .thenComparingDouble(villager::distanceToSqr));

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
            sourceItem = chosen;
            sourceType = SourceType.GROUND_ITEM;
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
        sourceContainerSlot = chosen;
        sourceType = SourceType.CONTAINER;
        sourcePos = chosen.pos();
        return true;
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }
}
