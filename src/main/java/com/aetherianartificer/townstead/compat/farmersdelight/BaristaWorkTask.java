package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.WorkBuildingNav;
import com.aetherianartificer.townstead.ai.work.WorkMovement;
import com.aetherianartificer.townstead.ai.work.WorkNavigationMetrics;
import com.aetherianartificer.townstead.ai.work.WorkNavigationResult;
import com.aetherianartificer.townstead.ai.work.WorkSiteRef;
import com.aetherianartificer.townstead.ai.work.WorkTarget;
import com.aetherianartificer.townstead.ai.work.WorkTaskAdapter;
import com.aetherianartificer.townstead.ai.work.WorkTargetFailures;
import com.aetherianartificer.townstead.ai.work.WorkTargetProgress;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.IngredientResolver;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.RecipeIngredient;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BaristaWorkTask extends Behavior<VillagerEntityMCA> implements WorkTaskAdapter {

    // ── Constants ──

    private static final int SEARCH_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 3;
    private static final int CLOSE_ENOUGH = 0;
    private static final int BUILDING_CLOSE_ENOUGH = 2;
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
    private static final long ROOM_BOUNDS_CACHE_TICKS = 80L;
    private static final long WORKSITE_TARGET_RETRY_COOLDOWN_TICKS = 60L;
    private static final int WORKSITE_MAX_RETRIES = 2;

    // ── State machine ──

    private enum BaristaState { PATH_TO_WORKSITE, PATH_TO_STATION, SELECT_RECIPE, GATHER, BREW, COLLECT, COLLECT_WAIT }
    private enum BlockedReason { NONE, NO_CAFE, NO_INGREDIENTS, NO_RECIPE, NO_STORAGE, UNREACHABLE }

    private static final int COLLECT_WAIT_MAX_TICKS = 40;

    private BaristaState state = BaristaState.PATH_TO_WORKSITE;
    private long stateEnteredTick;
    private BlockPos stationAnchor;
    private BlockPos standPos;
    private StationType stationType;
    private DiscoveredRecipe activeRecipe;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private long brewDoneTick;
    private long nextStandReacquireTick;
    private long nextDebugTick;
    private long nextRequestTick;
    private BlockedReason blocked = BlockedReason.NONE;
    private final Map<ResourceLocation, Integer> stagedInputs = new HashMap<>();
    private final Set<Long> usedStations = new HashSet<>();
    private final Map<ResourceLocation, Long> recipeCooldownUntil = new HashMap<>();
    private int recipeAttempts;
    private long idleUntilTick;
    private BlockPos currentWorksiteTarget;
    private String currentWorksiteTargetKind = "stand";
    private final WorkTargetProgress worksiteTargetProgress = new WorkTargetProgress();
    private final WorkTargetFailures worksiteTargetFailures = new WorkTargetFailures();

    // Cafe navigation area cache
    private Set<Long> cachedCafeNavigationArea = Set.of();
    private BlockPos cachedCafeNavigationAnchor = null;
    private long cachedCafeNavigationUntil = 0L;
    private WorkBuildingNav.Snapshot cachedCafeSnapshot = WorkBuildingNav.Snapshot.EMPTY;

    public BaristaWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    // ── Behavior lifecycle ──

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ModCompat.isLoaded("rusticdelight")) return false;
        if (!TownsteadConfig.isTownsteadCookEnabled()) return false;
        if (townstead$isFatigueGated(villager)) return false;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!FarmersDelightBaristaAssignment.isBaristaProfession(profession)) return false;
        if (!FarmersDelightBaristaAssignment.canVillagerWorkAsBarista(level, villager)) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        return currentActivity(villager) == Activity.WORK;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!FarmersDelightBaristaAssignment.isBaristaProfession(villager.getVillagerData().getProfession())) return;
        if (!FarmersDelightBaristaAssignment.canVillagerWorkAsBarista(level, villager)) return;
        blocked = BlockedReason.NONE;
        state = BaristaState.PATH_TO_WORKSITE;
        stateEnteredTick = gameTime;
        recipeAttempts = 0;
        usedStations.clear();
        resetWorksiteTargeting();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ModCompat.isLoaded("rusticdelight")) return false;
        if (!TownsteadConfig.isTownsteadCookEnabled()) return false;
        if (!FarmersDelightBaristaAssignment.isBaristaProfession(villager.getVillagerData().getProfession())) return false;
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
        stagedInputs.clear();
        usedStations.clear();
        recipeCooldownUntil.clear();
        recipeAttempts = 0;
        idleUntilTick = 0L;
        cachedCafeNavigationArea = Set.of();
        cachedCafeNavigationAnchor = null;
        cachedCafeNavigationUntil = 0L;
        cachedCafeSnapshot = WorkBuildingNav.Snapshot.EMPTY;
        resetWorksiteTargeting();
        state = BaristaState.PATH_TO_WORKSITE;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!FarmersDelightBaristaAssignment.canVillagerWorkAsBarista(level, villager)) {
            clearAll(villager, gameTime);
            return;
        }

        if (gameTime < idleUntilTick) return;

        debugTick(level, villager, gameTime);

        // State timeout
        if (gameTime - stateEnteredTick > STATE_TIMEOUT_TICKS
                && state != BaristaState.PATH_TO_WORKSITE
                && state != BaristaState.PATH_TO_STATION
                && state != BaristaState.BREW
                && state != BaristaState.COLLECT_WAIT) {
            debugChat(level, villager, "STATE:timeout in " + state.name() + ", resetting");
            transitionToNavigationState(villager, gameTime);
            releaseStationClaim(villager, stationAnchor);
            stationAnchor = null;
            activeRecipe = null;
            recipeAttempts = 0;
        }

        // Opportunistic sweep: pick up any recipe outputs near the villager
        if (gameTime % OPPORTUNISTIC_SWEEP_INTERVAL == 0) {
            sweepNearbyOutputs(level, villager);
        }

        switch (state) {
            case PATH_TO_WORKSITE -> tickPathToWorksite(level, villager, gameTime);
            case PATH_TO_STATION -> tickPathToStation(level, villager, gameTime);
            case SELECT_RECIPE -> tickSelectRecipe(level, villager, gameTime);
            case GATHER -> tickGather(level, villager, gameTime);
            case BREW -> tickBrew(level, villager, gameTime);
            case COLLECT -> tickCollect(level, villager, gameTime);
            case COLLECT_WAIT -> tickCollectWait(level, villager, gameTime);
        }
    }

    // ── State: PATH_TO_WORKSITE ──

    private void tickPathToWorksite(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationClaim(villager, stationAnchor);
        stationAnchor = null;
        standPos = null;
        stationType = null;
        activeRecipe = null;
        stagedInputs.clear();

        Set<Long> cafeStorageBounds = activeCafeStorageBounds(villager);
        if (cafeStorageBounds.isEmpty()) {
            setBlocked(level, villager, gameTime, BlockedReason.NO_CAFE, "");
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        WorkBuildingNav.Snapshot cafeSnapshot = activeCafeSnapshot(level, villager);
        List<StationSlot> stations = cafeSnapshot.stations();
        stations = stations.stream()
                .filter(s -> s.type() == StationType.HOT_STATION || s.type() == StationType.FIRE_STATION)
                .toList();
        if (stations.isEmpty()) {
            debugChat(level, villager, "ACQUIRE:no stations found in cafe (" + cafeStorageBounds.size() + " bounds)");
            setBlocked(level, villager, gameTime, BlockedReason.NO_CAFE, "");
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        if (isVillagerInActiveCafe(villager)) {
            blocked = BlockedReason.NONE;
            resetWorksiteTargeting();
            transition(BaristaState.PATH_TO_STATION, gameTime);
            return;
        }

        BlockPos target = currentOrNewCafeWorksiteTarget(villager, gameTime, cafeSnapshot);
        if (target == null) {
            debugChat(level, villager, "ENTER:no worksite target from "
                    + villager.blockPosition().getX() + "," + villager.blockPosition().getY() + "," + villager.blockPosition().getZ());
            setBlocked(level, villager, gameTime, BlockedReason.UNREACHABLE, "");
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        WorkNavigationResult move = WorkMovement.tickMoveToTarget(
                villager,
                target,
                WALK_SPEED,
                BUILDING_CLOSE_ENOUGH,
                ARRIVAL_DISTANCE_SQ,
                worksiteTargetProgress,
                worksiteTargetFailures,
                gameTime,
                STATE_TIMEOUT_TICKS,
                WORKSITE_MAX_RETRIES,
                (int) WORKSITE_TARGET_RETRY_COOLDOWN_TICKS
        );
        switch (move) {
            case ARRIVED -> {
                blocked = BlockedReason.NONE;
                currentWorksiteTarget = null;
                if (isVillagerInActiveCafe(villager)) {
                    transition(BaristaState.PATH_TO_STATION, gameTime);
                }
            }
            case MOVING -> blocked = BlockedReason.NONE;
            case BLOCKED -> {
                currentWorksiteTarget = null;
                debugChat(level, villager, "ENTER:blocked toward "
                        + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " kind=" + currentWorksiteTargetKind);
            }
            case NO_TARGET -> {
                currentWorksiteTarget = null;
                setBlocked(level, villager, gameTime, BlockedReason.UNREACHABLE, "");
            }
        }
    }

    // ── State: PATH_TO_STATION ──

    private void tickPathToStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isVillagerInActiveCafe(villager)) {
            transition(BaristaState.PATH_TO_WORKSITE, gameTime);
            return;
        }

        WorkBuildingNav.Snapshot cafeSnapshot = activeCafeSnapshot(level, villager);
        List<StationSlot> stations = cafeSnapshot.stations();
        stations = stations.stream()
                .filter(s -> s.type() == StationType.HOT_STATION || s.type() == StationType.FIRE_STATION)
                .toList();

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

        if (fresh.isEmpty()) {
            if (anyOccupied && usedStations.isEmpty()) {
                debugChat(level, villager, "ACQUIRE:all stations occupied, waiting");
                idleUntilTick = gameTime + OCCUPIED_BACKOFF;
                setBlocked(level, villager, gameTime, BlockedReason.UNREACHABLE, "");
                return;
            }
            debugChat(level, villager, "ACQUIRE:all stations tried, resetting");
            usedStations.clear();
            recipeAttempts = 0;
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        StationSlot best = fresh.get(ThreadLocalRandom.current().nextInt(fresh.size()));

        BlockPos stand = findCafeStandingPosition(level, villager, best.pos(), cafeSnapshot);
        if (stand == null) {
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
        transition(BaristaState.SELECT_RECIPE, gameTime);
    }

    // ── State: SELECT_RECIPE ──

    private void tickSelectRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;

        if (stationAnchor == null || !StationHandler.isStation(level, stationAnchor)) {
            transition(BaristaState.PATH_TO_STATION, gameTime);
            return;
        }

        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        int tier = Math.max(1, FarmersDelightBaristaAssignment.effectiveRecipeTier(level, villager));
        DiscoveredRecipe recipe = RecipeSelector.pickRecipe(
                level, villager, stationType, stationAnchor, cafeBounds, recipeCooldownUntil,
                false, true, tier);

        if (recipe == null) {
            int available = ModRecipeRegistry.getBeverageRecipesForStation(level, stationType, tier).size();
            debugChat(level, villager, "SELECT:no beverage recipe for " + stationType.name()
                    + " (tier=" + tier + ", candidates=" + available + "), rotating");
            usedStations.add(stationAnchor.asLong());
            setBlocked(level, villager, gameTime, BlockedReason.NO_RECIPE, townstead$stationDisplayName(stationType));
            transition(BaristaState.PATH_TO_STATION, gameTime);
            return;
        }

        activeRecipe = recipe;
        recipeAttempts = 0;
        debugChat(level, villager, "SELECT:" + recipe.output() + " tier=" + recipe.tier());
        if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
            String container = recipe.containerItemId() == null
                    ? "none"
                    : recipe.containerItemId() + " x" + recipe.containerCount();
            debugChat(level, villager, "SELECT-DETAIL:id=" + recipe.id()
                    + " inputs=" + recipe.inputs().size()
                    + " container=" + container
                    + " station=" + recipe.stationType());
        }
        transition(BaristaState.GATHER, gameTime);
    }

    // ── State: GATHER ──

    private void tickGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;
        if (activeRecipe == null) {
            transition(BaristaState.SELECT_RECIPE, gameTime);
            return;
        }

        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
            String missingBefore = IngredientResolver.describeMissingRequirements(level, villager, activeRecipe, stationAnchor, cafeBounds);
            debugChat(level, villager, "GATHER:start recipe=" + activeRecipe.output()
                    + " station=" + stationType
                    + " missingBefore=" + (missingBefore == null || missingBefore.isBlank() ? "<none>" : missingBefore));
        }
        IngredientResolver.PullResult pullResult = IngredientResolver.pullAndConsumeDetailed(
                level, villager, activeRecipe, stationAnchor, stationType, stagedInputs, cafeBounds);
        boolean success = pullResult.success();

        if (!success) {
            debugChat(level, villager, "GATHER:failed for " + activeRecipe.output()
                    + (pullResult.detail().isBlank() ? "" : " (" + pullResult.detail() + ")"));
            for (String diagnostic : pullResult.diagnostics()) {
                debugChat(level, villager, "GATHER:diag=" + diagnostic);
            }
            Set<ResourceLocation> soughtIds = new LinkedHashSet<>();
            for (RecipeIngredient ingredient : activeRecipe.inputs()) {
                soughtIds.addAll(ingredient.itemIds());
            }
            debugChat(level, villager, "GATHER:buildingStorage=" + IngredientResolver.describeKitchenStorage(level, cafeBounds, soughtIds));
            if (stationAnchor != null && stationType == StationType.HOT_STATION) {
                debugChat(level, villager, "GATHER:pot=" + StationHandler.describeCookingPotInputs(level, stationAnchor));
            }
            IngredientResolver.rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
            String recipeName = townstead$itemDisplayName(level, activeRecipe.output());
            String missing = IngredientResolver.describeMissingRequirements(level, villager, activeRecipe, stationAnchor, cafeBounds);
            if (missing == null || missing.isBlank()) missing = pullResult.detail();
            setBlocked(level, villager, gameTime, BlockedReason.NO_INGREDIENTS,
                    missing == null || missing.isBlank() ? recipeName : missing);
            recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
            activeRecipe = null;
            recipeAttempts++;
            if (recipeAttempts >= MAX_RECIPE_ATTEMPTS) {
                debugChat(level, villager, "GATHER:max attempts, rotating station");
                usedStations.add(stationAnchor.asLong());
                idleUntilTick = gameTime + IDLE_BACKOFF;
                transitionToNavigationState(villager, gameTime);
            } else {
                transition(BaristaState.SELECT_RECIPE, gameTime);
            }
            return;
        }

        debugChat(level, villager, "GATHER:success for " + activeRecipe.output());
        if (TownsteadConfig.DEBUG_VILLAGER_AI.get() && stationAnchor != null && stationType == StationType.HOT_STATION) {
            debugChat(level, villager, "GATHER:pot=" + StationHandler.describeCookingPotInputs(level, stationAnchor));
        }
        brewDoneTick = gameTime + activeRecipe.cookTimeTicks();
        playSound(level);
        transition(BaristaState.BREW, gameTime);
    }

    // ── State: BREW ──

    private void tickBrew(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
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
            Set<Long> cafeBounds = activeCafeStorageBounds(villager);
            for (ItemStack drop : drops) {
                IngredientResolver.storeOutputInCookStorage(level, villager, drop, stationAnchor, cafeBounds);
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

        if (gameTime < brewDoneTick) return;
        if (stationType == StationType.HOT_STATION && !hotStationOutputCollectible(level)) {
            return;
        }

        debugChat(level, villager, "BREW:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        transition(BaristaState.COLLECT, gameTime);
    }

    // ── State: COLLECT ──

    private void tickCollect(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            transition(BaristaState.SELECT_RECIPE, gameTime);
            return;
        }

        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        boolean collected = false;

        // Collect surface drops (campfire/fire station items that pop out)
        if (stationAnchor != null) {
            collected |= collectAndStoreSurfaceDrops(level, villager, cafeBounds, outputIds);
        }

        // Extract output from hot station (cooking pot)
        if (stationType == StationType.HOT_STATION && stationAnchor != null) {
            Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
            if (outputItem != Items.AIR) {
                int extracted = StationHandler.extractFromStation(level, stationAnchor, outputItem, activeRecipe.outputCount());
                if (extracted > 0) {
                    ItemStack output = new ItemStack(outputItem, extracted);
                    IngredientResolver.storeOutputInCookStorage(level, villager, output, stationAnchor, cafeBounds);
                    if (!output.isEmpty()) {
                        villager.getInventory().addItem(output);
                    }
                    collected = true;
                } else {
                    // Output not ready yet — wait for the station to finish
                    transition(BaristaState.COLLECT_WAIT, gameTime);
                    return;
                }
            }
        }

        // For fire station, if nothing was collected yet, wait for items to land
        if (stationType == StationType.FIRE_STATION && !collected) {
            transition(BaristaState.COLLECT_WAIT, gameTime);
            return;
        }

        finishCollect(level, villager, cafeBounds, outputIds, gameTime);
    }

    // ── State: COLLECT_WAIT ──

    private void tickCollectWait(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            transition(BaristaState.SELECT_RECIPE, gameTime);
            return;
        }

        // Keep looking at the station while waiting
        if (stationAnchor != null && standPos != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        }

        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        boolean collected = false;

        // Try collecting surface drops
        if (stationAnchor != null) {
            collected |= collectAndStoreSurfaceDrops(level, villager, cafeBounds, outputIds);
        }

        // Try extracting from hot station
        if (stationType == StationType.HOT_STATION && stationAnchor != null) {
            Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
            if (outputItem != Items.AIR) {
                int extracted = StationHandler.extractFromStation(level, stationAnchor, outputItem, activeRecipe.outputCount());
                if (extracted > 0) {
                    ItemStack output = new ItemStack(outputItem, extracted);
                    IngredientResolver.storeOutputInCookStorage(level, villager, output, stationAnchor, cafeBounds);
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
            finishCollect(level, villager, cafeBounds, outputIds, gameTime);
        }
    }

    // ── Shared collect helpers ──

    private boolean collectAndStoreSurfaceDrops(ServerLevel level, VillagerEntityMCA villager,
                                                 Set<Long> cafeBounds, Set<ResourceLocation> outputIds) {
        List<ItemStack> drops = StationHandler.collectSurfaceCookDrops(level, stationAnchor, outputIds);
        if (drops.isEmpty()) return false;
        for (ItemStack drop : drops) {
            IngredientResolver.storeOutputInCookStorage(level, villager, drop, stationAnchor, cafeBounds);
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

    private boolean hotStationOutputCollectible(ServerLevel level) {
        if (!hotStationOutputReady(level) || stationAnchor == null || activeRecipe == null) return false;
        Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
        if (outputItem == Items.AIR) return false;
        return StationHandler.canExtractFromStation(level, stationAnchor, outputItem, activeRecipe.outputCount());
    }

    private void finishCollect(ServerLevel level, VillagerEntityMCA villager,
                               Set<Long> cafeBounds, Set<ResourceLocation> outputIds, long gameTime) {
        // Store any pending output
        if (!pendingOutput.isEmpty()) {
            IngredientResolver.storeOutputInCookStorage(level, villager, pendingOutput, stationAnchor, cafeBounds);
            if (!pendingOutput.isEmpty()) {
                villager.getInventory().addItem(pendingOutput);
            }
            pendingOutput = ItemStack.EMPTY;
        }

        // Store any inventory items that are recipe outputs
        net.minecraft.world.SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null && outputIds.contains(itemId)) {
                IngredientResolver.storeOutputInCookStorage(level, villager, stack, stationAnchor, cafeBounds);
            }
        }

        // Award XP
        awardBaristaXP(level, villager);

        // Mark recipe cooldown
        if (activeRecipe != null) {
            recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
        }

        debugChat(level, villager, "COLLECT:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        activeRecipe = null;
        stagedInputs.clear();

        if (stationAnchor != null) usedStations.add(stationAnchor.asLong());

        // Station rotation: 50% chance to rotate
        if (ThreadLocalRandom.current().nextDouble() < 0.5d) {
            transition(BaristaState.PATH_TO_STATION, gameTime);
        } else {
            transition(BaristaState.SELECT_RECIPE, gameTime);
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
        Set<Long> cafeBounds = activeCafeStorageBounds(villager);
        BlockPos storageRef = stationAnchor != null ? stationAnchor : villager.blockPosition();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) continue;
            drop.discard();
            IngredientResolver.storeOutputInCookStorage(level, villager, stack, storageRef, cafeBounds);
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
            transitionToNavigationState(villager, gameTime);
            return false;
        }

        if (!isVillagerInActiveCafe(villager)) {
            transitionToNavigationState(villager, gameTime);
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
                BlockPos refreshed = findCafeStandingPosition(level, villager, stationAnchor,
                        activeCafeSnapshot(level, villager));
                if (refreshed != null) standPos = refreshed;
            }
            return false;
        }
        nextStandReacquireTick = 0L;
        return true;
    }

    // ── State transition ──

    private void transition(BaristaState newState, long gameTime) {
        state = newState;
        stateEnteredTick = gameTime;
    }

    private void transitionToNavigationState(VillagerEntityMCA villager, long gameTime) {
        transition(isVillagerInActiveCafe(villager) ? BaristaState.PATH_TO_STATION : BaristaState.PATH_TO_WORKSITE, gameTime);
    }

    private void resetWorksiteTargeting() {
        currentWorksiteTarget = null;
        currentWorksiteTargetKind = "stand";
        worksiteTargetProgress.reset();
        worksiteTargetFailures.reset();
    }

    private @Nullable BlockPos currentOrNewCafeWorksiteTarget(
            VillagerEntityMCA villager,
            long gameTime,
            WorkBuildingNav.Snapshot cafeSnapshot
    ) {
        if (currentWorksiteTarget != null
                && !worksiteTargetFailures.isBlacklisted(currentWorksiteTarget, gameTime)) {
            return currentWorksiteTarget;
        }

        List<BlockPos> standCandidates = cafeSnapshot.stationStandPositions().values().stream()
                .flatMap(List::stream)
                .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                .distinct()
                .sorted(Comparator.comparingDouble(pos ->
                        villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        if (!standCandidates.isEmpty()) {
            currentWorksiteTargetKind = "stand";
            currentWorksiteTarget = standCandidates.get(0);
            return currentWorksiteTarget;
        }

        List<BlockPos> fallbackCandidates = cafeSnapshot.approachTargets().stream()
                .filter(pos -> !worksiteTargetFailures.isBlacklisted(pos, gameTime))
                .sorted(Comparator.comparingDouble(pos ->
                        villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)))
                .toList();
        if (fallbackCandidates.isEmpty()) {
            currentWorksiteTarget = null;
            return null;
        }
        currentWorksiteTargetKind = "fallback";
        currentWorksiteTarget = fallbackCandidates.get(0);
        return currentWorksiteTarget;
    }

    private @Nullable BlockPos findCafeStandingPosition(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos anchor,
            WorkBuildingNav.Snapshot cafeSnapshot
    ) {
        BlockPos stand = WorkBuildingNav.nearestStationStand(cafeSnapshot, villager, anchor);
        if (stand != null) return stand;
        BlockPos fallback = StationHandler.findStandingPosition(level, villager, anchor);
        if (fallback != null && cafeSnapshot.walkableInterior().contains(fallback.asLong())) {
            return fallback.immutable();
        }
        return null;
    }

    // ── Cafe bounds ──

    private Set<Long> activeCafeStorageBounds(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return Set.of();
        return FarmersDelightBaristaAssignment.assignedCafeBounds(level, villager);
    }

    private void cacheCafeNavigationArea(BlockPos anchor, long gameTime, Set<Long> bounds) {
        cachedCafeNavigationAnchor = anchor == null ? null : anchor.immutable();
        cachedCafeNavigationArea = bounds == null ? Set.of() : bounds;
        cachedCafeNavigationUntil = gameTime + ROOM_BOUNDS_CACHE_TICKS;
    }

    private WorkBuildingNav.Snapshot activeCafeSnapshot(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos anchor = activeCafeReference(villager);
        long gameTime = level.getGameTime();
        if (anchor != null && cachedCafeNavigationAnchor != null
                && anchor.equals(cachedCafeNavigationAnchor)
                && gameTime <= cachedCafeNavigationUntil
                && !cachedCafeSnapshot.walkableInterior().isEmpty()) {
            return cachedCafeSnapshot;
        }
        Set<Long> assigned = activeCafeStorageBounds(villager);
        WorkBuildingNav.Snapshot snapshot = WorkBuildingNav.snapshot(level, assigned, anchor);
        cachedCafeSnapshot = snapshot;
        cacheCafeNavigationArea(anchor, gameTime, snapshot.walkableInterior());
        return snapshot;
    }

    private BlockPos activeCafeReference(VillagerEntityMCA villager) {
        if (villager.level() instanceof ServerLevel level) {
            Optional<Building> assigned = FarmersDelightBaristaAssignment.assignedCafe(level, villager);
            if (assigned.isPresent()) {
                BlockPos center = assigned.get().getCenter();
                if (center != null) return center;
                for (BlockPos bp : (Iterable<BlockPos>) assigned.get().getBlockPosStream()::iterator) {
                    return bp.immutable();
                }
            }
        }
        if (stationAnchor != null) return stationAnchor;
        return villager.blockPosition();
    }

    private boolean isVillagerInActiveCafe(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        return WorkBuildingNav.isInsideOrOnStationStand(activeCafeSnapshot(level, villager), villager.blockPosition());
    }

    // ── Station claims ──

    private void releaseStationClaim(VillagerEntityMCA villager, @Nullable BlockPos pos) {
        if (villager == null || pos == null) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        CookStationClaims.release(level, villager.getUUID(), pos);
    }

    // ── XP ──

    private void awardBaristaXP(ServerLevel level, VillagerEntityMCA villager) {
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
        level.playSound(null, stationAnchor, SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.0f);
    }

    // ── Blocked state ──

    private void setBlocked(ServerLevel level, VillagerEntityMCA villager, long gameTime, BlockedReason reason, String itemName) {
        blocked = reason;
        if (reason != BlockedReason.NONE) {
            BlockPos pos = villager.blockPosition();
            debugChat(level, villager, "BLOCKED:" + reason.name()
                    + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + (itemName == null || itemName.isBlank() ? "" : " detail=" + itemName));
        }
        if (reason == BlockedReason.NONE) return;
        if (!TownsteadConfig.isBaristaRequestChatEnabled()) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) return;
        if (reason == BlockedReason.UNREACHABLE
                && !shouldAnnounceBlockedNavigation(level, villager, activeWorkTarget(level, villager))) {
            return;
        }
        switch (blocked) {
            case NO_CAFE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_cafe/" + (1 + level.random.nextInt(4)));
            case NO_INGREDIENTS -> {
                if (itemName != null && !itemName.isBlank()) {
                    villager.sendChatToAllAround("dialogue.chat.barista_request.no_ingredients_item", itemName);
                } else {
                    villager.sendChatToAllAround("dialogue.chat.barista_request.no_ingredients/" + (1 + level.random.nextInt(4)));
                }
            }
            case NO_STORAGE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_storage/" + (1 + level.random.nextInt(4)));
            case UNREACHABLE -> villager.sendChatToAllAround("dialogue.chat.barista_request.unreachable/" + (1 + level.random.nextInt(4)));
            case NO_RECIPE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_recipe_item", itemName.isBlank() ? "that station" : itemName);
            case NONE -> {}
        }
        nextRequestTick = gameTime + Math.max(200, TownsteadConfig.BARISTA_REQUEST_INTERVAL_TICKS.get());
    }

    // ── Reset ──

    private void clearAll(VillagerEntityMCA villager, long gameTime) {
        releaseStationClaim(villager, stationAnchor);
        stationAnchor = null;
        standPos = null;
        stationType = null;
        activeRecipe = null;
        pendingOutput = ItemStack.EMPTY;
        stagedInputs.clear();
        usedStations.clear();
        recipeCooldownUntil.clear();
        recipeAttempts = 0;
        idleUntilTick = 0L;
        resetWorksiteTargeting();
        state = BaristaState.PATH_TO_WORKSITE;
        stateEnteredTick = gameTime;
        cachedCafeNavigationArea = Set.of();
        cachedCafeNavigationAnchor = null;
        cachedCafeNavigationUntil = 0L;
    }

    // ── Activity ──

    private Activity currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    @Override
    public WorkSiteRef activeWorkSite(ServerLevel level, VillagerEntityMCA villager) {
        Set<Long> bounds = activeCafeStorageBounds(villager);
        BlockPos reference = activeCafeReference(villager);
        return bounds.isEmpty() ? null : WorkSiteRef.building(reference, bounds);
    }

    @Override
    public WorkTarget activeWorkTarget(ServerLevel level, VillagerEntityMCA villager) {
        if (currentWorksiteTarget != null && state == BaristaState.PATH_TO_WORKSITE) {
            return WorkTarget.buildingApproach(currentWorksiteTarget, activeCafeReference(villager), currentWorksiteTargetKind);
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
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.literal("[Barista:" + villager.getName().getString() + "] " + msg));
    }

    private void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime < nextDebugTick) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        String name = villager.getName().getString();
        String id = villager.getUUID().toString();
        if (id.length() > 8) id = id.substring(0, 8);
        String recipe = activeRecipe == null ? "none" : activeRecipe.output().toString();
        String anchor = stationAnchor == null ? "none" : stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ();
        String station = stationType == null ? "none" : stationType.name().toLowerCase();
        String idleInfo = gameTime < idleUntilTick ? " idle=" + (idleUntilTick - gameTime) : "";
        StorageSearchContext.Snapshot storageSnapshot = StorageSearchContext.Profiler.snapshot();
        VillageAiBudget.Snapshot budgetSnapshot = VillageAiBudget.snapshot();
        WorkNavigationMetrics.Snapshot navSnapshot = WorkNavigationMetrics.snapshot();
        ConsumableTargetClaims.Snapshot claimSnapshot = ConsumableTargetClaims.snapshot();
        WorkBuildingNav.Snapshot cafeSnapshot = activeCafeSnapshot(level, villager);
        Optional<Building> assignedCafe = FarmersDelightBaristaAssignment.assignedCafe(level, villager);
        String assignedCafeDesc = assignedCafe.map(this::townstead$describeAssignedBuilding).orElse("none");
        String navMode = townstead$navigationMode(level, villager);
        player.sendSystemMessage(Component.literal("[BaristaDBG:" + name + "#" + id + "] state=" + state.name()
                + " station=" + station + " anchor=" + anchor + " recipe=" + recipe
                + " doneAt=" + brewDoneTick + " blocked=" + blocked.name()
                + " mode=" + navMode + " site=" + assignedCafeDesc + " stations=" + cafeSnapshot.stations().size()
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
        if (state == BaristaState.PATH_TO_WORKSITE) {
            return "approach:" + currentWorksiteTargetKind;
        }
        if (state == BaristaState.PATH_TO_STATION) {
            return stationAnchor != null ? "path_to_station" : "search";
        }
        return "station";
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
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        if (item == null) return itemId.getPath();
        return item.getDefaultInstance().getHoverName().getString();
    }
}
