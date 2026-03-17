package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.IngredientResolver;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.RecipeSelector;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler.StationSlot;
import com.aetherianartificer.townstead.hunger.CookProgressData;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BaristaWorkTask extends Behavior<VillagerEntityMCA> {

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
    private static final long ROOM_BOUNDS_CACHE_TICKS = 80L;
    private static final int ROOM_SEARCH_EXPAND_XZ = 24;
    private static final int ROOM_SEARCH_EXPAND_Y = 6;
    private static final int ROOM_FLOOD_MAX_NODES = 16384;

    // ── State machine ──

    private enum BaristaState { ACQUIRE_STATION, SELECT_RECIPE, GATHER, BREW, COLLECT, COLLECT_WAIT }
    private enum BlockedReason { NONE, NO_CAFE, NO_INGREDIENTS, NO_RECIPE, NO_STORAGE, UNREACHABLE }

    private static final int COLLECT_WAIT_MAX_TICKS = 40;

    private BaristaState state = BaristaState.ACQUIRE_STATION;
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

    // Cafe bounds cache
    private Set<Long> cachedCafeWorkArea = Set.of();
    private BlockPos cachedCafeWorkAnchor = null;
    private long cachedCafeWorkUntil = 0L;

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
        state = BaristaState.ACQUIRE_STATION;
        stateEnteredTick = gameTime;
        recipeAttempts = 0;
        usedStations.clear();
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
        cachedCafeWorkArea = Set.of();
        cachedCafeWorkAnchor = null;
        cachedCafeWorkUntil = 0L;
        state = BaristaState.ACQUIRE_STATION;
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
        if (gameTime - stateEnteredTick > STATE_TIMEOUT_TICKS && state != BaristaState.BREW && state != BaristaState.COLLECT_WAIT) {
            debugChat(level, villager, "STATE:timeout in " + state.name() + ", resetting");
            transition(BaristaState.ACQUIRE_STATION, gameTime);
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
            case ACQUIRE_STATION -> tickAcquireStation(level, villager, gameTime);
            case SELECT_RECIPE -> tickSelectRecipe(level, villager, gameTime);
            case GATHER -> tickGather(level, villager, gameTime);
            case BREW -> tickBrew(level, villager, gameTime);
            case COLLECT -> tickCollect(level, villager, gameTime);
            case COLLECT_WAIT -> tickCollectWait(level, villager, gameTime);
        }
    }

    // ── State: ACQUIRE_STATION ──

    private void tickAcquireStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationClaim(villager, stationAnchor);
        activeRecipe = null;
        stagedInputs.clear();

        Set<Long> cafeBounds = activeCafeBounds(villager, activeCafeReference(villager));
        if (cafeBounds.isEmpty()) {
            setBlocked(level, villager, gameTime, BlockedReason.NO_CAFE, "");
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        List<StationSlot> stations = StationHandler.discoverStations(level, villager, cafeBounds, SEARCH_RADIUS, VERTICAL_RADIUS);
        // Filter to only HOT_STATION and FIRE_STATION (barista doesn't use cutting board)
        stations = stations.stream()
                .filter(s -> s.type() == StationType.HOT_STATION || s.type() == StationType.FIRE_STATION)
                .toList();
        if (stations.isEmpty()) {
            debugChat(level, villager, "ACQUIRE:no stations found in cafe (" + cafeBounds.size() + " bounds)");
            setBlocked(level, villager, gameTime, BlockedReason.NO_CAFE, "");
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
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

        BlockPos stand = StationHandler.findStandingPosition(level, villager, best.pos());
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
            transition(BaristaState.ACQUIRE_STATION, gameTime);
            return;
        }

        Set<Long> cafeBounds = activeCafeBounds(villager, activeCafeReference(villager));
        int tier = Math.max(1, FarmersDelightBaristaAssignment.effectiveRecipeTier(level, villager));
        DiscoveredRecipe recipe = RecipeSelector.pickRecipe(
                level, villager, stationType, stationAnchor, cafeBounds, recipeCooldownUntil,
                false, true, tier);

        if (recipe == null) {
            int available = ModRecipeRegistry.getBeverageRecipesForStation(level, stationType, tier).size();
            debugChat(level, villager, "SELECT:no beverage recipe for " + stationType.name()
                    + " (tier=" + tier + ", candidates=" + available + "), rotating");
            usedStations.add(stationAnchor.asLong());
            setBlocked(level, villager, gameTime, BlockedReason.NO_RECIPE, "");
            transition(BaristaState.ACQUIRE_STATION, gameTime);
            return;
        }

        activeRecipe = recipe;
        recipeAttempts = 0;
        debugChat(level, villager, "SELECT:" + recipe.output() + " tier=" + recipe.tier());
        transition(BaristaState.GATHER, gameTime);
    }

    // ── State: GATHER ──

    private void tickGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;
        if (activeRecipe == null) {
            transition(BaristaState.SELECT_RECIPE, gameTime);
            return;
        }

        Set<Long> cafeBounds = activeCafeBounds(villager, activeCafeReference(villager));
        boolean success = IngredientResolver.pullAndConsume(
                level, villager, activeRecipe, stationAnchor, stationType, stagedInputs, cafeBounds);

        if (!success) {
            debugChat(level, villager, "GATHER:failed for " + activeRecipe.output());
            IngredientResolver.rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
            setBlocked(level, villager, gameTime, BlockedReason.NO_INGREDIENTS, "");
            recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
            activeRecipe = null;
            recipeAttempts++;
            if (recipeAttempts >= MAX_RECIPE_ATTEMPTS) {
                debugChat(level, villager, "GATHER:max attempts, rotating station");
                usedStations.add(stationAnchor.asLong());
                idleUntilTick = gameTime + IDLE_BACKOFF;
                transition(BaristaState.ACQUIRE_STATION, gameTime);
            } else {
                transition(BaristaState.SELECT_RECIPE, gameTime);
            }
            return;
        }

        debugChat(level, villager, "GATHER:success for " + activeRecipe.output());
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
            Set<Long> cafeBounds = activeCafeBounds(villager, activeCafeReference(villager));
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

        debugChat(level, villager, "BREW:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        transition(BaristaState.COLLECT, gameTime);
    }

    // ── State: COLLECT ──

    private void tickCollect(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            transition(BaristaState.SELECT_RECIPE, gameTime);
            return;
        }

        Set<Long> cafeBounds = activeCafeBounds(villager, activeCafeReference(villager));
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

        Set<Long> cafeBounds = activeCafeBounds(villager, activeCafeReference(villager));
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
                // Timed out waiting — produce virtual output as fallback
                Item outputItem = BuiltInRegistries.ITEM.get(activeRecipe.output());
                if (outputItem != Items.AIR) {
                    debugChat(level, villager, "COLLECT_WAIT:timeout, virtual output for " + activeRecipe.output());
                    ItemStack output = new ItemStack(outputItem, activeRecipe.outputCount());
                    IngredientResolver.storeOutputInCookStorage(level, villager, output, stationAnchor, cafeBounds);
                    if (!output.isEmpty()) {
                        villager.getInventory().addItem(output);
                    }
                }
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
            transition(BaristaState.ACQUIRE_STATION, gameTime);
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
        Set<Long> cafeBounds = activeCafeBounds(villager, activeCafeReference(villager));
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
            transition(BaristaState.ACQUIRE_STATION, gameTime);
            return false;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        double distSq = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5);
        double anchorDistSq = villager.distanceToSqr(
                stationAnchor.getX() + 0.5, stationAnchor.getY() + 0.5, stationAnchor.getZ() + 0.5);
        boolean inCafe = isVillagerInActiveCafe(villager);

        if (distSq > ARRIVAL_DISTANCE_SQ && anchorDistSq > NEAR_STATION_DISTANCE_SQ && !inCafe) {
            if (gameTime >= nextStandReacquireTick) {
                nextStandReacquireTick = gameTime + STAND_REACQUIRE_INTERVAL_TICKS;
                BlockPos refreshed = StationHandler.findStandingPosition(level, villager, stationAnchor);
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

    // ── Cafe bounds ──

    private Set<Long> activeCafeBounds(VillagerEntityMCA villager, BlockPos anchor) {
        long gameTime = villager.level().getGameTime();
        if (anchor != null && cachedCafeWorkAnchor != null
                && anchor.equals(cachedCafeWorkAnchor)
                && gameTime <= cachedCafeWorkUntil
                && !cachedCafeWorkArea.isEmpty()) {
            return cachedCafeWorkArea;
        }

        if (villager.level() instanceof ServerLevel level) {
            Set<Long> assigned = FarmersDelightBaristaAssignment.assignedCafeBounds(level, villager);
            if (!assigned.isEmpty()) {
                Set<Long> roomAware = roomExpandedCafeArea(level, assigned, anchor != null ? anchor : villager.blockPosition());
                cacheCafeWorkArea(anchor, gameTime, roomAware);
                return roomAware;
            }
        }

        return Set.of();
    }

    private void cacheCafeWorkArea(BlockPos anchor, long gameTime, Set<Long> bounds) {
        cachedCafeWorkAnchor = anchor == null ? null : anchor.immutable();
        cachedCafeWorkArea = bounds == null ? Set.of() : bounds;
        cachedCafeWorkUntil = gameTime + ROOM_BOUNDS_CACHE_TICKS;
    }

    private Set<Long> roomExpandedCafeArea(ServerLevel level, Set<Long> baseBounds, BlockPos reference) {
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
        minX -= ROOM_SEARCH_EXPAND_XZ; maxX += ROOM_SEARCH_EXPAND_XZ;
        minY -= ROOM_SEARCH_EXPAND_Y; maxY += ROOM_SEARCH_EXPAND_Y;
        minZ -= ROOM_SEARCH_EXPAND_XZ; maxZ += ROOM_SEARCH_EXPAND_XZ;

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
        Set<Long> bounds = activeCafeBounds(villager, activeCafeReference(villager));
        if (bounds.isEmpty()) return false;
        return StationHandler.isInKitchenWorkArea(bounds, villager.blockPosition());
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
        if (reason == BlockedReason.NONE) return;
        if (!TownsteadConfig.isBaristaRequestChatEnabled()) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) return;
        switch (blocked) {
            case NO_CAFE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_cafe/" + (1 + level.random.nextInt(4)));
            case NO_INGREDIENTS -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_ingredients/" + (1 + level.random.nextInt(4)));
            case NO_STORAGE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_storage/" + (1 + level.random.nextInt(4)));
            case UNREACHABLE -> villager.sendChatToAllAround("dialogue.chat.barista_request.unreachable/" + (1 + level.random.nextInt(4)));
            case NO_RECIPE -> villager.sendChatToAllAround("dialogue.chat.barista_request.no_recipe/" + (1 + level.random.nextInt(4)));
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
        state = BaristaState.ACQUIRE_STATION;
        stateEnteredTick = gameTime;
        cachedCafeWorkArea = Set.of();
        cachedCafeWorkAnchor = null;
        cachedCafeWorkUntil = 0L;
    }

    // ── Activity ──

    private Activity currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    // ── Debug ──

    private void debugChat(ServerLevel level, VillagerEntityMCA villager, String msg) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        player.sendSystemMessage(Component.literal("[Barista:" + villager.getName().getString() + "] " + msg));
    }

    private void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (gameTime < nextDebugTick) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        String name = villager.getName().getString();
        String id = villager.getUUID().toString();
        if (id.length() > 8) id = id.substring(0, 8);
        String recipe = activeRecipe == null ? "none" : activeRecipe.output().toString();
        String anchor = stationAnchor == null ? "none" : stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ();
        String station = stationType == null ? "none" : stationType.name().toLowerCase();
        String idleInfo = gameTime < idleUntilTick ? " idle=" + (idleUntilTick - gameTime) : "";
        player.sendSystemMessage(Component.literal("[BaristaDBG:" + name + "#" + id + "] state=" + state.name()
                + " station=" + station + " anchor=" + anchor + " recipe=" + recipe
                + " doneAt=" + brewDoneTick + " blocked=" + blocked.name()
                + " used=" + usedStations.size() + idleInfo));
        nextDebugTick = gameTime + 100L;
    }

    private static boolean townstead$isFatigueGated(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return false;
        //? if neoforge {
        net.minecraft.nbt.CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*net.minecraft.nbt.CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        return FatigueData.isGated(fatigue);
    }
}
