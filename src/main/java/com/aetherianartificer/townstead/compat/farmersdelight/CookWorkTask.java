package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.WorkBuildingNav;
import com.aetherianartificer.townstead.ai.work.WorkNavigationMetrics;
import com.aetherianartificer.townstead.ai.work.WorkPathing;
import com.aetherianartificer.townstead.ai.work.WorkSiteRef;
import com.aetherianartificer.townstead.ai.work.WorkTarget;
import com.aetherianartificer.townstead.ai.work.WorkTaskAdapter;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.IngredientResolver;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.RecipeSelector;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler.StationSlot;
import com.aetherianartificer.townstead.hunger.CookProgressData;
import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.storage.VillageAiBudget;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CookWorkTask extends Behavior<VillagerEntityMCA> implements WorkTaskAdapter {

    // ── Constants ──

    private static final int SEARCH_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 3;
    private static final int CLOSE_ENOUGH = 0;
    private static final int MAX_DURATION = 1200;
    private static final double ARRIVAL_DISTANCE_SQ = 0.36d;
    private static final double NEAR_STATION_DISTANCE_SQ = 9.0d;
    private static final long STAND_REACQUIRE_INTERVAL_TICKS = 60L;
    private static final int REQUEST_RANGE = 24;
    private static final int IDLE_BACKOFF = 80;
    private static final int OCCUPIED_BACKOFF = 200;
    private static final float WALK_SPEED = 0.52f;
    private static final long RECIPE_REPEAT_COOLDOWN_TICKS = 200L;
    private static final int STATE_TIMEOUT_TICKS = 100;
    private static final int OPPORTUNISTIC_SWEEP_INTERVAL = 10;
    private static final int MAX_RECIPE_ATTEMPTS = 3;
    private static final long ENTRY_TARGET_RETRY_COOLDOWN_TICKS = 60L;
    private static final long ENTRY_SEARCH_RETRY_TICKS = 40L;
    private static final long ROOM_BOUNDS_CACHE_TICKS = 80L;
    private static final int ROOM_FLOOD_MAX_NODES = 16384;

    // ── State machine ──

    private enum CookState { ACQUIRE_STATION, SELECT_RECIPE, GATHER, COOK, COLLECT, COLLECT_WAIT }
    private enum BlockedReason { NONE, NO_KITCHEN, NO_INGREDIENTS, NO_RECIPE, NO_STORAGE, UNREACHABLE }

    private static final int COLLECT_WAIT_MAX_TICKS = 40;

    private CookState state = CookState.ACQUIRE_STATION;
    private long stateEnteredTick;
    private BlockPos stationAnchor;
    private BlockPos standPos;
    private StationType stationType;
    private DiscoveredRecipe activeRecipe;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private long cookDoneTick;
    private long nextStandReacquireTick;
    private long nextDebugTick;
    private long nextRequestTick;
    private long nextKnifeAcquireTick;
    private BlockedReason blocked = BlockedReason.NONE;
    private ItemStack heldCuttingInput = ItemStack.EMPTY;
    private final Map<ResourceLocation, Integer> stagedInputs = new HashMap<>();
    private final Set<Long> usedStations = new HashSet<>();
    private final Map<ResourceLocation, Long> recipeCooldownUntil = new HashMap<>();
    private final Map<Long, Long> failedKitchenEntryUntil = new HashMap<>();
    private int recipeAttempts;
    private long idleUntilTick;
    private BlockPos currentKitchenEntryTarget;
    private long nextKitchenEntrySearchTick = 0L;
    private double lastKitchenEntryDistanceSq = Double.MAX_VALUE;
    private long lastKitchenEntryProgressTick = 0L;

    // Kitchen bounds cache
    private Set<Long> cachedKitchenWorkArea = Set.of();
    private BlockPos cachedKitchenWorkAnchor = null;
    private long cachedKitchenWorkUntil = 0L;
    private WorkBuildingNav.Snapshot cachedKitchenSnapshot = WorkBuildingNav.Snapshot.EMPTY;

    public CookWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    // ── Behavior lifecycle ──

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.isTownsteadCookEnabled()) return false;
        if (townstead$isFatigueGated(villager)) return false;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!FarmersDelightCookAssignment.isExternalCookProfession(profession)) return false;
        if (!FarmersDelightCookAssignment.canVillagerWorkAsCook(level, villager)) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        return currentActivity(villager) == Activity.WORK;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!FarmersDelightCookAssignment.isExternalCookProfession(villager.getVillagerData().getProfession())) return;
        if (!FarmersDelightCookAssignment.canVillagerWorkAsCook(level, villager)) return;
        blocked = BlockedReason.NONE;
        state = CookState.ACQUIRE_STATION;
        stateEnteredTick = gameTime;
        recipeAttempts = 0;
        usedStations.clear();
        failedKitchenEntryUntil.clear();
        currentKitchenEntryTarget = null;
        nextKitchenEntrySearchTick = 0L;
        lastKitchenEntryDistanceSq = Double.MAX_VALUE;
        lastKitchenEntryProgressTick = 0L;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.isTownsteadCookEnabled()) return false;
        if (!FarmersDelightCookAssignment.isExternalCookProfession(villager.getVillagerData().getProfession())) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        return currentActivity(villager) == Activity.WORK;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationClaim(villager, stationAnchor);
        stationAnchor = null;
        standPos = null;
        stationType = null;
        activeRecipe = null;
        pendingOutput = ItemStack.EMPTY;
        heldCuttingInput = ItemStack.EMPTY;
        stagedInputs.clear();
        usedStations.clear();
        recipeCooldownUntil.clear();
        recipeAttempts = 0;
        idleUntilTick = 0L;
        failedKitchenEntryUntil.clear();
        currentKitchenEntryTarget = null;
        nextKitchenEntrySearchTick = 0L;
        lastKitchenEntryDistanceSq = Double.MAX_VALUE;
        lastKitchenEntryProgressTick = 0L;
        cachedKitchenWorkArea = Set.of();
        cachedKitchenWorkAnchor = null;
        cachedKitchenWorkUntil = 0L;
        cachedKitchenSnapshot = WorkBuildingNav.Snapshot.EMPTY;
        state = CookState.ACQUIRE_STATION;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!FarmersDelightCookAssignment.canVillagerWorkAsCook(level, villager)) {
            clearAll(villager, gameTime);
            return;
        }

        if (gameTime < idleUntilTick) return;

        debugTick(level, villager, gameTime);

        // Opportunistic knife grab
        if (!StationHandler.hasKnife(villager.getInventory()) && gameTime >= nextKnifeAcquireTick) {
            Set<Long> bounds = activeKitchenBounds(villager, activeKitchenReference(villager));
            BlockPos center = stationAnchor != null ? stationAnchor : nearestKitchenAnchor(villager);
            IngredientResolver.findKitchenStorageSlot(level, villager, StationHandler::isKnifeStack, bounds);
            nextKnifeAcquireTick = gameTime + 200L;
        }

        // State timeout
        if (gameTime - stateEnteredTick > STATE_TIMEOUT_TICKS
                && state != CookState.COOK
                && state != CookState.COLLECT_WAIT
                && !kitchenEntryProgressActive(villager, gameTime)) {
            debugChat(level, villager, "STATE:timeout in " + state.name() + ", resetting");
            transition(CookState.ACQUIRE_STATION, gameTime);
            releaseStationClaim(villager, stationAnchor);
            stationAnchor = null;
            standPos = null;
            activeRecipe = null;
            recipeAttempts = 0;
        }

        // Opportunistic sweep: pick up any recipe outputs near the villager
        if (gameTime % OPPORTUNISTIC_SWEEP_INTERVAL == 0) {
            sweepNearbyOutputs(level, villager);
        }

        switch (state) {
            case ACQUIRE_STATION -> tickAcquireStation(level, villager, gameTime);
            case SELECT_RECIPE -> tickSelectRecipe(level, villager, gameTime);
            case GATHER -> tickGather(level, villager, gameTime);
            case COOK -> tickCook(level, villager, gameTime);
            case COLLECT -> tickCollect(level, villager, gameTime);
            case COLLECT_WAIT -> tickCollectWait(level, villager, gameTime);
        }
    }

    // ── State: ACQUIRE_STATION ──

    private void tickAcquireStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationClaim(villager, stationAnchor);
        activeRecipe = null;
        heldCuttingInput = ItemStack.EMPTY;
        stagedInputs.clear();
        pruneFailedKitchenEntryTargets(gameTime);

        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        if (kitchenBounds.isEmpty()) {
            setBlocked(level, villager, gameTime, BlockedReason.NO_KITCHEN, "");
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        WorkBuildingNav.Snapshot kitchenSnapshot = activeKitchenSnapshot(level, villager);
        if (!isVillagerInActiveKitchen(villager)) {
            BlockPos entryTarget = currentOrNewKitchenEntryTarget(level, villager, gameTime, kitchenSnapshot);
            if (entryTarget == null) {
                currentKitchenEntryTarget = null;
                lastKitchenEntryDistanceSq = Double.MAX_VALUE;
                debugChat(level, villager, "ENTER:no kitchen entry from "
                        + villager.blockPosition().getX() + "," + villager.blockPosition().getY() + "," + villager.blockPosition().getZ());
                setBlocked(level, villager, gameTime, BlockedReason.UNREACHABLE, "");
                idleUntilTick = gameTime + IDLE_BACKOFF;
                return;
            }
            boolean targetChanged = currentKitchenEntryTarget == null || !currentKitchenEntryTarget.equals(entryTarget);
            currentKitchenEntryTarget = entryTarget;
            nextKitchenEntrySearchTick = gameTime + ENTRY_SEARCH_RETRY_TICKS;
            blocked = BlockedReason.NONE;
            trackKitchenEntryProgress(villager, gameTime, entryTarget);
            if (targetChanged) {
                debugChat(level, villager, "ENTER:from "
                        + villager.blockPosition().getX() + "," + villager.blockPosition().getY() + "," + villager.blockPosition().getZ()
                        + " to "
                        + entryTarget.getX() + "," + entryTarget.getY() + "," + entryTarget.getZ());
            }
            BehaviorUtils.setWalkAndLookTargetMemories(villager, entryTarget, WALK_SPEED, CLOSE_ENOUGH);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(entryTarget, WALK_SPEED, CLOSE_ENOUGH));
            return;
        }
        currentKitchenEntryTarget = null;
        nextKitchenEntrySearchTick = 0L;
        lastKitchenEntryDistanceSq = Double.MAX_VALUE;
        blocked = BlockedReason.NONE;

        List<StationSlot> stations = kitchenSnapshot.stations();
        if (stations.isEmpty()) {
            debugChat(level, villager, "ACQUIRE:no stations found in kitchen (" + kitchenBounds.size() + " bounds)");
            setBlocked(level, villager, gameTime, BlockedReason.NO_KITCHEN, "");
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }
        // Log discovered station types on first acquire
        if (usedStations.isEmpty()) {
            Map<String, Integer> typeCounts = new LinkedHashMap<>();
            for (StationSlot slot : stations) {
                typeCounts.merge(slot.type().name(), 1, Integer::sum);
            }
            debugChat(level, villager, "ACQUIRE:found " + stations.size() + " stations " + typeCounts);
        }

        // Partition stations into: fresh (not tried, not claimed), tried, occupied
        List<StationSlot> fresh = new ArrayList<>();
        boolean anyOccupied = false;
        for (StationSlot slot : stations) {
            if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), slot.pos())) {
                anyOccupied = true;
                continue;
            }
            if (!usedStations.contains(slot.pos().asLong())) {
                fresh.add(slot);
            }
        }

        // If no fresh stations, reset used list and try again
        if (fresh.isEmpty()) {
            if (anyOccupied && usedStations.isEmpty()) {
                // All stations are occupied by other cooks — wait longer
                debugChat(level, villager, "ACQUIRE:all stations occupied, waiting");
                idleUntilTick = gameTime + OCCUPIED_BACKOFF;
                setBlocked(level, villager, gameTime, BlockedReason.UNREACHABLE, "");
                return;
            }
            // All tried but some might be free now — reset and backoff briefly
            debugChat(level, villager, "ACQUIRE:all stations tried, resetting");
            usedStations.clear();
            recipeAttempts = 0;
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        // Random pick from fresh stations
        StationSlot best = fresh.get(ThreadLocalRandom.current().nextInt(fresh.size()));

        BlockPos stand = WorkBuildingNav.nearestStationStand(kitchenSnapshot, villager, best.pos());
        if (stand == null) stand = StationHandler.findStandingPosition(level, villager, best.pos());
        if (stand == null) {
            // Can't reach this one, mark as used and try again next tick
            usedStations.add(best.pos().asLong());
            return;
        }

        stationAnchor = best.pos();
        stationType = best.type();
        standPos = stand;

        long claimUntil = gameTime + MAX_DURATION + 20L;
        CookStationClaims.tryClaim(level, villager.getUUID(), stationAnchor, claimUntil);

        BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
        debugChat(level, villager, "ACQUIRE:" + stationType.name()
                + " at " + stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ());
        transition(CookState.SELECT_RECIPE, gameTime);
    }

    // ── State: SELECT_RECIPE ──

    private void tickSelectRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;

        // Verify station still valid
        if (stationAnchor == null || !StationHandler.isStation(level, stationAnchor)) {
            transition(CookState.ACQUIRE_STATION, gameTime);
            return;
        }

        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        DiscoveredRecipe recipe = RecipeSelector.pickRecipe(
                level, villager, stationType, stationAnchor, kitchenBounds, recipeCooldownUntil,
                true, false);

        if (recipe == null) {
            int tier = Math.max(1, FarmersDelightCookAssignment.effectiveRecipeTier(level, villager));
            int available = ModRecipeRegistry.getRecipesForStation(level, stationType, tier).size();
            debugChat(level, villager, "SELECT:no recipe for " + stationType.name()
                    + " (tier=" + tier + ", candidates=" + available + "), rotating");
            usedStations.add(stationAnchor.asLong());
            setBlocked(level, villager, gameTime, BlockedReason.NO_RECIPE, townstead$stationDisplayName(stationType));
            transition(CookState.ACQUIRE_STATION, gameTime);
            return;
        }

        activeRecipe = recipe;
        recipeAttempts = 0;
        debugChat(level, villager, "SELECT:" + recipe.output() + " tier=" + recipe.tier());
        transition(CookState.GATHER, gameTime);
    }

    // ── State: GATHER ──

    private void tickGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;
        if (activeRecipe == null) {
            transition(CookState.SELECT_RECIPE, gameTime);
            return;
        }

        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        IngredientResolver.PullResult pullResult = IngredientResolver.pullAndConsumeDetailed(
                level, villager, activeRecipe, stationAnchor, stationType, stagedInputs, kitchenBounds);
        boolean success = pullResult.success();

        if (!success) {
            debugChat(level, villager, "GATHER:failed for " + activeRecipe.output()
                    + (pullResult.detail().isBlank() ? "" : " (" + pullResult.detail() + ")"));
            IngredientResolver.rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
            String recipeName = townstead$itemDisplayName(level, activeRecipe.output());
            String missing = IngredientResolver.describeMissingRequirements(level, villager, activeRecipe, stationAnchor, kitchenBounds);
            if (missing == null || missing.isBlank()) missing = pullResult.detail();
            setBlocked(level, villager, gameTime, BlockedReason.NO_INGREDIENTS,
                    missing == null || missing.isBlank() ? recipeName : missing);
            // Blacklist this recipe so it's not immediately re-selected
            recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
            activeRecipe = null;
            recipeAttempts++;
            if (recipeAttempts >= MAX_RECIPE_ATTEMPTS) {
                debugChat(level, villager, "GATHER:max attempts, rotating station");
                usedStations.add(stationAnchor.asLong());
                idleUntilTick = gameTime + IDLE_BACKOFF;
                transition(CookState.ACQUIRE_STATION, gameTime);
            } else {
                // Try a different recipe on the same station
                transition(CookState.SELECT_RECIPE, gameTime);
            }
            return;
        }

        // For cutting board, hold aside the input for the COOK phase
        if (stationType == StationType.CUTTING_BOARD && !activeRecipe.inputs().isEmpty()) {
            SimpleContainer inv = villager.getInventory();
            for (ResourceLocation id : activeRecipe.inputs().get(0).itemIds()) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR) continue;
                if (StationHandler.count(inv, item) > 0) {
                    heldCuttingInput = new ItemStack(item, 1);
                    StationHandler.consume(inv, item, 1);
                    break;
                }
            }
        }

        debugChat(level, villager, "GATHER:success for " + activeRecipe.output());
        cookDoneTick = gameTime + activeRecipe.cookTimeTicks();
        playSound(level);
        transition(CookState.COOK, gameTime);
    }

    // ── State: COOK ──

    private void tickCook(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor != null && standPos != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        }
        if (gameTime % 30 == 0) {
            villager.swing(villager.getDominantHand());
        }

        // Collect surface drops while waiting
        if (stationType == StationType.FIRE_STATION && stationAnchor != null) {
            Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
            List<ItemStack> drops = StationHandler.collectSurfaceCookDrops(level, stationAnchor, outputIds);
            Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
            for (ItemStack drop : drops) {
                int stored = IngredientResolver.storeOutputInCookStorage(level, villager, drop, stationAnchor, kitchenBounds);
                if (!drop.isEmpty()) {
                    ItemStack remainder = villager.getInventory().addItem(drop);
                    if (!remainder.isEmpty()) {
                        ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                        entity.setPickUpDelay(0);
                        level.addFreshEntity(entity);
                    }
                }
            }
        }

        if (gameTime < cookDoneTick) return;

        // Cutting board: process now
        if (stationType == StationType.CUTTING_BOARD && activeRecipe != null) {
            SimpleContainer inv = villager.getInventory();
            ItemStack knifeStack = ItemStack.EMPTY;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (StationHandler.isKnifeStack(inv.getItem(i))) {
                    knifeStack = inv.getItem(i);
                    break;
                }
            }
            if (!heldCuttingInput.isEmpty()) {
                boolean processed = StationHandler.cuttingBoardProcess(
                        level, villager, stationAnchor, heldCuttingInput, knifeStack);
                heldCuttingInput = ItemStack.EMPTY;
                if (!processed) {
                    debugChat(level, villager, "COOK:cutting board failed");
                    // Produce virtual output as fallback
                    Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
                    if (outputItem != Items.AIR) {
                        pendingOutput = new ItemStack(outputItem, activeRecipe.outputCount());
                        villager.getInventory().addItem(pendingOutput.copy());
                    }
                }
            }
        }

        if (stationType == StationType.HOT_STATION && !hotStationOutputReady(level)) {
            return;
        }

        debugChat(level, villager, "COOK:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        transition(CookState.COLLECT, gameTime);
    }

    // ── State: COLLECT ──

    private void tickCollect(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            transition(CookState.SELECT_RECIPE, gameTime);
            return;
        }

        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        boolean collected = false;

        // Collect surface drops (campfire/fire station items that pop out)
        if (stationAnchor != null) {
            collected |= collectAndStoreSurfaceDrops(level, villager, kitchenBounds, outputIds);
        }

        // Extract output from hot station (cooking pot)
        if (stationType == StationType.HOT_STATION && stationAnchor != null) {
            Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
            if (outputItem != Items.AIR) {
                int extracted = StationHandler.extractFromStation(level, stationAnchor, outputItem, activeRecipe.outputCount());
                if (extracted > 0) {
                    ItemStack output = new ItemStack(outputItem, extracted);
                    IngredientResolver.storeOutputInCookStorage(level, villager, output, stationAnchor, kitchenBounds);
                    if (!output.isEmpty()) {
                        villager.getInventory().addItem(output);
                    }
                    collected = true;
                } else {
                    // Output not ready yet — wait for the station to finish
                    transition(CookState.COLLECT_WAIT, gameTime);
                    return;
                }
            }
        }

        // For fire station, if nothing was collected yet, wait for items to land
        if (stationType == StationType.FIRE_STATION && !collected) {
            transition(CookState.COLLECT_WAIT, gameTime);
            return;
        }

        finishCollect(level, villager, kitchenBounds, outputIds, gameTime);
    }

    // ── State: COLLECT_WAIT ──

    private void tickCollectWait(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            transition(CookState.SELECT_RECIPE, gameTime);
            return;
        }

        // Keep looking at the station while waiting
        if (stationAnchor != null && standPos != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        }

        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        boolean collected = false;

        // Try collecting surface drops
        if (stationAnchor != null) {
            collected |= collectAndStoreSurfaceDrops(level, villager, kitchenBounds, outputIds);
        }

        // Try extracting from hot station
        if (stationType == StationType.HOT_STATION && stationAnchor != null) {
            Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
            if (outputItem != Items.AIR) {
                int extracted = StationHandler.extractFromStation(level, stationAnchor, outputItem, activeRecipe.outputCount());
                if (extracted > 0) {
                    ItemStack output = new ItemStack(outputItem, extracted);
                    IngredientResolver.storeOutputInCookStorage(level, villager, output, stationAnchor, kitchenBounds);
                    if (!output.isEmpty()) {
                        villager.getInventory().addItem(output);
                    }
                    collected = true;
                }
            }
        }

        // If we collected something, or timed out, finish
        long elapsed = gameTime - stateEnteredTick;
        if (collected || elapsed >= COLLECT_WAIT_MAX_TICKS) {
            if (!collected && stationType == StationType.HOT_STATION && activeRecipe != null) {
                if (elapsed == COLLECT_WAIT_MAX_TICKS) {
                    debugChat(level, villager, "COLLECT_WAIT:still waiting for real pot output " + activeRecipe.output());
                }
                return;
            }
            finishCollect(level, villager, kitchenBounds, outputIds, gameTime);
        }
    }

    // ── Shared collect helpers ──

    private boolean collectAndStoreSurfaceDrops(ServerLevel level, VillagerEntityMCA villager,
                                                 Set<Long> kitchenBounds, Set<ResourceLocation> outputIds) {
        List<ItemStack> drops = StationHandler.collectSurfaceCookDrops(level, stationAnchor, outputIds);
        if (drops.isEmpty()) return false;
        for (ItemStack drop : drops) {
            IngredientResolver.storeOutputInCookStorage(level, villager, drop, stationAnchor, kitchenBounds);
            if (!drop.isEmpty()) {
                ItemStack remainder = villager.getInventory().addItem(drop);
                if (!remainder.isEmpty()) {
                    ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                    entity.setPickUpDelay(0);
                    level.addFreshEntity(entity);
                }
            }
        }
        return true;
    }

    private boolean hotStationOutputReady(ServerLevel level) {
        if (stationType != StationType.HOT_STATION || stationAnchor == null || activeRecipe == null) return false;
        Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
        if (outputItem == Items.AIR) return false;
        return StationHandler.countItemInStation(level, stationAnchor, outputItem) >= activeRecipe.outputCount();
    }

    private void finishCollect(ServerLevel level, VillagerEntityMCA villager,
                               Set<Long> kitchenBounds, Set<ResourceLocation> outputIds, long gameTime) {
        // Store any pending output from cutting board / fire
        if (!pendingOutput.isEmpty()) {
            IngredientResolver.storeOutputInCookStorage(level, villager, pendingOutput, stationAnchor, kitchenBounds);
            if (!pendingOutput.isEmpty()) {
                villager.getInventory().addItem(pendingOutput);
            }
            pendingOutput = ItemStack.EMPTY;
        }

        // Also store anything in inventory that's a recipe output
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null && outputIds.contains(itemId)) {
                IngredientResolver.storeOutputInCookStorage(level, villager, stack, stationAnchor, kitchenBounds);
            }
        }

        // Award XP
        awardCookXP(level, villager);

        // Mark recipe cooldown
        if (activeRecipe != null) {
            recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
        }

        debugChat(level, villager, "COLLECT:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        activeRecipe = null;
        stagedInputs.clear();

        // Track station usage
        if (stationAnchor != null) usedStations.add(stationAnchor.asLong());

        // Station rotation: 50% chance to rotate if other types available
        if (ThreadLocalRandom.current().nextDouble() < 0.5d) {
            transition(CookState.ACQUIRE_STATION, gameTime);
        } else {
            transition(CookState.SELECT_RECIPE, gameTime);
        }
    }

    // ── Opportunistic output sweep ──

    private void sweepNearbyOutputs(ServerLevel level, VillagerEntityMCA villager) {
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        if (outputIds.isEmpty()) return;
        AABB area = villager.getBoundingBox().inflate(3.0, 2.0, 3.0);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && outputIds.contains(id);
        });
        if (drops.isEmpty()) return;
        Set<Long> kitchenBounds = activeKitchenBounds(villager, activeKitchenReference(villager));
        BlockPos storageRef = stationAnchor != null ? stationAnchor : villager.blockPosition();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) continue;
            drop.discard();
            IngredientResolver.storeOutputInCookStorage(level, villager, stack, storageRef, kitchenBounds);
            if (!stack.isEmpty()) {
                ItemStack remainder = villager.getInventory().addItem(stack);
                if (!remainder.isEmpty()) {
                    ItemEntity entity = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder);
                    entity.setPickUpDelay(0);
                    level.addFreshEntity(entity);
                }
            }
        }
    }

    // ── Navigation helper ──

    private boolean ensureNearStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || standPos == null) {
            transition(CookState.ACQUIRE_STATION, gameTime);
            return false;
        }

        if (!isVillagerInActiveKitchen(villager)) {
            transition(CookState.ACQUIRE_STATION, gameTime);
            return false;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        double distSq = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5);
        double anchorDistSq = villager.distanceToSqr(
                stationAnchor.getX() + 0.5, stationAnchor.getY() + 0.5, stationAnchor.getZ() + 0.5);

        if (distSq > ARRIVAL_DISTANCE_SQ && anchorDistSq > NEAR_STATION_DISTANCE_SQ) {
            if (gameTime >= nextStandReacquireTick) {
                nextStandReacquireTick = gameTime + STAND_REACQUIRE_INTERVAL_TICKS;
                BlockPos refreshed = WorkBuildingNav.nearestStationStand(activeKitchenSnapshot(level, villager), villager, stationAnchor);
                if (refreshed == null) refreshed = StationHandler.findStandingPosition(level, villager, stationAnchor);
                if (refreshed != null) standPos = refreshed;
            }
            return false;
        }
        nextStandReacquireTick = 0L;
        return true;
    }

    // ── State transition ──

    private void transition(CookState newState, long gameTime) {
        state = newState;
        stateEnteredTick = gameTime;
    }

    // ── Kitchen bounds ──

    private Set<Long> activeKitchenBounds(VillagerEntityMCA villager, BlockPos anchor) {
        if (villager.level() instanceof ServerLevel level) {
            Set<Long> assigned = FarmersDelightCookAssignment.assignedKitchenBounds(level, villager);
            if (!assigned.isEmpty()) {
                return assigned;
            }
        }

        List<Building> kitchens = StationHandler.kitchenBuildings(villager);
        if (kitchens.isEmpty()) return Set.of();

        Building selected = null;
        if (anchor != null) {
            long anchorKey = anchor.asLong();
            for (Building building : kitchens) {
                for (BlockPos bp : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                    if (bp.asLong() == anchorKey) { selected = building; break; }
                }
                if (selected != null) break;
            }
        }
        if (selected == null) {
            BlockPos reference = anchor != null ? anchor : villager.blockPosition();
            double best = Double.MAX_VALUE;
            for (Building building : kitchens) {
                BlockPos center = building.getCenter();
                if (center == null) continue;
                double dist = reference.distSqr(center);
                if (dist < best) { best = dist; selected = building; }
            }
            if (selected == null) selected = kitchens.get(0);
        }

        Set<Long> bounds = new HashSet<>();
        for (BlockPos bp : (Iterable<BlockPos>) selected.getBlockPosStream()::iterator) {
            bounds.add(bp.asLong());
        }
        return bounds;
    }

    private void cacheKitchenWorkArea(BlockPos anchor, long gameTime, Set<Long> bounds) {
        cachedKitchenWorkAnchor = anchor == null ? null : anchor.immutable();
        cachedKitchenWorkArea = bounds == null ? Set.of() : bounds;
        cachedKitchenWorkUntil = gameTime + ROOM_BOUNDS_CACHE_TICKS;
    }

    private WorkBuildingNav.Snapshot activeKitchenSnapshot(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos anchor = activeKitchenReference(villager);
        long gameTime = level.getGameTime();
        if (anchor != null && cachedKitchenWorkAnchor != null
                && anchor.equals(cachedKitchenWorkAnchor)
                && gameTime <= cachedKitchenWorkUntil
                && !cachedKitchenSnapshot.walkableInterior().isEmpty()) {
            return cachedKitchenSnapshot;
        }
        Set<Long> bounds = activeKitchenBounds(villager, anchor);
        WorkBuildingNav.Snapshot snapshot = WorkBuildingNav.snapshot(level, bounds, anchor);
        cachedKitchenSnapshot = snapshot;
        cacheKitchenWorkArea(anchor, gameTime, snapshot.walkableInterior());
        return snapshot;
    }

    private Set<Long> roomExpandedKitchenArea(ServerLevel level, Set<Long> baseBounds, BlockPos reference) {
        if (baseBounds == null || baseBounds.isEmpty() || reference == null) {
            return baseBounds == null ? Set.of() : baseBounds;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long key : baseBounds) {
            BlockPos p = BlockPos.of(key);
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        BlockPos seed = findRoomSeed(level, reference, minX, minY, minZ, maxX, maxY, maxZ);
        if (seed == null) return baseBounds;

        Set<Long> room = new HashSet<>(baseBounds);
        Set<Long> visitedAir = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visitedAir.add(seed.asLong());

        while (!queue.isEmpty() && visitedAir.size() < ROOM_FLOOD_MAX_NODES) {
            BlockPos cur = queue.removeFirst();
            room.add(cur.asLong());
            for (Direction dir : Direction.values()) {
                BlockPos nxt = cur.relative(dir);
                if (nxt.getX() < minX || nxt.getX() > maxX
                        || nxt.getY() < minY || nxt.getY() > maxY
                        || nxt.getZ() < minZ || nxt.getZ() > maxZ) continue;
                room.add(nxt.asLong());
                if (!isRoomPassable(level, nxt)) continue;
                if (visitedAir.add(nxt.asLong())) queue.addLast(nxt);
            }
        }
        return room;
    }

    private @Nullable BlockPos findRoomSeed(ServerLevel level, BlockPos reference,
                                            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (reference == null) return null;
        if (isWithin(reference, minX, minY, minZ, maxX, maxY, maxZ) && isRoomPassable(level, reference)) {
            return reference.immutable();
        }
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos p = reference.offset(dx, dy, dz);
                        if (!isWithin(p, minX, minY, minZ, maxX, maxY, maxZ)) continue;
                        if (isRoomPassable(level, p)) return p.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static boolean isWithin(BlockPos p, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return p.getX() >= minX && p.getX() <= maxX
                && p.getY() >= minY && p.getY() <= maxY
                && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    private static boolean isRoomPassable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.is(net.minecraft.tags.BlockTags.DOORS) || state.is(net.minecraft.tags.BlockTags.FENCE_GATES)
                || state.is(net.minecraft.tags.BlockTags.TRAPDOORS)) return false;
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private BlockPos activeKitchenReference(VillagerEntityMCA villager) {
        if (villager.level() instanceof ServerLevel level) {
            Optional<Building> assigned = FarmersDelightCookAssignment.assignedKitchen(level, villager);
            if (assigned.isPresent()) {
                BlockPos center = assigned.get().getCenter();
                if (center != null) return center;
                for (BlockPos bp : (Iterable<BlockPos>) assigned.get().getBlockPosStream()::iterator) {
                    return bp.immutable();
                }
            }
        }
        if (stationAnchor != null) return stationAnchor;
        BlockPos nearest = nearestKitchenAnchor(villager);
        return nearest != null ? nearest : villager.blockPosition();
    }

    private boolean isVillagerInActiveKitchen(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        return WorkBuildingNav.isInsideOrOnStationStand(activeKitchenSnapshot(level, villager), villager.blockPosition());
    }

    private @Nullable BlockPos currentOrNewKitchenEntryTarget(
            ServerLevel level,
            VillagerEntityMCA villager,
            long gameTime,
            WorkBuildingNav.Snapshot kitchenSnapshot
    ) {
        if (currentKitchenEntryTarget != null) {
            boolean currentStillValid = WorkPathing.isSafeStandPosition(level, currentKitchenEntryTarget);
            if (!currentStillValid) {
                markFailedKitchenEntryTarget(currentKitchenEntryTarget, gameTime);
                currentKitchenEntryTarget = null;
            } else {
                Path currentPath = villager.getNavigation().createPath(currentKitchenEntryTarget, CLOSE_ENOUGH);
                if (currentPath != null && currentPath.canReach()) {
                    return currentKitchenEntryTarget;
                }
                markFailedKitchenEntryTarget(currentKitchenEntryTarget, gameTime);
                currentKitchenEntryTarget = null;
            }
        }
        if (gameTime < nextKitchenEntrySearchTick) return null;
        List<BlockPos> waypointCandidates = WorkBuildingNav.intermediateApproachTargets(level, villager, kitchenSnapshot).stream()
                .filter(pos -> failedKitchenEntryUntil.getOrDefault(pos.asLong(), 0L) <= gameTime)
                .toList();
        BlockPos waypoint = WorkBuildingNav.chooseReachableTarget(level, villager, waypointCandidates, CLOSE_ENOUGH, false);
        if (waypoint != null) return waypoint;
        List<BlockPos> directCandidates = kitchenSnapshot.approachTargets().stream()
                .filter(pos -> failedKitchenEntryUntil.getOrDefault(pos.asLong(), 0L) <= gameTime)
                .toList();
        return WorkBuildingNav.chooseReachableTarget(level, villager, directCandidates, CLOSE_ENOUGH, false);
    }

    private void markFailedKitchenEntryTarget(@Nullable BlockPos pos, long gameTime) {
        if (pos == null) return;
        failedKitchenEntryUntil.put(pos.asLong(), gameTime + ENTRY_TARGET_RETRY_COOLDOWN_TICKS);
    }

    private void pruneFailedKitchenEntryTargets(long gameTime) {
        failedKitchenEntryUntil.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
    }

    private void trackKitchenEntryProgress(VillagerEntityMCA villager, long gameTime, BlockPos entryTarget) {
        double distSq = villager.distanceToSqr(entryTarget.getX() + 0.5, entryTarget.getY() + 0.5, entryTarget.getZ() + 0.5);
        if (distSq + 0.25d < lastKitchenEntryDistanceSq) {
            lastKitchenEntryDistanceSq = distSq;
            lastKitchenEntryProgressTick = gameTime;
        } else if (lastKitchenEntryDistanceSq == Double.MAX_VALUE) {
            lastKitchenEntryDistanceSq = distSq;
            lastKitchenEntryProgressTick = gameTime;
        }
    }

    private boolean kitchenEntryProgressActive(VillagerEntityMCA villager, long gameTime) {
        if (state != CookState.ACQUIRE_STATION || currentKitchenEntryTarget == null) return false;
        BlockPosTracker tracker = new BlockPosTracker(currentKitchenEntryTarget);
        if (!tracker.currentBlockPosition().equals(currentKitchenEntryTarget)) return false;
        return gameTime - lastKitchenEntryProgressTick <= STATE_TIMEOUT_TICKS;
    }

    private @Nullable BlockPos nearestKitchenAnchor(VillagerEntityMCA villager) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos anchor : StationHandler.kitchenAnchors((ServerLevel) villager.level(), villager)) {
            double dist = villager.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
            if (dist < bestDist) { bestDist = dist; best = anchor; }
        }
        return best;
    }

    // ── Station claims ──

    private void releaseStationClaim(VillagerEntityMCA villager, @Nullable BlockPos pos) {
        if (villager == null || pos == null) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        CookStationClaims.release(level, villager.getUUID(), pos);
    }

    // ── XP ──

    private void awardCookXP(ServerLevel level, VillagerEntityMCA villager) {
        if (activeRecipe == null) return;
        int xp = Math.max(1, activeRecipe.tier());
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        CookProgressData.addXp(data, xp, level.getGameTime());
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    // ── Sound ──

    private void playSound(ServerLevel level) {
        if (stationAnchor == null || stationType == null) return;
        if (stationType == StationType.CUTTING_BOARD) {
            level.playSound(null, stationAnchor, SoundEvents.AXE_STRIP, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.1f);
        } else {
            level.playSound(null, stationAnchor, SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.0f);
        }
    }

    // ── Blocked state ──

    private void setBlocked(ServerLevel level, VillagerEntityMCA villager, long gameTime, BlockedReason reason, String itemName) {
        blocked = reason;
        if (reason == BlockedReason.NONE) return;
        if (!TownsteadConfig.ENABLE_COOK_REQUEST_CHAT.get()) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) return;
        switch (blocked) {
            case NO_KITCHEN -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_kitchen/" + (1 + level.random.nextInt(4)));
            case NO_INGREDIENTS -> {
                if (itemName != null && !itemName.isBlank()) {
                    villager.sendChatToAllAround("dialogue.chat.cook_request.no_ingredients_item", itemName);
                } else {
                    villager.sendChatToAllAround("dialogue.chat.cook_request.no_ingredients/" + (1 + level.random.nextInt(6)));
                }
            }
            case NO_STORAGE -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_storage/" + (1 + level.random.nextInt(4)));
            case UNREACHABLE -> villager.sendChatToAllAround("dialogue.chat.cook_request.unreachable/" + (1 + level.random.nextInt(6)));
            case NO_RECIPE -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_recipe_item", itemName.isBlank() ? "that station" : itemName);
            case NONE -> {}
        }
        nextRequestTick = gameTime + Math.max(200, TownsteadConfig.COOK_REQUEST_INTERVAL_TICKS.get());
    }

    // ── Reset ──

    private void clearAll(VillagerEntityMCA villager, long gameTime) {
        releaseStationClaim(villager, stationAnchor);
        stationAnchor = null;
        standPos = null;
        stationType = null;
        activeRecipe = null;
        pendingOutput = ItemStack.EMPTY;
        heldCuttingInput = ItemStack.EMPTY;
        stagedInputs.clear();
        usedStations.clear();
        recipeCooldownUntil.clear();
        failedKitchenEntryUntil.clear();
        recipeAttempts = 0;
        idleUntilTick = 0L;
        currentKitchenEntryTarget = null;
        nextKitchenEntrySearchTick = 0L;
        lastKitchenEntryDistanceSq = Double.MAX_VALUE;
        lastKitchenEntryProgressTick = 0L;
        state = CookState.ACQUIRE_STATION;
        stateEnteredTick = gameTime;
        cachedKitchenWorkArea = Set.of();
        cachedKitchenWorkAnchor = null;
        cachedKitchenWorkUntil = 0L;
    }

    // ── Activity ──

    private Activity currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    @Override
    public WorkSiteRef activeWorkSite(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos reference = activeKitchenReference(villager);
        Set<Long> bounds = activeKitchenBounds(villager, reference);
        return bounds.isEmpty() ? null : WorkSiteRef.building(reference, bounds);
    }

    @Override
    public WorkTarget activeWorkTarget(ServerLevel level, VillagerEntityMCA villager) {
        if (currentKitchenEntryTarget != null) {
            return isVillagerInActiveKitchen(villager)
                    ? WorkTarget.buildingEntry(currentKitchenEntryTarget, activeKitchenReference(villager), "enter")
                    : WorkTarget.buildingApproach(currentKitchenEntryTarget, activeKitchenReference(villager), "approach");
        }
        if (standPos == null || stationAnchor == null) return null;
        return WorkTarget.stationStand(standPos, stationAnchor, state.name().toLowerCase());
    }

    @Override
    public float navigationWalkSpeed(ServerLevel level, VillagerEntityMCA villager) {
        return WALK_SPEED;
    }

    @Override
    public int navigationCloseEnough(ServerLevel level, VillagerEntityMCA villager) {
        return CLOSE_ENOUGH;
    }

    @Override
    public double navigationArrivalDistanceSq(ServerLevel level, VillagerEntityMCA villager) {
        return ARRIVAL_DISTANCE_SQ;
    }

    @Override
    public String navigationState(ServerLevel level, VillagerEntityMCA villager) {
        return state.name();
    }

    @Override
    public String navigationBlockedState(ServerLevel level, VillagerEntityMCA villager) {
        return blocked.name();
    }

    // ── Debug ──

    private void debugChat(ServerLevel level, VillagerEntityMCA villager, String msg) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.literal("[Cook:" + villager.getName().getString() + "] " + msg));
    }

    private void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (gameTime < nextDebugTick) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        String cookName = villager.getName().getString();
        String cookId = villager.getUUID().toString();
        if (cookId.length() > 8) cookId = cookId.substring(0, 8);
        String recipe = activeRecipe == null ? "none" : activeRecipe.output().toString();
        String anchor = stationAnchor == null ? "none" : stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ();
        String station = stationType == null ? "none" : stationType.name().toLowerCase();
        String idleInfo = gameTime < idleUntilTick ? " idle=" + (idleUntilTick - gameTime) : "";
        StorageSearchContext.Snapshot storageSnapshot = StorageSearchContext.Profiler.snapshot();
        VillageAiBudget.Snapshot budgetSnapshot = VillageAiBudget.snapshot();
        WorkNavigationMetrics.Snapshot navSnapshot = WorkNavigationMetrics.snapshot();
        ConsumableTargetClaims.Snapshot claimSnapshot = ConsumableTargetClaims.snapshot();
        WorkBuildingNav.Snapshot kitchenSnapshot = activeKitchenSnapshot(level, villager);
        Optional<Building> assignedKitchen = FarmersDelightCookAssignment.assignedKitchen(level, villager);
        String assignedKitchenDesc = assignedKitchen.map(this::townstead$describeAssignedBuilding).orElse("none");
        String navMode = townstead$navigationMode(level, villager);
        player.sendSystemMessage(Component.literal("[CookDBG:" + cookName + "#" + cookId + "] state=" + state.name()
                + " station=" + station + " anchor=" + anchor + " recipe=" + recipe
                + " doneAt=" + cookDoneTick + " blocked=" + blocked.name()
                + " mode=" + navMode + " site=" + assignedKitchenDesc + " stations=" + kitchenSnapshot.stations().size()
                + " used=" + usedStations.size() + " storage=" + storageSnapshot.observedBlocks()
                + "/" + storageSnapshot.handlerLookups()
                + " budget=" + budgetSnapshot.granted() + "/" + budgetSnapshot.throttled()
                + " nav=" + navSnapshot.snapshotRebuilds() + "/" + navSnapshot.pathAttempts()
                + "/" + navSnapshot.pathSuccesses() + "/" + navSnapshot.pathFailures()
                + " claims=" + claimSnapshot.grants() + "/" + claimSnapshot.conflicts() + "/" + claimSnapshot.activeClaims()
                + idleInfo));
        nextDebugTick = gameTime + 100L;
    }

    private String townstead$navigationMode(ServerLevel level, VillagerEntityMCA villager) {
        if (state != CookState.ACQUIRE_STATION) return "station";
        if (!isVillagerInActiveKitchen(villager)) return "approach";
        return stationAnchor != null ? "station" : "search";
    }

    private String townstead$describeAssignedBuilding(Building building) {
        if (building == null) return "none";
        BlockPos center = building.getCenter();
        int blockCount = 0;
        for (BlockPos ignored : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
            blockCount++;
        }
        String centerDesc = center == null ? "none" : center.getX() + "," + center.getY() + "," + center.getZ();
        return building.getType() + "@" + centerDesc + "[" + blockCount + "]";
    }

    private static boolean townstead$isFatigueGated(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return false;
        //? if neoforge {
        net.minecraft.nbt.CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*net.minecraft.nbt.CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        return FatigueData.isGated(fatigue) || FatigueData.getFatigue(fatigue) >= FatigueData.DROWSY_THRESHOLD;
    }

    private static String townstead$stationDisplayName(ModRecipeRegistry.StationType stationType) {
        // Convert COOKING_POT → "Cooking Pot", STOVE → "Stove", etc.
        String raw = stationType.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : raw.toCharArray()) {
            sb.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = (c == ' ');
        }
        return sb.toString();
    }

    private static String townstead$itemDisplayName(ServerLevel level, net.minecraft.resources.ResourceLocation itemId) {
        //? if >=1.21 {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        //?} else {
        /*var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        *///?}
        if (item == null) return itemId.getPath();
        return item.getDefaultInstance().getHoverName().getString();
    }
}
