package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.ReachableTargetSelector;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Unified "refuel" behavior: replaces the old SeekFoodTask + SeekDrinkTask. Consuming is one
 * mechanic here; hunger vs thirst is only the selection criteria for what to fetch/eat.
 *
 * <p>A villager starts a session when genuinely low on either need (hysteresis: low bar to start,
 * high bar to stop), then sits and consumes to satiety on BOTH needs in one sitting, preferring
 * carried rations (eat in place, no pathing) and only making a trip when out of stock. This removes
 * the old nibble-and-run grazing.
 */
public class RefuelTask extends Behavior<VillagerEntityMCA> {
    private static final String SEARCH_CADENCE_KEY = "refuel_search";
    private static final String CLAIM_CATEGORY = "consumable";

    private static final int SEARCH_RADIUS = 48;
    private static final int VERTICAL_RADIUS = 8;
    private static final float WALK_SPEED = 0.6f;
    private static final int CLOSE_ENOUGH = 2;
    private static final int MAX_DURATION = 1200;
    private static final int UNREACHABLE_TARGET_TTL_TICKS = 40;
    private static final int MAX_PATH_ATTEMPTS_PER_SEARCH = 5;

    // How many spare rations to pull on a supply trip so the next session is in-place (B).
    private static final int RATION_GRAB = 4;
    // Refractory floor after a session so it can't immediately re-arm.
    private static final int REFRACTORY_TICKS = 600;

    private enum Phase { ACQUIRE, CONSUME }
    private enum TargetType { NONE, GROUND_ITEM, CONTAINER, CROP }
    private enum Need { FOOD, DRINK }

    private Phase phase = Phase.CONSUME;
    private TargetType targetType = TargetType.NONE;
    private Need acquiring = Need.FOOD;
    private BlockPos targetPos;
    private ItemEntity targetItem;
    private NearbyItemSources.ContainerSlot targetContainerSlot;
    // Container a session's rations were pulled from, so emptied containers go back there.
    private BlockPos sessionSource;
    private int cooldown;

    public RefuelTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    // --- Need helpers ---

    private static boolean hungerOn() {
        return TownsteadConfig.isVillagerHungerEnabled();
    }

    private static boolean thirstOn() {
        return TownsteadConfig.isVillagerThirstEnabled() && ThirstBridgeResolver.get() != null;
    }

    /** Below the start threshold for some active need (genuinely hungry/thirsty). */
    private static boolean shouldStart(TownsteadVillager.Needs needs) {
        return (hungerOn() && needs.hunger() <= HungerData.ADEQUATE_THRESHOLD)
                || (thirstOn() && needs.thirst() <= ThirstData.ADEQUATE_THRESHOLD);
    }

    private static boolean emergency(TownsteadVillager.Needs needs) {
        return (hungerOn() && needs.hunger() <= HungerData.EMERGENCY_THRESHOLD)
                || (thirstOn() && needs.thirst() <= ThirstData.EMERGENCY_THRESHOLD);
    }

    /** Still has room to eat (below satiety). */
    private static boolean wantsFood(TownsteadVillager.Needs needs) {
        return hungerOn() && needs.hunger() < HungerData.SATIETY_THRESHOLD;
    }

    private static boolean wantsDrink(TownsteadVillager.Needs needs) {
        return thirstOn() && needs.thirst() < ThirstData.SATIETY_THRESHOLD;
    }

    // --- Lifecycle ---

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!hungerOn() && !thirstOn()) return false;
        if (VillagerConsumptionManager.isConsuming(villager)) return false;
        if (currentScheduleActivity(villager) == Activity.REST) return false;
        if (villager.getLastHurtByMob() != null) return false;

        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();

        // Don't interrupt an existing walk target unless an emergency.
        if (villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent() && !emergency(needs)) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!VillagerSearchCadence.isDue(level, villager, SEARCH_CADENCE_KEY)) return false;

        // Outside of breaks, only an emergency pulls a villager off-task.
        if (currentScheduleActivity(villager) == Activity.WORK && !emergency(needs)) return false;

        return shouldStart(needs);
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetType = TargetType.NONE;
        targetPos = null;
        targetItem = null;
        targetContainerSlot = null;
        sessionSource = null;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();

        // Eat carried rations in place if we can; otherwise go fetch.
        if (hasRationFor(villager, needs)) {
            phase = Phase.CONSUME;
            return;
        }
        if (beginAcquire(level, villager, needs)) {
            phase = Phase.ACQUIRE;
            return;
        }
        // Nothing carried and nothing reachable — back off.
        cooldown = 200;
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 40);
        phase = Phase.CONSUME; // canStillUse will end it (no rations, no target)
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        switch (phase) {
            case ACQUIRE -> tickAcquire(level, villager, needs, gameTime);
            case CONSUME -> tickConsume(level, villager, needs, gameTime);
        }
    }

    private void tickAcquire(ServerLevel level, VillagerEntityMCA villager, TownsteadVillager.Needs needs, long gameTime) {
        switch (targetType) {
            case GROUND_ITEM -> {
                if (targetItem == null || targetItem.isRemoved()) { doStop(level, villager, gameTime); return; }
                BehaviorUtils.setWalkAndLookTargetMemories(villager, targetItem, WALK_SPEED, CLOSE_ENOUGH);
                if (villager.distanceToSqr(targetItem) <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                    grabFromGround(villager);
                    phase = Phase.CONSUME;
                }
            }
            case CONTAINER, CROP -> {
                if (targetPos == null) { doStop(level, villager, gameTime); return; }
                BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
                double distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distSq <= (CLOSE_ENOUGH + 1) * (CLOSE_ENOUGH + 1)) {
                    if (targetType == TargetType.CONTAINER) grabFromContainer(villager);
                    else harvestCrop(level, villager);
                    phase = Phase.CONSUME;
                }
            }
            case NONE -> doStop(level, villager, gameTime);
        }
    }

    private void tickConsume(ServerLevel level, VillagerEntityMCA villager, TownsteadVillager.Needs needs, long gameTime) {
        if (VillagerConsumptionManager.isConsuming(villager)) return; // current bite/sip in progress

        boolean food = wantsFood(needs);
        boolean drink = wantsDrink(needs);
        if (!food && !drink) { doStop(level, villager, gameTime); return; } // satiated

        // Consume a carried ration for an unsatisfied need (food first).
        if (food) {
            int slot = bestFoodSlot(villager.getInventory());
            if (slot >= 0) {
                ItemStack stack = villager.getInventory().getItem(slot);
                if (VillagerConsumptionManager.startConsuming(villager, stack, sessionSource)) {
                    stack.shrink(1);
                }
                return;
            }
        }
        if (drink) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            int slot = bestDrinkSlot(villager.getInventory(), bridge);
            if (slot >= 0) {
                ItemStack stack = villager.getInventory().getItem(slot);
                if (VillagerConsumptionManager.startConsuming(villager, stack, sessionSource)) {
                    ItemStack remainder = bridge != null ? bridge.onDrinkConsumed(stack) : ItemStack.EMPTY;
                    if (remainder.isEmpty()) stack.shrink(1);
                }
                return;
            }
        }

        // Out of rations for an unsatisfied need: make a supply trip, else give up.
        if (beginAcquire(level, villager, needs)) {
            phase = Phase.ACQUIRE;
        } else {
            doStop(level, villager, gameTime);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (villager.getLastHurtByMob() != null) return false;
        if (currentScheduleActivity(villager) == Activity.REST) return false;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        if (!wantsFood(needs) && !wantsDrink(needs)) return false; // satiated
        if (phase == Phase.ACQUIRE && targetType == TargetType.NONE) return false;
        return true;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetType = TargetType.NONE;
        targetPos = null;
        targetItem = null;
        targetContainerSlot = null;
        sessionSource = null;
        ConsumableTargetClaims.releaseAll(villager.getUUID());
        cooldown = REFRACTORY_TICKS;
        VillagerSearchCadence.schedule(level, villager, SEARCH_CADENCE_KEY, cooldown, 40);
    }

    // --- Inventory rations ---

    private boolean hasRationFor(VillagerEntityMCA villager, TownsteadVillager.Needs needs) {
        SimpleContainer inv = villager.getInventory();
        if (wantsFood(needs) && bestFoodSlot(inv) >= 0) return true;
        return wantsDrink(needs) && bestDrinkSlot(inv, ThirstBridgeResolver.get()) >= 0;
    }

    private static int bestFoodSlot(SimpleContainer inv) {
        int best = -1, bestNutrition = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!FoodSafety.isSafeNutritiousFood(stack)) continue;
            int n = getNutrition(stack);
            if (n > bestNutrition) { bestNutrition = n; best = i; }
        }
        return best;
    }

    private static int bestDrinkSlot(SimpleContainer inv, ThirstCompatBridge bridge) {
        if (bridge == null) return -1;
        int best = -1, bestScore = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            int score = thirstScore(inv.getItem(i), bridge);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    // --- Acquisition (the supply trip) ---

    /** Picks a source for whichever need is unsatisfied and lacks a ration. Food is prioritized. */
    private boolean beginAcquire(ServerLevel level, VillagerEntityMCA villager, TownsteadVillager.Needs needs) {
        if (wantsFood(needs) && bestFoodSlot(villager.getInventory()) < 0) {
            if (acquireFood(level, villager)) { acquiring = Need.FOOD; setAcquireWalkTarget(villager); return true; }
        }
        if (wantsDrink(needs) && bestDrinkSlot(villager.getInventory(), ThirstBridgeResolver.get()) < 0) {
            if (acquireDrink(level, villager)) { acquiring = Need.DRINK; setAcquireWalkTarget(villager); return true; }
        }
        return false;
    }

    private void setAcquireWalkTarget(VillagerEntityMCA villager) {
        if (targetType == TargetType.GROUND_ITEM && targetItem != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetItem, WALK_SPEED, CLOSE_ENOUGH);
        } else if (targetPos != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, WALK_SPEED, CLOSE_ENOUGH);
        }
    }

    private boolean acquireFood(ServerLevel level, VillagerEntityMCA villager) {
        long claimUntil = level.getGameTime() + MAX_DURATION + 20L;
        List<ScoredCandidate> candidates = new ArrayList<>();
        if (TownsteadConfig.ENABLE_GROUND_ITEM_SOURCING.get()) {
            AABB box = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> !e.isRemoved() && FoodSafety.isSafeNutritiousFood(e.getItem()))) {
                if (ConsumableTargetClaims.isClaimedByOtherItem(level, villager.getUUID(), CLAIM_CATEGORY, item)) continue;
                candidates.add(new ScoredCandidate(TargetType.GROUND_ITEM, getNutrition(item.getItem()), item, null));
            }
        }
        if (TownsteadConfig.ENABLE_CONTAINER_SOURCING.get()) {
            NearbyItemSources.collectBestFoodSlots(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS, villager.blockPosition(), slot -> {
                if (ConsumableTargetClaims.isClaimedByOtherSlot(level, villager.getUUID(), CLAIM_CATEGORY, slot)) return;
                candidates.add(new ScoredCandidate(TargetType.CONTAINER, slot.score(), null, slot));
            });
        }
        if (selectAndClaim(level, villager, candidates, claimUntil)) return true;

        // Last resort: harvest a mature crop off the stalk.
        if (TownsteadConfig.ENABLE_CROP_SOURCING.get()) {
            BlockPos cropPos = NearbyCropIndex.snapshot(level, villager.blockPosition(), SEARCH_RADIUS, VERTICAL_RADIUS).nearestTo(villager);
            if (cropPos != null) {
                BlockPos chosen = ReachableTargetSelector.chooseReachable(level, villager,
                        List.of(new ReachableTargetSelector.Candidate<>(cropPos, cropPos)), CLOSE_ENOUGH, 1,
                        UNREACHABLE_TARGET_TTL_TICKS,
                        c -> villager.distanceToSqr(c.pos().getX() + 0.5, c.pos().getY() + 0.5, c.pos().getZ() + 0.5));
                if (chosen != null && ConsumableTargetClaims.tryClaimPos(level, villager.getUUID(), CLAIM_CATEGORY, chosen, claimUntil)) {
                    targetType = TargetType.CROP;
                    targetPos = chosen;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean acquireDrink(ServerLevel level, VillagerEntityMCA villager) {
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null) return false;
        long claimUntil = level.getGameTime() + MAX_DURATION + 20L;
        List<ScoredCandidate> candidates = new ArrayList<>();
        if (TownsteadConfig.isGroundItemThirstSourcingEnabled()) {
            AABB box = villager.getBoundingBox().inflate(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> !e.isRemoved())) {
                int score = thirstScore(item.getItem(), bridge);
                if (score <= 0) continue;
                if (ConsumableTargetClaims.isClaimedByOtherItem(level, villager.getUUID(), CLAIM_CATEGORY, item)) continue;
                candidates.add(new ScoredCandidate(TargetType.GROUND_ITEM, score, item, null));
            }
        }
        if (TownsteadConfig.isContainerThirstSourcingEnabled()) {
            NearbyItemSources.collectMatchingSlots(level, villager, SEARCH_RADIUS, VERTICAL_RADIUS,
                    stack -> thirstScore(stack, bridge) > 0,
                    stack -> thirstScore(stack, bridge),
                    villager.blockPosition(),
                    slot -> {
                        if (ConsumableTargetClaims.isClaimedByOtherSlot(level, villager.getUUID(), CLAIM_CATEGORY, slot)) return;
                        candidates.add(new ScoredCandidate(TargetType.CONTAINER, slot.score(), null, slot));
                    });
        }
        return selectAndClaim(level, villager, candidates, claimUntil);
    }

    private record ScoredCandidate(TargetType type, int score, ItemEntity item, NearbyItemSources.ContainerSlot slot) {}

    /** Sorts by score then distance, picks the best reachable, claims it, and stores it as the target. */
    private boolean selectAndClaim(ServerLevel level, VillagerEntityMCA villager, List<ScoredCandidate> candidates, long claimUntil) {
        if (candidates.isEmpty()) return false;
        candidates.sort(Comparator.comparingInt((ScoredCandidate c) -> -c.score())
                .thenComparingDouble(c -> distSqOf(villager, c)));
        List<ReachableTargetSelector.Candidate<ScoredCandidate>> reachable = new ArrayList<>();
        for (ScoredCandidate c : candidates) {
            BlockPos path = c.type() == TargetType.GROUND_ITEM ? c.item().blockPosition() : c.slot().pos();
            reachable.add(new ReachableTargetSelector.Candidate<>(c, path));
        }
        ScoredCandidate chosen = ReachableTargetSelector.chooseReachable(level, villager, reachable, CLOSE_ENOUGH,
                MAX_PATH_ATTEMPTS_PER_SEARCH, UNREACHABLE_TARGET_TTL_TICKS, c -> distSqOf(villager, c.value()));
        if (chosen == null) return false;
        boolean claimed = chosen.type() == TargetType.GROUND_ITEM
                ? ConsumableTargetClaims.tryClaimItem(level, villager.getUUID(), CLAIM_CATEGORY, chosen.item(), claimUntil)
                : ConsumableTargetClaims.tryClaimSlot(level, villager.getUUID(), CLAIM_CATEGORY, chosen.slot(), claimUntil);
        if (!claimed) return false;
        if (chosen.type() == TargetType.GROUND_ITEM) {
            targetType = TargetType.GROUND_ITEM;
            targetItem = chosen.item();
        } else {
            targetType = TargetType.CONTAINER;
            targetContainerSlot = chosen.slot();
            targetPos = chosen.slot().pos();
        }
        return true;
    }

    private static double distSqOf(VillagerEntityMCA villager, ScoredCandidate c) {
        if (c.type() == TargetType.GROUND_ITEM) return villager.distanceToSqr(c.item());
        BlockPos p = c.slot().pos();
        return villager.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    // --- Grabbing rations into inventory ---

    private void grabFromGround(VillagerEntityMCA villager) {
        if (targetItem == null || targetItem.isRemoved()) return;
        ItemStack stack = targetItem.getItem();
        int take = Math.min(stack.getCount(), RATION_GRAB);
        //? if >=1.21 {
        ItemStack grabbed = stack.copyWithCount(take);
        //?} else {
        /*ItemStack grabbed = stack.copy(); grabbed.setCount(take);
        *///?}
        ItemStack leftover = villager.getInventory().addItem(grabbed);
        stack.shrink(take - leftover.getCount());
        if (stack.isEmpty()) targetItem.discard();
        sessionSource = null; // ground item, nowhere to return empties
    }

    private void grabFromContainer(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level) || targetContainerSlot == null) return;
        sessionSource = targetContainerSlot.pos();
        // We're at storage: return any empties we've been carrying since the last meal before restocking.
        com.aetherianartificer.townstead.storage.EmptyContainerDropoff.depositCarried(level, villager);
        for (int i = 0; i < RATION_GRAB; i++) {
            ItemStack extracted = NearbyItemSources.extractOne(level, targetContainerSlot);
            if (extracted.isEmpty()) break;
            ItemStack leftover = villager.getInventory().addItem(extracted);
            if (!leftover.isEmpty()) { // inventory full; put the unplaceable one back-ish and stop
                villager.getInventory().addItem(leftover);
                break;
            }
        }
    }

    private void harvestCrop(ServerLevel level, VillagerEntityMCA villager) {
        if (targetPos == null) return;
        BlockState state = level.getBlockState(targetPos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        List<ItemStack> drops = CropBlock.getDrops(state, level, targetPos, null);
        level.destroyBlock(targetPos, false, villager);
        NearbyCropIndex.invalidate(level, targetPos);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) villager.getInventory().addItem(drop);
        }
        sessionSource = null;
    }

    // --- Scoring ---

    private static int getNutrition(ItemStack stack) {
        //? if >=1.21 {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null ? food.nutrition() : 0;
        //?} else {
        /*FoodProperties food = stack.getFoodProperties(null);
        return food != null ? food.getNutrition() : 0;
        *///?}
    }

    private static int thirstScore(ItemStack stack, ThirstCompatBridge bridge) {
        if (stack.isEmpty() || bridge == null || !bridge.itemRestoresThirst(stack)) return 0;
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
