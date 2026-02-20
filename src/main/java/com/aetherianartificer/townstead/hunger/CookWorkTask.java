package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

public class CookWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int SEARCH_RADIUS = 24;
    private static final int VERTICAL_RADIUS = 3;
    private static final int CLOSE_ENOUGH = 0;
    private static final int MAX_DURATION = 1200;
    private static final double ARRIVAL_DISTANCE_SQ = 0.36d;
    private static final double NEAR_STATION_DISTANCE_SQ = 4.0d;
    private static final long STAND_REACQUIRE_INTERVAL_TICKS = 60L;
    private static final int REQUEST_RANGE = 24;
    private static final int IDLE_BACKOFF = 80;
    private static final float WALK_SPEED = 0.52f;
    private static final int SHIFT_PLAN_MAX_RECIPES = 6;
    private static final long STATION_CLAIM_BUFFER_TICKS = 20L;
    private static final Map<Long, UUID> STATION_CLAIM_OWNER = new ConcurrentHashMap<>();
    private static final Map<Long, Long> STATION_CLAIM_UNTIL = new ConcurrentHashMap<>();
    private static Class<?> FD_STOVE_BE_CLASS;
    private static Method FD_STOVE_GET_NEXT_EMPTY_SLOT;
    private static Method FD_STOVE_GET_MATCHING_RECIPE;
    private static Method FD_STOVE_ADD_ITEM;
    private static Method FD_STOVE_IS_BLOCKED_ABOVE;
    private static Class<?> FD_SKILLET_BE_CLASS;
    private static Method FD_SKILLET_HAS_STORED_STACK;
    private static Method FD_SKILLET_GET_MATCHING_RECIPE;
    private static Method FD_SKILLET_ADD_ITEM_TO_COOK;

    private static final ResourceLocation FD_CUTTING_BOARD = ResourceLocation.parse("farmersdelight:cutting_board");
    private static final ResourceLocation FD_COOKING_POT = ResourceLocation.parse("farmersdelight:cooking_pot");
    private static final ResourceLocation FD_SKILLET = ResourceLocation.parse("farmersdelight:skillet");
    private static final ResourceLocation FD_STOVE = ResourceLocation.parse("farmersdelight:stove");
    private static final ResourceLocation MINECRAFT_BOWL = ResourceLocation.parse("minecraft:bowl");
    private static final int FD_COOKING_POT_CONTAINER_SLOT = 7;
    private static final Set<ResourceLocation> HOT_VESSEL_POT = Set.of(FD_COOKING_POT);
    private static final Set<ResourceLocation> HOT_VESSEL_SKILLET = Set.of(FD_SKILLET);
    private static final TagKey<Item> KNIFE_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/knife"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_UPGRADED_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_upgraded"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_NETHER_TAG = TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_nether"));

    private enum StationType { CUTTING_BOARD, HOT_STATION, FIRE_STATION }
    private enum BlockedReason { NONE, NO_KITCHEN, NO_INGREDIENTS, NO_RECIPE, NO_STORAGE, UNREACHABLE }
    private enum WorkPhase { IDLE, GATHER, COOK, STORE }

    private record Ingredient(ResourceLocation itemId, int count) {}
    private record RecipeDef(
            int minTier,
            StationType stationType,
            ResourceLocation output,
            int outputCount,
            int timeTicks,
            boolean needsKnife,
            boolean needsWater,
            int bowlsRequired,
            List<Ingredient> inputs,
            Set<ResourceLocation> allowedHotStations
    ) {}

    private static final List<RecipeDef> RECIPES = List.of(
            recipe(1, StationType.FIRE_STATION, "minecraft:cooked_chicken", 1, 100, false, false, in("minecraft:chicken", 1)),
            recipe(1, StationType.FIRE_STATION, "minecraft:cooked_rabbit", 1, 100, false, false, in("minecraft:rabbit", 1)),
            recipe(1, StationType.FIRE_STATION, "minecraft:cooked_cod", 1, 100, false, false, in("minecraft:cod", 1)),
            recipe(1, StationType.FIRE_STATION, "minecraft:cooked_salmon", 1, 100, false, false, in("minecraft:salmon", 1)),
            recipe(1, StationType.FIRE_STATION, "minecraft:cooked_porkchop", 1, 100, false, false, in("minecraft:porkchop", 1)),
            recipe(1, StationType.FIRE_STATION, "minecraft:cooked_mutton", 1, 100, false, false, in("minecraft:mutton", 1)),
            recipe(1, StationType.FIRE_STATION, "minecraft:cooked_beef", 1, 100, false, false, in("minecraft:beef", 1)),
            recipe(2, StationType.CUTTING_BOARD, "farmersdelight:chicken_cuts", 2, 80, true, false, in("minecraft:chicken", 1)),
            recipe(2, StationType.CUTTING_BOARD, "farmersdelight:cod_slice", 2, 80, true, false, in("minecraft:cod", 1)),
            recipe(2, StationType.CUTTING_BOARD, "farmersdelight:salmon_slice", 2, 80, true, false, in("minecraft:salmon", 1)),
            recipe(2, StationType.CUTTING_BOARD, "farmersdelight:mutton_chops", 2, 80, true, false, in("minecraft:mutton", 1)),
            recipe(2, StationType.CUTTING_BOARD, "farmersdelight:bacon", 2, 80, true, false, in("minecraft:porkchop", 1)),
            recipe(2, StationType.CUTTING_BOARD, "farmersdelight:minced_beef", 2, 80, true, false, in("minecraft:beef", 1)),
            recipe(2, StationType.FIRE_STATION, "farmersdelight:cooked_chicken_cuts", 1, 100, false, false, in("farmersdelight:chicken_cuts", 1)),
            recipe(2, StationType.FIRE_STATION, "farmersdelight:cooked_cod_slice", 1, 100, false, false, in("farmersdelight:cod_slice", 1)),
            recipe(2, StationType.FIRE_STATION, "farmersdelight:cooked_salmon_slice", 1, 100, false, false, in("farmersdelight:salmon_slice", 1)),
            recipe(2, StationType.FIRE_STATION, "farmersdelight:cooked_mutton_chops", 1, 100, false, false, in("farmersdelight:mutton_chops", 1)),
            recipe(2, StationType.FIRE_STATION, "farmersdelight:cooked_bacon", 1, 100, false, false, in("farmersdelight:bacon", 1)),
            recipe(2, StationType.FIRE_STATION, "farmersdelight:beef_patty", 1, 100, false, false, in("farmersdelight:minced_beef", 1)),
            recipe(3, StationType.FIRE_STATION, "farmersdelight:fried_egg", 1, 100, false, false, in("minecraft:egg", 1)),
            hotRecipe(3, "farmersdelight:fried_egg", 1, 80, false, false, HOT_VESSEL_SKILLET, in("minecraft:egg", 1)),
            hotRecipe(3, "farmersdelight:cooked_rice", 1, 100, false, false, 1, HOT_VESSEL_POT, in("farmersdelight:rice", 1)),
            hotRecipe(3, "minecraft:mushroom_stew", 1, 120, false, false, 1, HOT_VESSEL_POT,
                    in("minecraft:brown_mushroom", 1), in("minecraft:red_mushroom", 1)),
            hotRecipe(3, "minecraft:beetroot_soup", 1, 120, false, false, 1, HOT_VESSEL_POT,
                    in("minecraft:beetroot", 3)),
            hotRecipe(3, "farmersdelight:beef_stew", 1, 140, false, false, 1, HOT_VESSEL_POT,
                    in("minecraft:beef", 1), in("minecraft:carrot", 1), in("minecraft:potato", 1)),
            hotRecipe(3, "farmersdelight:baked_cod_stew", 1, 140, false, false, 1, HOT_VESSEL_POT,
                    in("minecraft:cod", 1), in("minecraft:potato", 1), in("minecraft:egg", 1), in("farmersdelight:tomato", 1)),
            hotRecipe(3, "farmersdelight:fried_rice", 1, 120, false, false, 1, HOT_VESSEL_POT,
                    in("farmersdelight:rice", 1), in("minecraft:egg", 1), in("minecraft:carrot", 1), in("farmersdelight:onion", 1)),
            hotRecipe(3, "farmersdelight:vegetable_soup", 1, 130, false, false, 1, HOT_VESSEL_POT,
                    in("minecraft:carrot", 1), in("minecraft:potato", 1), in("minecraft:beetroot", 1), in("farmersdelight:cabbage", 1)),
            hotRecipe(3, "minecraft:cooked_chicken", 1, 90, false, false, HOT_VESSEL_SKILLET, in("minecraft:chicken", 1)),
            hotRecipe(3, "minecraft:cooked_cod", 1, 90, false, false, HOT_VESSEL_SKILLET, in("minecraft:cod", 1)),
            hotRecipe(3, "minecraft:cooked_salmon", 1, 90, false, false, HOT_VESSEL_SKILLET, in("minecraft:salmon", 1)),
            hotRecipe(3, "minecraft:cooked_mutton", 1, 90, false, false, HOT_VESSEL_SKILLET, in("minecraft:mutton", 1)),
            hotRecipe(3, "minecraft:cooked_beef", 1, 90, false, false, HOT_VESSEL_SKILLET, in("minecraft:beef", 1)),
            hotRecipe(3, "farmersdelight:cooked_chicken_cuts", 1, 90, false, false, HOT_VESSEL_SKILLET, in("farmersdelight:chicken_cuts", 1)),
            hotRecipe(3, "farmersdelight:cooked_cod_slice", 1, 90, false, false, HOT_VESSEL_SKILLET, in("farmersdelight:cod_slice", 1)),
            hotRecipe(3, "farmersdelight:cooked_salmon_slice", 1, 90, false, false, HOT_VESSEL_SKILLET, in("farmersdelight:salmon_slice", 1)),
            hotRecipe(3, "farmersdelight:cooked_mutton_chops", 1, 90, false, false, HOT_VESSEL_SKILLET, in("farmersdelight:mutton_chops", 1)),
            hotRecipe(3, "farmersdelight:cooked_bacon", 1, 90, false, false, HOT_VESSEL_SKILLET, in("farmersdelight:bacon", 1)),
            hotRecipe(3, "farmersdelight:beef_patty", 1, 90, false, false, HOT_VESSEL_SKILLET, in("farmersdelight:minced_beef", 1)),
            hotRecipe(4, "farmersdelight:tomato_sauce", 1, 120, false, false, HOT_VESSEL_POT, in("farmersdelight:tomato", 2))
    );
    private static final Set<ResourceLocation> RECIPE_OUTPUT_IDS;
    private static final Set<ResourceLocation> RECIPE_INPUT_IDS;
    private static final Set<ResourceLocation> STATION_UNLOAD_IDS;

    static {
        Set<ResourceLocation> outputs = new HashSet<>();
        Set<ResourceLocation> inputs = new HashSet<>();
        for (RecipeDef recipe : RECIPES) {
            outputs.add(recipe.output());
            if (recipe.bowlsRequired() > 0) {
                inputs.add(MINECRAFT_BOWL);
            }
            for (Ingredient ingredient : recipe.inputs()) {
                inputs.add(ingredient.itemId());
            }
        }
        Set<ResourceLocation> unloadOnly = new HashSet<>(outputs);
        unloadOnly.removeAll(inputs);
        RECIPE_OUTPUT_IDS = Collections.unmodifiableSet(outputs);
        RECIPE_INPUT_IDS = Collections.unmodifiableSet(inputs);
        STATION_UNLOAD_IDS = Collections.unmodifiableSet(unloadOnly);
    }

    private BlockPos stationAnchor;
    private BlockPos standPos;
    private StationType stationType;
    private RecipeDef activeRecipe;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private long cookDoneTick;
    private long nextAcquireTick;
    private long nextKnifeAcquireTick;
    private long nextDebugTick;
    private long nextRequestTick;
    private long nextStandReacquireTick;
    private BlockedReason blocked = BlockedReason.NONE;
    private String unsupportedItem = "";
    private String lastFailure = "none";
    private String lastOutputTrace = "none";
    private String lastOutputDest = "none";
    private final Map<ResourceLocation, Integer> stagedInputs = new HashMap<>();
    private final List<RecipeDef> shiftPlan = new ArrayList<>();
    private int shiftPlanIndex;
    private WorkPhase workPhase = WorkPhase.IDLE;
    private ResourceLocation lastCompletedOutput = null;
    private int consecutiveCompletedOutput = 0;
    private BlockPos lastWorkedStationAnchor = null;

    public CookWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        VillagerBrain<?> brain = villager.getVillagerBrain();
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!FarmersDelightCookAssignment.isExternalCookProfession(profession)) return false;
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        return townstead$currentActivity(villager) == Activity.WORK;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!FarmersDelightCookAssignment.isExternalCookProfession(villager.getVillagerData().getProfession())) {
            return;
        }
        blocked = BlockedReason.NONE;
        unsupportedItem = "";
        lastFailure = "none";
        nextAcquireTick = 0L;
        nextStandReacquireTick = 0L;
        if (shiftPlan.isEmpty() || shiftPlanIndex >= shiftPlan.size()) {
            townstead$buildShiftPlan(level, villager);
        }
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!FarmersDelightCookAssignment.isExternalCookProfession(villager.getVillagerData().getProfession())) {
            townstead$releaseStationClaim(villager, stationAnchor);
            stationAnchor = null;
            standPos = null;
            stationType = null;
            activeRecipe = null;
            pendingOutput = ItemStack.EMPTY;
            stagedInputs.clear();
            shiftPlan.clear();
            shiftPlanIndex = 0;
            workPhase = WorkPhase.IDLE;
            lastFailure = "none";
            lastCompletedOutput = null;
            consecutiveCompletedOutput = 0;
            nextStandReacquireTick = 0L;
            lastWorkedStationAnchor = null;
            return;
        }

        townstead$debugTick(level, villager, gameTime);

        if (!townstead$hasKnife(villager.getInventory()) && gameTime >= nextKnifeAcquireTick) {
            BlockPos knifeCenter = stationAnchor != null ? stationAnchor : townstead$nearestKitchenAnchor(villager);
            townstead$pullSingleTool(level, villager, townstead$isKnifeStackPredicate(), knifeCenter);
            nextKnifeAcquireTick = gameTime + 200L;
        }

        if (gameTime < nextAcquireTick) {
            if (standPos != null) {
                BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            }
            return;
        }
        StationType desiredStationType = activeRecipe != null ? activeRecipe.stationType() : null;
        if (stationAnchor == null || !townstead$isStation(level, stationAnchor)) {
            if (!townstead$acquireStation(level, villager, desiredStationType, activeRecipe)) {
                if (activeRecipe != null) {
                    townstead$clearActiveWorkState(level, villager);
                    desiredStationType = null;
                    if (!townstead$acquireStation(level, villager, null, null)) {
                        townstead$setBlocked(level, villager, gameTime, BlockedReason.NO_KITCHEN, "");
                        nextAcquireTick = gameTime + IDLE_BACKOFF;
                        return;
                    }
                } else {
                    townstead$setBlocked(level, villager, gameTime, BlockedReason.NO_KITCHEN, "");
                    nextAcquireTick = gameTime + IDLE_BACKOFF;
                    return;
                }
            }
        }

        if (villager.blockPosition().equals(stationAnchor) && standPos != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            // Do not hard-return here; cooks can otherwise get stuck forever standing on the station.
        }
        if (standPos == null) {
            if (!townstead$acquireStation(level, villager, desiredStationType, activeRecipe)) {
                if (activeRecipe != null) {
                    townstead$clearActiveWorkState(level, villager);
                    desiredStationType = null;
                    if (!townstead$acquireStation(level, villager, null, null)) {
                        townstead$setBlocked(level, villager, gameTime, BlockedReason.UNREACHABLE, "");
                        nextAcquireTick = gameTime + IDLE_BACKOFF;
                        return;
                    }
                } else {
                    townstead$setBlocked(level, villager, gameTime, BlockedReason.UNREACHABLE, "");
                    nextAcquireTick = gameTime + IDLE_BACKOFF;
                    return;
                }
            }
        }

        BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
        double distSq = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5);
        double anchorDistSq = stationAnchor == null
                ? Double.MAX_VALUE
                : villager.distanceToSqr(stationAnchor.getX() + 0.5, stationAnchor.getY() + 0.5, stationAnchor.getZ() + 0.5);
        if (distSq > ARRIVAL_DISTANCE_SQ && anchorDistSq > NEAR_STATION_DISTANCE_SQ) {
            if (gameTime >= nextStandReacquireTick) {
                nextStandReacquireTick = gameTime + STAND_REACQUIRE_INTERVAL_TICKS;
                if (stationAnchor != null) {
                    BlockPos refreshed = townstead$findStand(level, villager, stationAnchor);
                    if (refreshed != null) {
                        standPos = refreshed;
                    }
                }
                StationType desired = activeRecipe != null ? activeRecipe.stationType() : stationType;
                townstead$acquireStation(level, villager, desired, activeRecipe);
            }
            return;
        }
        nextStandReacquireTick = 0L;

        townstead$collectSurfaceCookDrops(level, villager, stationAnchor);
        townstead$unloadReadyOutputsFromStation(level, villager, stationAnchor);

        if (!pendingOutput.isEmpty()) {
            workPhase = WorkPhase.STORE;
            ItemStack pendingSnapshot = pendingOutput.copy();
            lastOutputDest = "none";
            ItemStack moving = townstead$takeFromInventoryForPending(villager.getInventory(), pendingOutput);
            if (moving.isEmpty()) {
                // Pending output must represent real inventory-held items.
                townstead$traceOutput("store:missing_inv item=" + townstead$itemId(pendingSnapshot.getItem()) + " pending=" + pendingSnapshot.getCount());
                pendingOutput = ItemStack.EMPTY;
                nextAcquireTick = gameTime + 20L;
                return;
            }
            int beforeNearbyInsert = moving.getCount();
            int insertedNearby = townstead$storeOutputInCookStorage(level, villager, moving, stationAnchor);
            if (insertedNearby <= 0) {
                ItemStack putBack = villager.getInventory().addItem(moving);
                if (!putBack.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                            level, villager.getX(), villager.getY() + 0.25, villager.getZ(), putBack.copy()
                    );
                    drop.setPickUpDelay(0);
                    level.addFreshEntity(drop);
                }
                pendingOutput = pendingSnapshot;
                townstead$traceOutput("store:no_target item=" + townstead$itemId(pendingSnapshot.getItem())
                        + " pending=" + pendingSnapshot.getCount());
                townstead$setBlocked(level, villager, gameTime, BlockedReason.NO_STORAGE, "");
                nextAcquireTick = gameTime + IDLE_BACKOFF;
                return;
            }
            if (!moving.isEmpty()) {
                ItemStack remainder = villager.getInventory().addItem(moving);
                if (!remainder.isEmpty()) {
                    townstead$traceOutput("store:blocked item=" + townstead$itemId(pendingSnapshot.getItem())
                            + " pending=" + pendingSnapshot.getCount()
                            + " pulled=" + beforeNearbyInsert
                            + " nearby=" + insertedNearby
                            + " dst=" + lastOutputDest
                            + " rem=" + remainder.getCount());
                    pendingOutput = remainder;
                    townstead$setBlocked(level, villager, gameTime, BlockedReason.NO_STORAGE, "");
                    nextAcquireTick = gameTime + IDLE_BACKOFF;
                    return;
                }
                townstead$traceOutput("store:to_inv item=" + townstead$itemId(pendingSnapshot.getItem())
                        + " pending=" + pendingSnapshot.getCount()
                        + " pulled=" + beforeNearbyInsert
                        + " nearby=" + insertedNearby
                        + " dst=" + lastOutputDest
                        + " rem=0");
            } else {
                townstead$traceOutput("store:done item=" + townstead$itemId(pendingSnapshot.getItem())
                        + " pending=" + pendingSnapshot.getCount()
                        + " pulled=" + beforeNearbyInsert
                        + " nearby=" + insertedNearby
                        + " dst=" + lastOutputDest
                        + " rem=0");
            }
            pendingOutput = ItemStack.EMPTY;
        }

        if (activeRecipe == null) {
            if ((shiftPlan.isEmpty() || shiftPlanIndex >= shiftPlan.size()) && !townstead$buildShiftPlan(level, villager)) {
                townstead$setBlocked(level, villager, gameTime, BlockedReason.NO_INGREDIENTS, "");
                nextAcquireTick = gameTime + IDLE_BACKOFF;
                return;
            }
            activeRecipe = townstead$selectNextPlannedRecipe(level, villager);
            if (activeRecipe == null && townstead$buildShiftPlan(level, villager)) {
                activeRecipe = townstead$selectNextPlannedRecipe(level, villager);
            }
            if (activeRecipe == null) {
                BlockedReason reason = townstead$blockedReasonFromFailure();
                if (reason == BlockedReason.NO_INGREDIENTS && !unsupportedItem.isBlank()) {
                    reason = BlockedReason.NO_RECIPE;
                }
                townstead$setBlocked(level, villager, gameTime, reason, unsupportedItem);
                nextAcquireTick = gameTime + IDLE_BACKOFF;
                return;
            }
        }

        if (cookDoneTick == 0L) {
            if (townstead$isStationBusyForCook(level, villager, stationAnchor, activeRecipe)) {
                if (activeRecipe != null && activeRecipe.stationType() == StationType.FIRE_STATION) {
                    BlockPos previousAnchor = stationAnchor;
                    BlockPos previousStand = standPos;
                    StationType previousType = stationType;
                    if (townstead$acquireStation(level, villager, StationType.FIRE_STATION, activeRecipe)
                            && stationAnchor != null
                            && !stationAnchor.equals(previousAnchor)
                            && !townstead$isStationBusyForCook(level, villager, stationAnchor, activeRecipe)) {
                        workPhase = WorkPhase.GATHER;
                        nextAcquireTick = gameTime + 5L;
                        return;
                    }
                    stationAnchor = previousAnchor;
                    standPos = previousStand;
                    stationType = previousType;
                }
                if (townstead$clearStationResidualInputs(level, villager, stationAnchor)) {
                    workPhase = WorkPhase.STORE;
                    nextAcquireTick = gameTime + 10L;
                    return;
                }
                workPhase = WorkPhase.IDLE;
                nextAcquireTick = gameTime + 20L;
                return;
            }
            workPhase = WorkPhase.GATHER;
            if (!townstead$pullAndConsume(level, villager, activeRecipe)) {
                if (lastFailure.startsWith("surface_full")) {
                    // Fire surface currently occupied: do not hard-block, retry plan selection soon.
                    townstead$setBlocked(level, villager, gameTime, BlockedReason.NONE, "");
                    activeRecipe = null;
                    nextAcquireTick = gameTime + 20L;
                    return;
                }
                townstead$setBlocked(level, villager, gameTime, townstead$blockedReasonFromFailure(), "");
                activeRecipe = null;
                nextAcquireTick = gameTime + IDLE_BACKOFF;
                return;
            }
            if (activeRecipe.stationType() == StationType.FIRE_STATION && townstead$isSurfaceFireStation(level, stationAnchor)) {
                lastWorkedStationAnchor = stationAnchor == null ? null : stationAnchor.immutable();
                workPhase = WorkPhase.COOK;
                townstead$setBlocked(level, villager, gameTime, BlockedReason.NONE, "");
                activeRecipe = null;
                nextAcquireTick = gameTime + 20L;
                return;
            }
            cookDoneTick = gameTime + activeRecipe.timeTicks();
            lastWorkedStationAnchor = stationAnchor == null ? null : stationAnchor.immutable();
            townstead$claimStation(villager, stationAnchor, cookDoneTick + STATION_CLAIM_BUFFER_TICKS);
            workPhase = WorkPhase.COOK;
            townstead$playSound(level);
            townstead$setBlocked(level, villager, gameTime, BlockedReason.NONE, "");
            return;
        }

        if (gameTime < cookDoneTick) {
            if (gameTime % 20L == 0L) townstead$playSound(level);
            return;
        }

        if (activeRecipe != null && activeRecipe.stationType() == StationType.HOT_STATION) {
            ItemStack resolved = townstead$resolveHotStationOutput(level, stationAnchor, activeRecipe);
            if (!resolved.isEmpty()) {
                ItemStack remainder = villager.getInventory().addItem(resolved.copy());
                int movedToInventory = resolved.getCount() - remainder.getCount();
                if (movedToInventory > 0) {
                    ItemStack queued = resolved.copyWithCount(movedToInventory);
                    if (pendingOutput.isEmpty()) pendingOutput = queued;
                    else if (ItemStack.isSameItemSameComponents(pendingOutput, queued)) pendingOutput.grow(queued.getCount());
                    else {
                        ItemStack spill = villager.getInventory().addItem(queued);
                        if (!spill.isEmpty()) {
                            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                                    level, villager.getX(), villager.getY() + 0.25, villager.getZ(), spill.copy()
                            );
                            drop.setPickUpDelay(0);
                            level.addFreshEntity(drop);
                        }
                    }
                }
                if (!remainder.isEmpty()) {
                    int insertedNearby = townstead$storeOutputInCookStorage(level, villager, remainder, stationAnchor);
                    if (insertedNearby <= 0) {
                        NearbyItemSources.insertIntoNearbyStorage(level, villager, remainder, 16, 3, stationAnchor);
                    }
                    if (!remainder.isEmpty()) {
                        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                                level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy()
                        );
                        drop.setPickUpDelay(0);
                        level.addFreshEntity(drop);
                    }
                }
            }
            townstead$traceOutput("hot:complete recipe=" + activeRecipe.output()
                    + " resolved=" + resolved.getCount()
                    + " pending=" + pendingOutput.getCount());
            townstead$noteCompletedRecipe(activeRecipe);
            stagedInputs.clear();
            activeRecipe = null;
            cookDoneTick = 0L;
            townstead$releaseStationClaim(villager, stationAnchor);
            workPhase = WorkPhase.STORE;
            return;
        }

        if (!stagedInputs.isEmpty()) {
            townstead$consumeStagedInputs(level, activeRecipe);
            stagedInputs.clear();
        }
        pendingOutput = townstead$outputStack(activeRecipe);
        townstead$noteCompletedRecipe(activeRecipe);
        activeRecipe = null;
        cookDoneTick = 0L;
        townstead$releaseStationClaim(villager, stationAnchor);
        workPhase = WorkPhase.STORE;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BlockPos claimed = stationAnchor;
        if (!stagedInputs.isEmpty() && stationAnchor != null) {
            townstead$rollbackStagedInputs(level, villager);
        }
        activeRecipe = null;
        cookDoneTick = 0L;
        pendingOutput = ItemStack.EMPTY;
        stationAnchor = null;
        standPos = null;
        stationType = null;
        blocked = BlockedReason.NONE;
        unsupportedItem = "";
        workPhase = WorkPhase.IDLE;
        lastFailure = "none";
        lastWorkedStationAnchor = null;
        townstead$releaseStationClaim(villager, claimed);
    }

    private void townstead$clearActiveWorkState(ServerLevel level, VillagerEntityMCA villager) {
        if (!stagedInputs.isEmpty() && stationAnchor != null) {
            townstead$rollbackStagedInputs(level, villager);
        } else {
            stagedInputs.clear();
        }
        activeRecipe = null;
        cookDoneTick = 0L;
        workPhase = WorkPhase.IDLE;
        lastFailure = "none";
        townstead$releaseStationClaim(villager, stationAnchor);
        nextStandReacquireTick = 0L;
    }

    private void townstead$unloadReadyOutputsFromStation(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (pos == null) return;
        if (townstead$isClaimedByOther(level, villager, pos)) return;
        if (cookDoneTick > 0L && pos.equals(stationAnchor)) return;

        for (ResourceLocation itemId : STATION_UNLOAD_IDS) {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == Items.AIR) continue;

            int acceptedByInventory = townstead$inventoryAcceptance(villager.getInventory(), new ItemStack(item, 64));
            if (acceptedByInventory <= 0) continue;

            int extracted = townstead$extractFromStation(level, pos, item, acceptedByInventory);
            if (extracted <= 0) continue;

            ItemStack moved = new ItemStack(item, extracted);
            ItemStack remainder = villager.getInventory().addItem(moved.copy());
            int movedToInventory = extracted - remainder.getCount();
            if (!remainder.isEmpty()) {
                // Safety: put any unexpected overflow back into station, never delete it.
                ItemStack putBackRemainder = townstead$insertIntoStation(level, pos, remainder.copy());
                if (!putBackRemainder.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                            level, villager.getX(), villager.getY() + 0.25, villager.getZ(), putBackRemainder
                    );
                    drop.setPickUpDelay(0);
                    level.addFreshEntity(drop);
                }
            }

            if (movedToInventory > 0) {
                ItemStack queued = moved.copyWithCount(movedToInventory);
                if (pendingOutput.isEmpty()) pendingOutput = queued;
                else if (ItemStack.isSameItemSameComponents(pendingOutput, queued)) pendingOutput.grow(queued.getCount());
                else {
                    // If this differs from current pending, keep it in inventory for the next store pass.
                    nextAcquireTick = level.getGameTime() + 20L;
                }
                townstead$traceOutput("unload:station item=" + townstead$itemId(item)
                        + " ex=" + extracted
                        + " inv=" + movedToInventory
                        + " pending=" + pendingOutput.getCount());
            }
        }
    }

    private boolean townstead$acquireStation(ServerLevel level, VillagerEntityMCA villager) {
        return townstead$acquireStation(level, villager, null, null);
    }

    private boolean townstead$acquireStation(ServerLevel level, VillagerEntityMCA villager, StationType preferredType) {
        return townstead$acquireStation(level, villager, preferredType, null);
    }

    private boolean townstead$acquireStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType preferredType,
            RecipeDef preferredRecipe
    ) {
        Set<Long> kitchenBounds = townstead$activeKitchenBounds(villager, townstead$activeKitchenReference(villager));
        BlockPos bestWorkPos = null;
        double bestWorkDist = Double.MAX_VALUE;
        StationType bestWorkType = null;
        BlockPos bestWorkStand = null;

        BlockPos bestAnyPos = null;
        double bestAnyDist = Double.MAX_VALUE;
        StationType bestAnyType = null;
        BlockPos bestAnyStand = null;

        List<Building> kitchenBuildings = townstead$kitchenBuildings(villager);
        for (Building building : kitchenBuildings) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                if (!kitchenBounds.isEmpty() && !kitchenBounds.contains(pos.asLong())) continue;
                StationType type = townstead$stationType(level, pos);
                if (type == null) continue;
                if (preferredType != null && preferredType != type) continue;
                if (preferredRecipe != null && !townstead$stationSupportsRecipe(level, pos, preferredRecipe)) continue;
                if (preferredRecipe != null
                        && preferredRecipe.stationType() == StationType.FIRE_STATION
                        && type == StationType.FIRE_STATION
                        && townstead$isSurfaceFireStation(level, pos.immutable())
                        && !townstead$surfaceHasFreeSlot(level, pos.immutable())) {
                    continue;
                }
                if (townstead$isClaimedByOther(level, villager, pos)) continue;
                BlockPos stand = townstead$findStand(level, villager, pos.immutable());
                if (stand == null) continue;
                double dist = townstead$stationSelectionDistance(villager, pos.immutable());
                if (dist < bestAnyDist) {
                    bestAnyPos = pos.immutable();
                    bestAnyDist = dist;
                    bestAnyType = type;
                    bestAnyStand = stand;
                }
                if (preferredType == null) {
                    RecipeDef recipe = townstead$pickRecipeForStation(level, villager, type, pos.immutable());
                    if (recipe != null && townstead$isBetterStation(bestWorkType, bestWorkDist, type, dist)) {
                        bestWorkPos = pos.immutable();
                        bestWorkDist = dist;
                        bestWorkType = type;
                        bestWorkStand = stand;
                    }
                }
            }
        }

        List<BlockPos> anchors = townstead$kitchenAnchors(level, villager);
        if (anchors.isEmpty()) {
            anchors = List.of(villager.blockPosition());
        }

        for (BlockPos anchor : anchors) {
            for (BlockPos pos : BlockPos.betweenClosed(
                    anchor.offset(-SEARCH_RADIUS, -VERTICAL_RADIUS, -SEARCH_RADIUS),
                    anchor.offset(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS))) {
                if (!kitchenBounds.isEmpty() && !kitchenBounds.contains(pos.asLong())) continue;
                StationType type = townstead$stationType(level, pos);
                if (type == null) continue;
                if (preferredType != null && preferredType != type) continue;
                if (preferredRecipe != null && !townstead$stationSupportsRecipe(level, pos, preferredRecipe)) continue;
                if (preferredRecipe != null
                        && preferredRecipe.stationType() == StationType.FIRE_STATION
                        && type == StationType.FIRE_STATION
                        && townstead$isSurfaceFireStation(level, pos.immutable())
                        && !townstead$surfaceHasFreeSlot(level, pos.immutable())) {
                    continue;
                }
                if (townstead$isClaimedByOther(level, villager, pos)) continue;
                BlockPos stand = townstead$findStand(level, villager, pos.immutable());
                if (stand == null) continue;
                double dist = townstead$stationSelectionDistance(villager, pos.immutable());
                if (dist < bestAnyDist) {
                    bestAnyPos = pos.immutable();
                    bestAnyDist = dist;
                    bestAnyType = type;
                    bestAnyStand = stand;
                }
                if (preferredType == null) {
                    RecipeDef recipe = townstead$pickRecipeForStation(level, villager, type, pos.immutable());
                    if (recipe != null && townstead$isBetterStation(bestWorkType, bestWorkDist, type, dist)) {
                        bestWorkPos = pos.immutable();
                        bestWorkDist = dist;
                        bestWorkType = type;
                        bestWorkStand = stand;
                    }
                }
            }
        }

        if (preferredType != null && bestAnyPos != null && bestAnyStand != null) {
            stationAnchor = bestAnyPos;
            stationType = bestAnyType;
            standPos = bestAnyStand;
            nextStandReacquireTick = 0L;
            return true;
        }

        if (bestWorkPos != null && bestWorkStand != null) {
            stationAnchor = bestWorkPos;
            stationType = bestWorkType;
            standPos = bestWorkStand;
            nextStandReacquireTick = 0L;
            return true;
        }

        stationAnchor = bestAnyPos;
        stationType = bestAnyType;
        standPos = bestAnyStand;
        if (stationAnchor != null && standPos != null) {
            nextStandReacquireTick = 0L;
        }
        return stationAnchor != null && standPos != null;
    }

    private double townstead$stationSelectionDistance(VillagerEntityMCA villager, BlockPos pos) {
        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (lastWorkedStationAnchor != null && lastWorkedStationAnchor.equals(pos)) {
            dist += 9.0d; // Prefer a different valid station in the same kitchen to simulate movement.
        }
        return dist;
    }

    private BlockPos townstead$nearestKitchenAnchor(VillagerEntityMCA villager) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos anchor : townstead$kitchenAnchors((ServerLevel) villager.level(), villager)) {
            double dist = villager.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = anchor;
            }
        }
        return best;
    }

    private boolean townstead$isBetterStation(StationType currentType, double currentDist, StationType candidateType, double candidateDist) {
        if (currentType == null) return true;
        int currentPriority = townstead$stationPriority(currentType);
        int candidatePriority = townstead$stationPriority(candidateType);
        if (candidatePriority != currentPriority) return candidatePriority > currentPriority;
        return candidateDist < currentDist;
    }

    private int townstead$stationPriority(StationType type) {
        return switch (type) {
            case HOT_STATION -> 3;
            case CUTTING_BOARD -> 2;
            case FIRE_STATION -> 1;
        };
    }

    private List<Building> townstead$kitchenBuildings(VillagerEntityMCA villager) {
        Optional<Village> village = FarmersDelightCookAssignment.resolveVillage(villager);
        if (village.isEmpty()) return List.of();
        return village.get().getBuildings().values().stream()
                .filter(b -> FarmersDelightCookAssignment.isKitchenType(b.getType()))
                .toList();
    }

    private List<BlockPos> townstead$kitchenAnchors(ServerLevel level, VillagerEntityMCA villager) {
        return townstead$kitchenBuildings(villager).stream()
                .map(Building::getCenter)
                .filter(pos -> pos != null)
                .distinct()
                .toList();
    }

    private RecipeDef townstead$pickRecipeForStation(ServerLevel level, VillagerEntityMCA villager, StationType targetStationType) {
        return townstead$pickRecipeForStation(level, villager, targetStationType, stationAnchor);
    }

    private RecipeDef townstead$pickRecipeForStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType targetStationType,
            BlockPos searchCenter
    ) {
        if (targetStationType == null) return null;
        int tier = Math.max(1, FarmersDelightCookAssignment.effectiveKitchenTier(level, villager));
        unsupportedItem = "";
        for (RecipeDef recipe : RECIPES) {
            if (recipe.minTier() > tier || recipe.stationType() != targetStationType) continue;
            if (townstead$outputStack(recipe).isEmpty()) continue;
            if (searchCenter != null && !townstead$stationSupportsRecipe(level, searchCenter, recipe)) continue;
            if (townstead$canFulfill(level, villager, recipe, searchCenter)) return recipe;
        }
        unsupportedItem = townstead$unsupportedName(villager.getInventory());
        return null;
    }

    private boolean townstead$buildShiftPlan(ServerLevel level, VillagerEntityMCA villager) {
        shiftPlan.clear();
        shiftPlanIndex = 0;

        int tier = Math.max(1, FarmersDelightCookAssignment.effectiveKitchenTier(level, villager));
        boolean hasHotStation = townstead$hasStationType(level, villager, StationType.HOT_STATION);
        boolean hasFireStation = townstead$hasStationType(level, villager, StationType.FIRE_STATION);
        boolean hasCuttingStation = townstead$hasStationType(level, villager, StationType.CUTTING_BOARD);
        List<RecipeDef> tierRecipes = RECIPES.stream()
                .filter(r -> r.minTier() <= tier)
                .filter(r -> !townstead$outputStack(r).isEmpty())
                .sorted(java.util.Comparator.<RecipeDef>comparingInt(RecipeDef::minTier).reversed())
                .toList();
        if (tierRecipes.isEmpty()) return false;

        Set<ResourceLocation> trackedIds = new HashSet<>();
        for (RecipeDef recipe : tierRecipes) {
            trackedIds.add(recipe.output());
            if (recipe.bowlsRequired() > 0) trackedIds.add(MINECRAFT_BOWL);
            for (Ingredient ingredient : recipe.inputs()) {
                trackedIds.add(ingredient.itemId());
            }
        }

        Map<ResourceLocation, Integer> virtualSupply = townstead$buildSupplySnapshot(level, villager, trackedIds);
        Map<ResourceLocation, Integer> outputStock = new HashMap<>();
        Map<ResourceLocation, Integer> chainDemand = new HashMap<>();
        Set<ResourceLocation> plannedOutputs = new HashSet<>();
        for (RecipeDef recipe : tierRecipes) {
            outputStock.put(recipe.output(), virtualSupply.getOrDefault(recipe.output(), 0));
            for (Ingredient ingredient : recipe.inputs()) {
                chainDemand.merge(ingredient.itemId(), 1, Integer::sum);
            }
        }

        boolean knifeAvailable = townstead$knifeAvailableForPlanning(level, villager);
        boolean waterAvailable = townstead$waterAvailableForPlanning(level, villager, villager.blockPosition());
        Map<RecipeDef, Boolean> compatibleStationCache = new HashMap<>();

        int workBudget = Math.max(120, townstead$remainingWorkTicks(villager));
        int budgetUsed = 0;
        while (shiftPlan.size() < SHIFT_PLAN_MAX_RECIPES) {
            RecipeDef bestRecipe = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            int bestCost = 0;
            boolean hasNonRepeatedCandidate = false;
            for (RecipeDef recipe : tierRecipes) {
                if (plannedOutputs.contains(recipe.output())) continue;
                if (!compatibleStationCache.computeIfAbsent(recipe, r -> townstead$hasCompatibleStationForRecipe(level, villager, r))) continue;
                if (!townstead$canPlanWithVirtual(recipe, virtualSupply, knifeAvailable, waterAvailable)) continue;
                int recipeCost = recipe.timeTicks() + 40;
                if (!shiftPlan.isEmpty() && budgetUsed + recipeCost > workBudget) continue;
                if (lastCompletedOutput == null || !lastCompletedOutput.equals(recipe.output())) {
                    hasNonRepeatedCandidate = true;
                    break;
                }
            }

            for (RecipeDef recipe : tierRecipes) {
                if (plannedOutputs.contains(recipe.output())) continue;
                if (!compatibleStationCache.computeIfAbsent(recipe, r -> townstead$hasCompatibleStationForRecipe(level, villager, r))) continue;
                if (!townstead$canPlanWithVirtual(recipe, virtualSupply, knifeAvailable, waterAvailable)) continue;

                int recipeCost = recipe.timeTicks() + 40;
                if (!shiftPlan.isEmpty() && budgetUsed + recipeCost > workBudget) continue;
                if (hasNonRepeatedCandidate
                        && lastCompletedOutput != null
                        && consecutiveCompletedOutput >= 2
                        && lastCompletedOutput.equals(recipe.output())) {
                    continue;
                }
                double score = townstead$scoreRecipe(
                        recipe,
                        0,
                        outputStock.getOrDefault(recipe.output(), 0),
                        chainDemand.getOrDefault(recipe.output(), 0),
                        tier,
                        hasHotStation,
                        hasFireStation
                );
                if (score > bestScore) {
                    bestScore = score;
                    bestRecipe = recipe;
                    bestCost = recipeCost;
                }
            }

            if (bestRecipe == null) break;
            shiftPlan.add(bestRecipe);
            plannedOutputs.add(bestRecipe.output());
            townstead$applyVirtual(bestRecipe, virtualSupply);
            budgetUsed += bestCost;
            if (budgetUsed >= workBudget) break;
        }
        return !shiftPlan.isEmpty();
    }

    private RecipeDef townstead$selectNextPlannedRecipe(ServerLevel level, VillagerEntityMCA villager) {
        if (shiftPlan.isEmpty()) return null;
        int attempts = shiftPlan.size();
        while (attempts-- > 0) {
            if (shiftPlanIndex >= shiftPlan.size()) shiftPlanIndex = 0;
            RecipeDef candidate = shiftPlan.get(shiftPlanIndex++);
            if (candidate == null) continue;
            if (candidate.stationType() == StationType.FIRE_STATION
                    && !townstead$hasReadyFireStationForRecipe(level, villager, candidate)) {
                lastFailure = "surface_full";
                continue;
            }
            if (stationType != candidate.stationType() && !townstead$acquireStation(level, villager, candidate.stationType(), candidate)) {
                lastFailure = "no_station:" + candidate.stationType().name().toLowerCase();
                continue;
            }
            if (!townstead$stationSupportsRecipe(level, stationAnchor, candidate)) {
                if (!townstead$acquireStation(level, villager, candidate.stationType(), candidate)) {
                    lastFailure = "no_station:" + candidate.stationType().name().toLowerCase();
                    continue;
                }
                if (!townstead$stationSupportsRecipe(level, stationAnchor, candidate)) {
                    lastFailure = "station_rejects:" + candidate.output();
                    continue;
                }
            }
            if (stationType != candidate.stationType()) {
                lastFailure = "no_station:" + candidate.stationType().name().toLowerCase();
                continue;
            }
            BlockPos center = stationAnchor != null ? stationAnchor : villager.blockPosition();
            if (!townstead$canFulfill(level, villager, candidate, center)) continue;
            return candidate;
        }
        return null;
    }

    private double townstead$scoreRecipe(
            RecipeDef recipe,
            int repeatsAlreadyPlanned,
            int currentStock,
            int chainDemand,
            int tier,
            boolean hasHotStation,
            boolean hasFireStation
    ) {
        ItemStack output = townstead$outputStack(recipe);
        FoodProperties food = output.get(DataComponents.FOOD);
        double nutrition = food != null ? food.nutrition() : 0.0d;
        double saturation = food != null ? food.saturation() : 0.0d;

        double score = nutrition * 2.0d + saturation * 4.0d;
        score += recipe.outputCount() * 0.35d;
        score -= recipe.inputs().size() * 0.25d;
        if (recipe.needsWater()) score -= 0.50d;
        if (recipe.needsKnife()) score -= 0.25d;
        score -= repeatsAlreadyPlanned * 2.5d;
        score += Math.max(0, 16 - currentStock) * 0.30d;
        score += chainDemand * 0.85d;
        score += Math.max(0, recipe.minTier() - 1) * 2.25d;
        score += (double) recipe.minTier() / Math.max(1, tier) * 1.25d;
        if (tier >= 3 && hasHotStation) {
            if (recipe.stationType() == StationType.HOT_STATION) score += 2.0d;
            if (recipe.stationType() == StationType.CUTTING_BOARD) score += 0.75d;
            if (recipe.stationType() == StationType.FIRE_STATION) score -= 1.0d;
        }
        if (hasFireStation && recipe.stationType() == StationType.FIRE_STATION) {
            score += 1.5d;
        }
        return score;
    }

    private boolean townstead$hasStationType(ServerLevel level, VillagerEntityMCA villager, StationType wanted) {
        Set<Long> kitchenBounds = townstead$activeKitchenBounds(villager, townstead$activeKitchenReference(villager));
        for (Building building : townstead$kitchenBuildings(villager)) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                if (!kitchenBounds.isEmpty() && !kitchenBounds.contains(pos.asLong())) continue;
                if (townstead$stationType(level, pos) == wanted) return true;
            }
        }
        return false;
    }

    private boolean townstead$hasCompatibleStationForRecipe(ServerLevel level, VillagerEntityMCA villager, RecipeDef recipe) {
        if (recipe == null) return false;
        if (recipe.stationType() == StationType.FIRE_STATION) {
            return townstead$hasReadyFireStationForRecipe(level, villager, recipe);
        }
        Set<Long> kitchenBounds = townstead$activeKitchenBounds(villager, townstead$activeKitchenReference(villager));
        for (Building building : townstead$kitchenBuildings(villager)) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                if (!kitchenBounds.isEmpty() && !kitchenBounds.contains(pos.asLong())) continue;
                if (townstead$stationType(level, pos) != recipe.stationType()) continue;
                if (!townstead$stationSupportsRecipe(level, pos, recipe)) continue;
                if (townstead$findStand(level, villager, pos.immutable()) == null) continue;
                return true;
            }
        }

        for (BlockPos anchor : townstead$kitchenAnchors(level, villager)) {
            for (BlockPos pos : BlockPos.betweenClosed(
                    anchor.offset(-SEARCH_RADIUS, -VERTICAL_RADIUS, -SEARCH_RADIUS),
                    anchor.offset(SEARCH_RADIUS, VERTICAL_RADIUS, SEARCH_RADIUS))) {
                if (!kitchenBounds.isEmpty() && !kitchenBounds.contains(pos.asLong())) continue;
                if (townstead$stationType(level, pos) != recipe.stationType()) continue;
                if (!townstead$stationSupportsRecipe(level, pos, recipe)) continue;
                if (townstead$findStand(level, villager, pos.immutable()) == null) continue;
                return true;
            }
        }
        return false;
    }

    private boolean townstead$hasReadyFireStationForRecipe(ServerLevel level, VillagerEntityMCA villager, RecipeDef recipe) {
        if (recipe == null || recipe.stationType() != StationType.FIRE_STATION) return false;
        Set<Long> kitchenBounds = townstead$activeKitchenBounds(villager, townstead$activeKitchenReference(villager));
        if (kitchenBounds.isEmpty()) return false;
        for (long key : kitchenBounds) {
            BlockPos pos = BlockPos.of(key);
            if (townstead$stationType(level, pos) != StationType.FIRE_STATION) continue;
            if (!townstead$stationSupportsRecipe(level, pos, recipe)) continue;
            if (townstead$isSurfaceFireStation(level, pos) && !townstead$surfaceHasFreeSlot(level, pos)) continue;
            if (townstead$isClaimedByOther(level, villager, pos)) continue;
            if (townstead$findStand(level, villager, pos.immutable()) == null) continue;
            return true;
        }
        return false;
    }

    private boolean townstead$isStationBusyForCook(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, RecipeDef recipe) {
        if (pos == null) return false;
        if (townstead$isClaimedByOther(level, villager, pos)) return true;
        return townstead$stationHasContents(level, pos, recipe);
    }

    private boolean townstead$stationHasContents(ServerLevel level, BlockPos pos, RecipeDef recipe) {
        if (pos == null) return false;
        ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (recipe != null && recipe.stationType() == StationType.FIRE_STATION && townstead$isSurfaceFireStation(level, pos)) {
            return !townstead$surfaceHasFreeSlot(level, pos);
        }
        boolean allowPotBowlPrestage =
                FD_COOKING_POT.equals(stationId)
                        && recipe != null
                        && recipe.stationType() == StationType.HOT_STATION
                        && recipe.bowlsRequired() > 0;
        Set<Item> allowedHotPrestageItems = null;
        if (recipe != null && recipe.stationType() == StationType.HOT_STATION) {
            allowedHotPrestageItems = new HashSet<>();
            for (Ingredient ingredient : recipe.inputs()) {
                Item item = BuiltInRegistries.ITEM.get(ingredient.itemId());
                if (item != Items.AIR) {
                    allowedHotPrestageItems.add(item);
                }
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;

        IItemHandler handler = townstead$preferredIngredientHandler(level, pos);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (slot.isEmpty()) continue;
                if (allowPotBowlPrestage && i == FD_COOKING_POT_CONTAINER_SLOT && slot.is(Items.BOWL)) continue;
                if (allowedHotPrestageItems != null && allowedHotPrestageItems.contains(slot.getItem())) continue;
                return true;
            }
            return false;
        }

        handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (slot.isEmpty()) continue;
                if (allowPotBowlPrestage && i == FD_COOKING_POT_CONTAINER_SLOT && slot.is(Items.BOWL)) continue;
                if (allowedHotPrestageItems != null && allowedHotPrestageItems.contains(slot.getItem())) continue;
                return true;
            }
        }

        for (Direction dir : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (slot.isEmpty()) continue;
                if (allowPotBowlPrestage && i == FD_COOKING_POT_CONTAINER_SLOT && slot.is(Items.BOWL)) continue;
                if (allowedHotPrestageItems != null && allowedHotPrestageItems.contains(slot.getItem())) continue;
                return true;
            }
        }

        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (!container.getItem(i).isEmpty()) return true;
            }
        }
        return false;
    }

    private void townstead$collectSurfaceCookDrops(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (pos == null) return;
        AABB area = new AABB(pos).inflate(1.5, 1.0, 1.5);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && RECIPE_OUTPUT_IDS.contains(id);
        });
        if (drops.isEmpty()) return;

        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) continue;
            drop.discard();

            ItemStack moving = stack.copy();
            int inserted = townstead$storeOutputInCookStorage(level, villager, moving, pos);
            if (inserted > 0) {
                townstead$traceOutput("surface:drop_store item=" + townstead$itemId(stack.getItem())
                        + " moved=" + inserted
                        + " dst=" + lastOutputDest);
            }

            if (!moving.isEmpty()) {
                ItemStack remainder = villager.getInventory().addItem(moving);
                if (!remainder.isEmpty()) {
                    ItemEntity putBack = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), remainder.copy());
                    putBack.setPickUpDelay(0);
                    level.addFreshEntity(putBack);
                }
            }
        }
    }

    private boolean townstead$isSurfaceFireStation(ServerLevel level, BlockPos pos) {
        if (pos == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.CAMPFIRES)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return FD_STOVE.equals(id) || FD_SKILLET.equals(id);
    }

    private boolean townstead$surfaceHasFreeSlot(ServerLevel level, BlockPos pos) {
        if (pos == null) return false;
        if (townstead$fireSurfaceBlocked(level, pos)) return false;
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            for (ItemStack slot : campfire.getItems()) {
                if (slot.isEmpty()) return true;
            }
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (townstead$stoveBlockedAbove(be)) return false;
            Integer slot = townstead$stoveNextEmptySlot(be);
            return slot != null && slot >= 0;
        }
        if (FD_SKILLET.equals(id)) {
            return !townstead$skilletHasStoredStack(be);
        }
        return false;
    }

    private boolean townstead$surfaceCanCookRecipeInput(ServerLevel level, BlockPos pos, RecipeDef recipe) {
        if (recipe == null || recipe.inputs().isEmpty()) return false;
        if (townstead$fireSurfaceBlocked(level, pos)) return false;
        Ingredient input = recipe.inputs().get(0);
        Item inputItem = BuiltInRegistries.ITEM.get(input.itemId());
        if (inputItem == Items.AIR) return false;
        ItemStack probe = new ItemStack(inputItem, 1);

        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            Optional<RecipeHolder<CampfireCookingRecipe>> match = campfire.getCookableRecipe(probe);
            return match.isPresent();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (townstead$stoveBlockedAbove(be)) return false;
            return townstead$stoveMatchingRecipe(be, probe).isPresent();
        }
        if (FD_SKILLET.equals(id)) {
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                return false;
            }
            return townstead$skilletMatchingRecipe(be, probe).isPresent();
        }
        return false;
    }

    private boolean townstead$loadSurfaceFireStation(ServerLevel level, VillagerEntityMCA villager, RecipeDef recipe) {
        if (recipe == null || stationAnchor == null || recipe.inputs().isEmpty()) {
            lastFailure = "surface_recipe_missing";
            return false;
        }
        Ingredient input = recipe.inputs().get(0);
        Item inputItem = BuiltInRegistries.ITEM.get(input.itemId());
        if (inputItem == Items.AIR) {
            lastFailure = "surface_bad_ingredient";
            return false;
        }
        SimpleContainer inv = villager.getInventory();
        if (townstead$count(inv, inputItem) <= 0) {
            lastFailure = "missing:" + input.itemId();
            return false;
        }

        ItemStack one = new ItemStack(inputItem, 1);
        BlockState state = level.getBlockState(stationAnchor);
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (townstead$fireSurfaceBlocked(level, stationAnchor)) {
            lastFailure = "surface_blocked";
            return false;
        }
        boolean loaded = false;
        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            Optional<RecipeHolder<CampfireCookingRecipe>> match = campfire.getCookableRecipe(one);
            if (match.isEmpty()) {
                lastFailure = "surface_recipe_missing";
                return false;
            }
            int cookTime = match.get().value().getCookingTime();
            loaded = campfire.placeFood(villager, one.copy(), cookTime);
            if (!loaded) {
                lastFailure = "surface_full";
                return false;
            }
        } else {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (FD_STOVE.equals(id)) {
                if (townstead$stoveBlockedAbove(be)) {
                    lastFailure = "surface_blocked";
                    return false;
                }
                Optional<?> match = townstead$stoveMatchingRecipe(be, one);
                if (match.isEmpty()) {
                    lastFailure = "surface_recipe_missing";
                    return false;
                }
                Integer slot = townstead$stoveNextEmptySlot(be);
                if (slot == null || slot < 0) {
                    lastFailure = "surface_full";
                    return false;
                }
                Object holder = match.get();
                loaded = townstead$stoveAddItem(be, one.copy(), holder, slot);
                if (!loaded) {
                    lastFailure = "surface_full";
                    return false;
                }
            } else if (FD_SKILLET.equals(id)) {
                if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                        && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                    lastFailure = "surface_waterlogged";
                    return false;
                }
                if (townstead$skilletHasStoredStack(be)) {
                    lastFailure = "surface_full";
                    return false;
                }
                if (townstead$skilletMatchingRecipe(be, one).isEmpty()) {
                    lastFailure = "surface_recipe_missing";
                    return false;
                }
                loaded = townstead$skilletAddItem(be, one.copy());
            }
        }

        if (!loaded) {
            lastFailure = "surface_full";
            return false;
        }
        if (!townstead$consume(inv, inputItem, 1)) {
            lastFailure = "consume_failed:" + input.itemId();
            return false;
        }
        townstead$traceOutput("surface:loaded recipe=" + recipe.output()
                + " at=" + stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ());
        return true;
    }

    private boolean townstead$ensureStoveReflection() {
        if (FD_STOVE_BE_CLASS != null
                && FD_STOVE_GET_NEXT_EMPTY_SLOT != null
                && FD_STOVE_GET_MATCHING_RECIPE != null
                && FD_STOVE_ADD_ITEM != null
                && FD_STOVE_IS_BLOCKED_ABOVE != null) {
            return true;
        }
        try {
            Class<?> recipeHolderClass = Class.forName("net.minecraft.world.item.crafting.RecipeHolder");
            FD_STOVE_BE_CLASS = Class.forName("vectorwing.farmersdelight.common.block.entity.StoveBlockEntity");
            FD_STOVE_GET_NEXT_EMPTY_SLOT = FD_STOVE_BE_CLASS.getMethod("getNextEmptySlot");
            FD_STOVE_GET_MATCHING_RECIPE = FD_STOVE_BE_CLASS.getMethod("getMatchingRecipe", ItemStack.class);
            FD_STOVE_ADD_ITEM = FD_STOVE_BE_CLASS.getMethod("addItem", ItemStack.class, recipeHolderClass, int.class);
            FD_STOVE_IS_BLOCKED_ABOVE = FD_STOVE_BE_CLASS.getMethod("isStoveBlockedAbove");
            return true;
        } catch (Throwable ignored) {
            FD_STOVE_BE_CLASS = null;
            FD_STOVE_GET_NEXT_EMPTY_SLOT = null;
            FD_STOVE_GET_MATCHING_RECIPE = null;
            FD_STOVE_ADD_ITEM = null;
            FD_STOVE_IS_BLOCKED_ABOVE = null;
            return false;
        }
    }

    private Integer townstead$stoveNextEmptySlot(BlockEntity be) {
        if (be == null || !townstead$ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return null;
        try {
            Object value = FD_STOVE_GET_NEXT_EMPTY_SLOT.invoke(be);
            return value instanceof Integer i ? i : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Optional<?> townstead$stoveMatchingRecipe(BlockEntity be, ItemStack stack) {
        if (be == null || stack.isEmpty() || !townstead$ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) {
            return Optional.empty();
        }
        try {
            Object value = FD_STOVE_GET_MATCHING_RECIPE.invoke(be, stack.copy());
            if (value instanceof Optional<?> opt) return opt;
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private boolean townstead$stoveAddItem(BlockEntity be, ItemStack stack, Object recipeHolder, int cookTime) {
        if (be == null || stack.isEmpty() || recipeHolder == null) return false;
        if (!townstead$ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return false;
        try {
            Object value = FD_STOVE_ADD_ITEM.invoke(be, stack, recipeHolder, cookTime);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean townstead$stoveBlockedAbove(BlockEntity be) {
        if (be == null || !townstead$ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return false;
        try {
            Object value = FD_STOVE_IS_BLOCKED_ABOVE.invoke(be);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean townstead$ensureSkilletReflection() {
        if (FD_SKILLET_BE_CLASS != null
                && FD_SKILLET_HAS_STORED_STACK != null
                && FD_SKILLET_GET_MATCHING_RECIPE != null
                && FD_SKILLET_ADD_ITEM_TO_COOK != null) {
            return true;
        }
        try {
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            FD_SKILLET_BE_CLASS = Class.forName("vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity");
            FD_SKILLET_HAS_STORED_STACK = FD_SKILLET_BE_CLASS.getMethod("hasStoredStack");
            FD_SKILLET_GET_MATCHING_RECIPE = FD_SKILLET_BE_CLASS.getDeclaredMethod("getMatchingRecipe", ItemStack.class);
            FD_SKILLET_GET_MATCHING_RECIPE.setAccessible(true);
            FD_SKILLET_ADD_ITEM_TO_COOK = FD_SKILLET_BE_CLASS.getMethod("addItemToCook", ItemStack.class, playerClass);
            return true;
        } catch (Throwable ignored) {
            FD_SKILLET_BE_CLASS = null;
            FD_SKILLET_HAS_STORED_STACK = null;
            FD_SKILLET_GET_MATCHING_RECIPE = null;
            FD_SKILLET_ADD_ITEM_TO_COOK = null;
            return false;
        }
    }

    private boolean townstead$skilletHasStoredStack(BlockEntity be) {
        if (be == null || !townstead$ensureSkilletReflection() || !FD_SKILLET_BE_CLASS.isInstance(be)) return false;
        try {
            Object value = FD_SKILLET_HAS_STORED_STACK.invoke(be);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Optional<?> townstead$skilletMatchingRecipe(BlockEntity be, ItemStack stack) {
        if (be == null || stack.isEmpty() || !townstead$ensureSkilletReflection() || !FD_SKILLET_BE_CLASS.isInstance(be)) {
            return Optional.empty();
        }
        try {
            Object value = FD_SKILLET_GET_MATCHING_RECIPE.invoke(be, stack.copy());
            if (value instanceof Optional<?> opt) return opt;
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private boolean townstead$skilletAddItem(BlockEntity be, ItemStack stack) {
        if (be == null || stack.isEmpty()) return false;
        if (!townstead$ensureSkilletReflection() || !FD_SKILLET_BE_CLASS.isInstance(be)) return false;
        try {
            Object value = FD_SKILLET_ADD_ITEM_TO_COOK.invoke(be, stack.copy(), null);
            if (value instanceof ItemStack remainder) {
                return remainder.isEmpty();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean townstead$clearStationResidualInputs(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (pos == null) return false;
        if (townstead$isClaimedByOther(level, villager, pos)) return false;

        boolean movedAny = false;
        for (ResourceLocation itemId : RECIPE_INPUT_IDS) {
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == Items.AIR) continue;

            int extracted = townstead$extractFromStation(level, pos, item, 64);
            if (extracted <= 0) continue;
            movedAny = true;

            ItemStack moved = new ItemStack(item, extracted);
            ItemStack remainder = villager.getInventory().addItem(moved);
            if (!remainder.isEmpty()) {
                int insertedNearby = townstead$storeOutputInCookStorage(level, villager, remainder, pos);
                if (insertedNearby <= 0 && !remainder.isEmpty()) {
                    // Never delete leftovers if both inventory and storage are full.
                    ItemStack putBack = townstead$insertIntoStation(level, pos, remainder);
                    if (!putBack.isEmpty()) {
                        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                                level, villager.getX(), villager.getY() + 0.25, villager.getZ(), putBack.copy()
                        );
                        drop.setPickUpDelay(0);
                        level.addFreshEntity(drop);
                    }
                }
            }
        }

        if (movedAny) {
            townstead$traceOutput("cleanup:station_inputs at=" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
        }
        return movedAny;
    }

    private void townstead$claimStation(VillagerEntityMCA villager, BlockPos pos, long untilTick) {
        if (pos == null) return;
        long key = pos.asLong();
        STATION_CLAIM_OWNER.put(key, villager.getUUID());
        STATION_CLAIM_UNTIL.put(key, untilTick);
    }

    private void townstead$releaseStationClaim(VillagerEntityMCA villager, BlockPos pos) {
        if (villager == null || pos == null) return;
        long key = pos.asLong();
        UUID owner = STATION_CLAIM_OWNER.get(key);
        if (owner == null || !owner.equals(villager.getUUID())) return;
        STATION_CLAIM_OWNER.remove(key);
        STATION_CLAIM_UNTIL.remove(key);
    }

    private boolean townstead$isClaimedByOther(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (villager == null || pos == null) return false;
        long key = pos.asLong();
        Long until = STATION_CLAIM_UNTIL.get(key);
        if (until == null) return false;
        if (until <= level.getGameTime()) {
            STATION_CLAIM_OWNER.remove(key);
            STATION_CLAIM_UNTIL.remove(key);
            return false;
        }
        UUID owner = STATION_CLAIM_OWNER.get(key);
        return owner != null && !owner.equals(villager.getUUID());
    }

    private boolean townstead$canPlanWithVirtual(
            RecipeDef recipe,
            Map<ResourceLocation, Integer> virtualSupply,
            boolean knifeAvailable,
            boolean waterAvailable
    ) {
        if (recipe.needsKnife() && !knifeAvailable) return false;
        if (recipe.needsWater() && !waterAvailable) return false;
        if (recipe.bowlsRequired() > 0) {
            int bowls = virtualSupply.getOrDefault(MINECRAFT_BOWL, 0);
            if (bowls < recipe.bowlsRequired()) return false;
        }
        for (Ingredient ingredient : recipe.inputs()) {
            int available = virtualSupply.getOrDefault(ingredient.itemId(), 0);
            if (available < ingredient.count()) return false;
        }
        return true;
    }

    private void townstead$applyVirtual(RecipeDef recipe, Map<ResourceLocation, Integer> virtualSupply) {
        if (recipe.bowlsRequired() > 0) {
            int bowls = virtualSupply.getOrDefault(MINECRAFT_BOWL, 0);
            virtualSupply.put(MINECRAFT_BOWL, Math.max(0, bowls - recipe.bowlsRequired()));
        }
        for (Ingredient ingredient : recipe.inputs()) {
            int available = virtualSupply.getOrDefault(ingredient.itemId(), 0);
            virtualSupply.put(ingredient.itemId(), Math.max(0, available - ingredient.count()));
        }
        virtualSupply.merge(recipe.output(), recipe.outputCount(), Integer::sum);
    }

    private boolean townstead$canFulfill(ServerLevel level, VillagerEntityMCA villager, RecipeDef recipe, BlockPos searchCenter) {
        lastFailure = "none";
        BlockPos center = searchCenter != null ? searchCenter : villager.blockPosition();
        if (recipe.stationType() == StationType.FIRE_STATION && searchCenter != null) {
            if (townstead$isSurfaceFireStation(level, center) && !townstead$surfaceHasFreeSlot(level, center)) {
                lastFailure = "surface_full";
                return false;
            }
        }
        if (searchCenter != null && !townstead$stationSupportsRecipe(level, center, recipe)) {
            lastFailure = "station_rejects:" + recipe.output();
            return false;
        }
        if (recipe.needsKnife() && !townstead$knifeAvailableForPlanning(level, villager)) {
            lastFailure = "missing_knife";
            return false;
        }
        if (recipe.needsWater() && !townstead$waterAvailableForPlanning(level, villager, center)) {
            lastFailure = "missing_water";
            return false;
        }
        if (recipe.bowlsRequired() > 0) {
            int bowlsAlreadyStaged = townstead$cookingPotContainerBowlCount(level, center);
            int bowlsNeeded = Math.max(0, recipe.bowlsRequired() - bowlsAlreadyStaged);
            int bowls = townstead$count(villager.getInventory(), Items.BOWL);
            NearbyItemSources.ContainerSlot nearbySlot = NearbyItemSources.findBestNearbySlot(
                    level, villager, 16, 3, s -> s.is(Items.BOWL), ItemStack::getCount, center
            );
            if (nearbySlot != null) bowls += Math.max(1, nearbySlot.score());
            if (bowls < bowlsNeeded) {
                NearbyItemSources.ContainerSlot villageSlot = townstead$findVillageStorageSlot(level, villager, Items.BOWL);
                if (villageSlot != null) bowls += Math.max(1, villageSlot.score());
            }
            if (bowls < bowlsNeeded) {
                lastFailure = "missing:minecraft:bowl";
                return false;
            }
        }

        SimpleContainer inv = villager.getInventory();
        for (Ingredient ingredient : recipe.inputs()) {
            Item item = BuiltInRegistries.ITEM.get(ingredient.itemId());
            if (item == Items.AIR) {
                lastFailure = "bad_ingredient:" + ingredient.itemId();
                return false;
            }
            int neededCount = ingredient.count();
            if (recipe.stationType() == StationType.HOT_STATION) {
                int preloaded = townstead$countItemInStation(level, center, item);
                neededCount = Math.max(0, neededCount - preloaded);
            }
            if (neededCount <= 0) continue;
            if (townstead$count(inv, item) >= neededCount) continue;
            NearbyItemSources.ContainerSlot nearbySlot = NearbyItemSources.findBestNearbySlot(
                    level, villager, 16, 3, s -> s.is(item), ItemStack::getCount, center
            );
            if (nearbySlot == null && townstead$findVillageStorageSlot(level, villager, item) == null) {
                lastFailure = "missing:" + ingredient.itemId();
                return false;
            }
        }
        return true;
    }

    private Map<ResourceLocation, Integer> townstead$buildSupplySnapshot(
            ServerLevel level,
            VillagerEntityMCA villager,
            Set<ResourceLocation> trackedIds
    ) {
        Map<ResourceLocation, Integer> supply = new HashMap<>();
        if (trackedIds.isEmpty()) return supply;

        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null || !trackedIds.contains(itemId)) continue;
            supply.merge(itemId, stack.getCount(), Integer::sum);
        }

        Optional<Village> villageOpt = FarmersDelightCookAssignment.resolveVillage(villager);
        if (villageOpt.isEmpty()) return supply;

        Village village = villageOpt.get();
        Set<Long> kitchenBounds = townstead$activeKitchenBounds(villager, townstead$activeKitchenReference(villager));
        Set<Long> visited = new HashSet<>();
        for (Building building : village.getBuildings().values()) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                long key = pos.asLong();
                if (!visited.add(key)) continue;
                if (!kitchenBounds.isEmpty() && !kitchenBounds.contains(key)) continue;
                if (!village.isWithinBorder(pos, 0)) continue;
                if (TownsteadConfig.isProtectedStorage(level.getBlockState(pos))) continue;

                BlockEntity be = level.getBlockEntity(pos);
                if (NearbyItemSources.isProcessingContainer(level, pos, be)) continue;
                if (be == null) continue;

                if (be instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        ItemStack stack = container.getItem(i);
                        if (stack.isEmpty()) continue;
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        if (itemId == null || !trackedIds.contains(itemId)) continue;
                        supply.merge(itemId, stack.getCount(), Integer::sum);
                    }
                    continue;
                }

                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler == null) continue;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (itemId == null || !trackedIds.contains(itemId)) continue;
                    supply.merge(itemId, stack.getCount(), Integer::sum);
                }
            }
        }
        return supply;
    }

    private int townstead$remainingWorkTicks(VillagerEntityMCA villager) {
        int dayTime = (int) (villager.level().getDayTime() % 24000L);
        int remaining = 0;
        for (int i = 0; i < 24000; i++) {
            Activity activity = villager.getBrain().getSchedule().getActivityAt((dayTime + i) % 24000);
            if (activity != Activity.WORK) break;
            remaining++;
        }
        return remaining;
    }

    private boolean townstead$knifeAvailableForPlanning(ServerLevel level, VillagerEntityMCA villager) {
        if (townstead$hasKnife(villager.getInventory())) return true;
        return townstead$findVillageStorageSlot(level, villager, townstead$isKnifeStackPredicate()) != null;
    }

    private boolean townstead$waterAvailableForPlanning(ServerLevel level, VillagerEntityMCA villager, BlockPos center) {
        if (townstead$hasItem(villager.getInventory(), Items.WATER_BUCKET)) return true;

        BlockPos searchCenter = center;
        if (searchCenter == null) searchCenter = stationAnchor;
        if (searchCenter == null) searchCenter = townstead$nearestKitchenAnchor(villager);
        if (searchCenter == null) searchCenter = villager.blockPosition();

        if (townstead$findVillageStorageSlot(level, villager, s -> s.is(Items.WATER_BUCKET)) != null) {
            return true;
        }

        boolean hasBucket =
                townstead$hasItem(villager.getInventory(), Items.BUCKET)
                        || townstead$findVillageStorageSlot(level, villager, s -> s.is(Items.BUCKET)) != null;
        return hasBucket && townstead$hasNearbyWater(level, searchCenter, 24, 4);
    }

    private boolean townstead$pullAndConsume(ServerLevel level, VillagerEntityMCA villager, RecipeDef recipe) {
        lastFailure = "none";
        BlockPos center = stationAnchor != null ? stationAnchor : villager.blockPosition();
        if (recipe.needsKnife() && !townstead$hasKnife(villager.getInventory())) {
            if (!townstead$pullSingleTool(level, villager, townstead$isKnifeStackPredicate(), center)) {
                lastFailure = "missing_knife";
                return false;
            }
        }
        if (recipe.needsWater() && !townstead$ensureWaterAvailable(level, villager, center)) {
            lastFailure = "missing_water";
            return false;
        }
        int bowlsNeededForStage = 0;
        if (recipe.bowlsRequired() > 0) {
            int bowlsAlreadyStaged = townstead$cookingPotContainerBowlCount(level, stationAnchor);
            bowlsNeededForStage = Math.max(0, recipe.bowlsRequired() - bowlsAlreadyStaged);
            while (townstead$count(villager.getInventory(), Items.BOWL) < bowlsNeededForStage) {
                if (!townstead$pullSingleIngredient(level, villager, Items.BOWL)) {
                    lastFailure = "missing:minecraft:bowl";
                    return false;
                }
            }
        }

        SimpleContainer inv = villager.getInventory();
        for (Ingredient ingredient : recipe.inputs()) {
            Item item = BuiltInRegistries.ITEM.get(ingredient.itemId());
            while (townstead$count(inv, item) < ingredient.count()) {
                boolean ok = townstead$pullSingleIngredient(level, villager, item);
                if (!ok) {
                    lastFailure = "missing:" + ingredient.itemId();
                    return false;
                }
            }
        }

        if (stationType == StationType.FIRE_STATION && townstead$isSurfaceFireStation(level, stationAnchor)) {
            if (!townstead$loadSurfaceFireStation(level, villager, recipe)) {
                return false;
            }
            return true;
        }

        stagedInputs.clear();
        Map<ResourceLocation, Integer> stationProvidedInputs = new HashMap<>();
        if (stationType == StationType.HOT_STATION || stationType == StationType.CUTTING_BOARD) {
            for (Ingredient ingredient : recipe.inputs()) {
                Item item = BuiltInRegistries.ITEM.get(ingredient.itemId());
                int neededToStage = ingredient.count();
                if (stationType == StationType.HOT_STATION && stationAnchor != null) {
                    int alreadyInStation = townstead$countItemInStation(level, stationAnchor, item);
                    int fromStation = Math.min(ingredient.count(), alreadyInStation);
                    if (fromStation > 0) {
                        stationProvidedInputs.put(ingredient.itemId(), fromStation);
                        neededToStage = Math.max(0, ingredient.count() - fromStation);
                    }
                }

                int stagedCount = 0;
                if (neededToStage > 0) {
                    stagedCount = townstead$stageFromInventoryToStation(level, villager, item, neededToStage);
                    if (stagedCount <= 0) {
                        lastFailure = "station_rejects:" + ingredient.itemId();
                        return false;
                    }
                    stagedInputs.merge(ingredient.itemId(), stagedCount, Integer::sum);
                    if (stagedCount < neededToStage) {
                        townstead$rollbackStagedInputs(level, villager);
                        lastFailure = "station_partial:" + ingredient.itemId();
                        return false;
                    }
                }
            }
            if (recipe.bowlsRequired() > 0 && bowlsNeededForStage > 0) {
                int stagedBowls = townstead$stageBowlsFromInventoryToStation(level, villager, bowlsNeededForStage);
                if (stagedBowls <= 0) {
                    townstead$rollbackStagedInputs(level, villager);
                    lastFailure = "station_rejects:minecraft:bowl";
                    return false;
                }
                stagedInputs.merge(MINECRAFT_BOWL, stagedBowls, Integer::sum);
                if (stagedBowls < bowlsNeededForStage) {
                    townstead$rollbackStagedInputs(level, villager);
                    lastFailure = "station_partial:minecraft:bowl";
                    return false;
                }
            }
        }

        for (Ingredient ingredient : recipe.inputs()) {
            int stagedCount = stagedInputs.getOrDefault(ingredient.itemId(), 0);
            int fromStation = stationProvidedInputs.getOrDefault(ingredient.itemId(), 0);
            int remaining = Math.max(0, ingredient.count() - stagedCount - fromStation);
            if (remaining > 0 && !townstead$consume(inv, BuiltInRegistries.ITEM.get(ingredient.itemId()), remaining)) {
                lastFailure = "consume_failed:" + ingredient.itemId();
                return false;
            }
        }
        return true;
    }

    private void townstead$rollbackStagedInputs(ServerLevel level, VillagerEntityMCA villager) {
        if (stationAnchor == null || stagedInputs.isEmpty()) {
            stagedInputs.clear();
            return;
        }

        for (Map.Entry<ResourceLocation, Integer> entry : stagedInputs.entrySet()) {
            Item item = BuiltInRegistries.ITEM.get(entry.getKey());
            if (item == Items.AIR) continue;
            int removed = townstead$extractFromStation(level, stationAnchor, item, entry.getValue());
            if (removed > 0) villager.getInventory().addItem(new ItemStack(item, removed));
        }
        stagedInputs.clear();
    }

    private boolean townstead$pullSingleIngredient(ServerLevel level, VillagerEntityMCA villager, Item item) {
        BlockPos center = stationAnchor != null ? stationAnchor : villager.blockPosition();
        if (NearbyItemSources.pullSingleToInventory(level, villager, 16, 3, s -> s.is(item), ItemStack::getCount, center)) {
            return true;
        }
        NearbyItemSources.ContainerSlot villageSlot = townstead$findVillageStorageSlot(level, villager, s -> s.is(item));
        if (villageSlot == null) return false;
        ItemStack extracted = NearbyItemSources.extractOne(level, villageSlot);
        if (extracted.isEmpty()) return false;
        return townstead$addToInventoryOrNearbyStorage(level, villager, extracted, center);
    }

    private boolean townstead$pullSingleTool(
            ServerLevel level,
            VillagerEntityMCA villager,
            java.util.function.Predicate<ItemStack> matcher,
            BlockPos center
    ) {
        BlockPos searchCenter = center != null ? center : villager.blockPosition();
        if (NearbyItemSources.pullSingleToInventory(level, villager, 16, 3, matcher, ItemStack::getCount, searchCenter)) {
            return true;
        }
        NearbyItemSources.ContainerSlot villageSlot = townstead$findVillageStorageSlot(level, villager, matcher);
        if (villageSlot == null) return false;
        ItemStack extracted = NearbyItemSources.extractOne(level, villageSlot);
        if (extracted.isEmpty()) return false;
        return townstead$addToInventoryOrNearbyStorage(level, villager, extracted, searchCenter);
    }

    private NearbyItemSources.ContainerSlot townstead$findVillageStorageSlot(
            ServerLevel level,
            VillagerEntityMCA villager,
            Item item
    ) {
        return townstead$findVillageStorageSlot(level, villager, s -> s.is(item));
    }

    private NearbyItemSources.ContainerSlot townstead$findVillageStorageSlot(
            ServerLevel level,
            VillagerEntityMCA villager,
            java.util.function.Predicate<ItemStack> matcher
    ) {
        Optional<Village> villageOpt = FarmersDelightCookAssignment.resolveVillage(villager);
        if (villageOpt.isEmpty()) return null;
        Village village = villageOpt.get();
        Set<Long> kitchenBounds = townstead$activeKitchenBounds(villager, townstead$activeKitchenReference(villager));

        NearbyItemSources.ContainerSlot best = null;
        Set<Long> visited = new HashSet<>();
        for (Building building : village.getBuildings().values()) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                long key = pos.asLong();
                if (!visited.add(key)) continue;
                if (!kitchenBounds.isEmpty() && !kitchenBounds.contains(key)) continue;
                if (!village.isWithinBorder(pos, 0)) continue;
                if (TownsteadConfig.isProtectedStorage(level.getBlockState(pos))) continue;

                BlockEntity be = level.getBlockEntity(pos);
                if (NearbyItemSources.isProcessingContainer(level, pos, be)) continue;

                if (be instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        ItemStack stack = container.getItem(i);
                        if (!matcher.test(stack)) continue;
                        int score = stack.getCount();
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (townstead$isBetterSlot(best, dist, score)) {
                            best = new NearbyItemSources.ContainerSlot(pos.immutable(), container, false, i, score, dist, null);
                        }
                    }
                }

                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!matcher.test(stack)) continue;
                        int score = stack.getCount();
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (townstead$isBetterSlot(best, dist, score)) {
                            best = new NearbyItemSources.ContainerSlot(pos.immutable(), null, true, i, score, dist, null);
                        }
                    }
                }
                for (Direction side : Direction.values()) {
                    handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                    if (handler == null) continue;
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!matcher.test(stack)) continue;
                        int score = stack.getCount();
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (townstead$isBetterSlot(best, dist, score)) {
                            best = new NearbyItemSources.ContainerSlot(pos.immutable(), null, true, i, score, dist, side);
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean townstead$addToInventoryOrNearbyStorage(
            ServerLevel level,
            VillagerEntityMCA villager,
            ItemStack stack,
            BlockPos center
    ) {
        if (stack.isEmpty()) return true;
        ItemStack remainder = villager.getInventory().addItem(stack);
        if (remainder.isEmpty()) return true;

        NearbyItemSources.insertIntoNearbyStorage(level, villager, remainder, 16, 3, center != null ? center : villager.blockPosition());
        if (remainder.isEmpty()) return false;

        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy()
        );
        drop.setPickUpDelay(0);
        level.addFreshEntity(drop);
        return false;
    }

    private boolean townstead$isBetterSlot(NearbyItemSources.ContainerSlot currentBest, double candidateDist, int candidateScore) {
        if (currentBest == null) return true;
        if (candidateDist < currentBest.distanceSqr() - 4.0d) return true;
        return candidateDist < currentBest.distanceSqr() + 4.0d && candidateScore > currentBest.score();
    }

    private boolean townstead$hasKnife(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (townstead$isKnifeStack(stack)) return true;
        }
        return false;
    }

    private java.util.function.Predicate<ItemStack> townstead$isKnifeStackPredicate() {
        return this::townstead$isKnifeStack;
    }

    private boolean townstead$isKnifeStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(KNIFE_TAG)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        return id.getPath().contains("knife");
    }

    private boolean townstead$ensureWaterAvailable(ServerLevel level, VillagerEntityMCA villager, BlockPos center) {
        BlockPos searchCenter = center != null ? center : villager.blockPosition();
        if (townstead$hasItem(villager.getInventory(), Items.WATER_BUCKET)) return true;

        if (!townstead$pullSingleTool(level, villager, s -> s.is(Items.WATER_BUCKET), searchCenter)) {
            // If no full bucket found, try empty bucket + nearby water source.
            if (!townstead$hasItem(villager.getInventory(), Items.BUCKET)) {
                townstead$pullSingleTool(level, villager, s -> s.is(Items.BUCKET), searchCenter);
            }
            if (!townstead$hasItem(villager.getInventory(), Items.BUCKET)) return false;
            if (!townstead$hasNearbyWater(level, searchCenter, 24, 4)) return false;
            if (!townstead$consume(villager.getInventory(), Items.BUCKET, 1)) return false;
            villager.getInventory().addItem(new ItemStack(Items.WATER_BUCKET));
        }
        return townstead$hasItem(villager.getInventory(), Items.WATER_BUCKET);
    }

    private boolean townstead$hasNearbyWater(ServerLevel level, BlockPos center, int radius, int vertical) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -vertical, -radius),
                center.offset(radius, vertical, radius))) {
            if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    private boolean townstead$hasItem(SimpleContainer inv, Item item) {
        return townstead$count(inv, item) > 0;
    }

    private int townstead$stageFromInventoryToStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            Item item,
            int count
    ) {
        if (count <= 0) return 0;
        int removed = townstead$removeUpTo(villager.getInventory(), item, count);
        if (removed <= 0) return 0;

        ItemStack toInsert = new ItemStack(item, removed);
        ItemStack remainder = townstead$insertIntoStation(level, stationAnchor, toInsert);
        if (!remainder.isEmpty()) {
            villager.getInventory().addItem(remainder);
        }
        return removed - remainder.getCount();
    }

    private int townstead$stageBowlsFromInventoryToStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            int count
    ) {
        if (count <= 0) return 0;
        int removed = townstead$removeUpTo(villager.getInventory(), Items.BOWL, count);
        if (removed <= 0) return 0;

        ItemStack toInsert = new ItemStack(Items.BOWL, removed);
        ItemStack remainder = townstead$insertIntoCookingPotContainerSlot(level, stationAnchor, toInsert, false);
        if (!remainder.isEmpty()) {
            villager.getInventory().addItem(remainder);
        }
        return removed - remainder.getCount();
    }

    private ItemStack townstead$insertIntoStation(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || pos == null) return stack;
        if (stack.is(Items.BOWL)) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (FD_COOKING_POT.equals(blockId)) {
                return townstead$insertIntoCookingPotContainerSlot(level, pos, stack, false);
            }
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return stack;

        ItemStack remainder = stack.copy();
        IItemHandler handler = townstead$preferredIngredientHandler(level, pos);
        if (handler != null) {
            remainder = townstead$insertIntoHandler(handler, remainder, false);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        } else {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                remainder = townstead$insertIntoHandler(handler, remainder, false);
                if (remainder.isEmpty()) return ItemStack.EMPTY;
            }

            if (!remainder.isEmpty()) {
                for (Direction dir : Direction.values()) {
                    handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
                    if (handler == null) continue;
                    remainder = townstead$insertIntoHandler(handler, remainder, false);
                    if (remainder.isEmpty()) return ItemStack.EMPTY;
                }
            }
        }

        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (remainder.isEmpty()) break;
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && !ItemStack.isSameItemSameComponents(slot, remainder)) continue;
                if (!container.canPlaceItem(i, remainder)) continue;
                int limit = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize());
                int slotCount = slot.getCount();
                int move = slot.isEmpty() ? Math.min(limit, remainder.getCount()) : Math.min(limit - slotCount, remainder.getCount());
                if (move <= 0) continue;
                if (slot.isEmpty()) {
                    container.setItem(i, remainder.copyWithCount(move));
                } else {
                    slot.grow(move);
                }
                remainder.shrink(move);
                container.setChanged();
            }
        }
        return remainder;
    }

    private ItemStack townstead$insertIntoCookingPotContainerSlot(
            ServerLevel level,
            BlockPos pos,
            ItemStack stack,
            boolean simulate
    ) {
        if (stack.isEmpty() || pos == null) return stack;
        if (!stack.is(Items.BOWL)) return stack;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return stack;

        ItemStack remainder = stack.copy();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            remainder = townstead$insertIntoHandlerSlot(handler, FD_COOKING_POT_CONTAINER_SLOT, remainder, simulate);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }

    private int townstead$cookingPotContainerBowlCount(ServerLevel level, BlockPos pos) {
        if (pos == null) return 0;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return 0;

        int best = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            if (FD_COOKING_POT_CONTAINER_SLOT < 0 || FD_COOKING_POT_CONTAINER_SLOT >= handler.getSlots()) continue;
            ItemStack slot = handler.getStackInSlot(FD_COOKING_POT_CONTAINER_SLOT);
            if (slot.is(Items.BOWL)) {
                best = Math.max(best, slot.getCount());
            }
        }
        return best;
    }

    private int townstead$countItemInStation(ServerLevel level, BlockPos pos, Item item) {
        if (pos == null || item == Items.AIR) return 0;
        int total = 0;

        IItemHandler handler = townstead$preferredIngredientHandler(level, pos);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (slot.is(item)) total += slot.getCount();
            }
            return total;
        }

        handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (slot.is(item)) total += slot.getCount();
            }
            return total;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.is(item)) total += slot.getCount();
            }
        }
        return total;
    }

    private ItemStack townstead$insertIntoHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int i = 0; i < handler.getSlots(); i++) {
            remainder = handler.insertItem(i, remainder, simulate);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }

    private ItemStack townstead$insertIntoHandlerSlot(IItemHandler handler, int slot, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) return stack;
        if (slot < 0 || slot >= handler.getSlots()) return stack;
        return handler.insertItem(slot, stack.copy(), simulate);
    }

    private boolean townstead$stationSupportsRecipe(ServerLevel level, BlockPos pos, RecipeDef recipe) {
        if (recipe == null) return true;
        if (pos == null) return false;
        if (recipe.stationType() == StationType.FIRE_STATION) {
            if (townstead$isSurfaceFireStation(level, pos)) {
                return townstead$surfaceCanCookRecipeInput(level, pos, recipe);
            }
            return true;
        }
        if (recipe.stationType() == StationType.HOT_STATION && !townstead$hotStationMatchesRecipe(level, pos, recipe)) {
            return false;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;

        for (Ingredient ingredient : recipe.inputs()) {
            Item item = BuiltInRegistries.ITEM.get(ingredient.itemId());
            if (item == Items.AIR) return false;
            if (!townstead$canInsertIntoStation(level, pos, new ItemStack(item, ingredient.count()))) return false;
        }
        return true;
    }

    private boolean townstead$canInsertIntoStation(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return true;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;

        if (stack.is(Items.BOWL)) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (FD_COOKING_POT.equals(blockId)) {
                return townstead$insertIntoCookingPotContainerSlot(level, pos, stack, true).isEmpty();
            }
        }

        ItemStack remainder = stack.copy();
        IItemHandler handler = townstead$preferredIngredientHandler(level, pos);
        if (handler != null) {
            remainder = townstead$insertIntoHandler(handler, remainder, true);
            if (remainder.isEmpty()) return true;
        } else {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                remainder = townstead$insertIntoHandler(handler, remainder, true);
                if (remainder.isEmpty()) return true;
            }

            if (!remainder.isEmpty()) {
                for (Direction dir : Direction.values()) {
                    handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
                    if (handler == null) continue;
                    remainder = townstead$insertIntoHandler(handler, remainder, true);
                    if (remainder.isEmpty()) return true;
                }
            }
        }

        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize() && !remainder.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && !ItemStack.isSameItemSameComponents(slot, remainder)) continue;
                if (!container.canPlaceItem(i, remainder)) continue;
                int limit = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize());
                int slotCount = slot.getCount();
                int move = slot.isEmpty() ? Math.min(limit, remainder.getCount()) : Math.min(limit - slotCount, remainder.getCount());
                if (move <= 0) continue;
                remainder.shrink(move);
            }
        }

        return remainder.isEmpty();
    }

    private ItemStack townstead$takeFromInventoryForPending(SimpleContainer inv, ItemStack pending) {
        if (pending.isEmpty()) return ItemStack.EMPTY;
        int remaining = pending.getCount();
        ItemStack pulled = ItemStack.EMPTY;

        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slot, pending)) continue;
            int move = Math.min(remaining, slot.getCount());
            if (move <= 0) continue;
            if (pulled.isEmpty()) {
                pulled = slot.copyWithCount(move);
            } else {
                pulled.grow(move);
            }
            slot.shrink(move);
            remaining -= move;
            if (slot.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

        return pulled;
    }

    private int townstead$storeOutputInCookStorage(
            ServerLevel level,
            VillagerEntityMCA villager,
            ItemStack stack,
            BlockPos center
    ) {
        if (stack.isEmpty()) return 0;
        String itemId = townstead$itemId(stack.getItem());
        Set<Long> kitchenBounds = townstead$activeKitchenBounds(villager, center);
        if (kitchenBounds.isEmpty()) return 0;
        BlockPos origin = center != null ? center : villager.blockPosition();
        int totalInserted = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-16, -3, -16),
                origin.offset(16, 3, 16))) {
            if (stack.isEmpty()) break;
            if (!kitchenBounds.contains(pos.asLong())) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (!townstead$isCookStorageCandidate(level, pos, be)) continue;

            int before = stack.getCount();
            if (be instanceof Container container) {
                townstead$insertIntoContainer(container, stack);
            }

            if (!stack.isEmpty()) {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    ItemStack remainder = townstead$insertIntoHandler(handler, stack, false);
                    int inserted = stack.getCount() - remainder.getCount();
                    if (inserted > 0) stack.shrink(inserted);
                }
                for (Direction dir : Direction.values()) {
                    if (stack.isEmpty()) break;
                    handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
                    if (handler == null) continue;
                    ItemStack remainder = townstead$insertIntoHandler(handler, stack, false);
                    int inserted = stack.getCount() - remainder.getCount();
                    if (inserted > 0) stack.shrink(inserted);
                }
            }

            int insertedHere = before - stack.getCount();
            if (insertedHere > 0) {
                totalInserted += insertedHere;
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
                lastOutputDest = pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + ":" + (blockId == null ? "unknown" : blockId);
                townstead$traceOutput("store:dst item=" + itemId
                        + " ins=" + insertedHere
                        + " at=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + " block=" + (blockId == null ? "unknown" : blockId));
            }
        }

        return totalInserted;
    }

    private Set<Long> townstead$activeKitchenBounds(VillagerEntityMCA villager, BlockPos anchor) {
        List<Building> kitchens = townstead$kitchenBuildings(villager);
        if (kitchens.isEmpty()) return Set.of();

        Building selected = null;
        if (anchor != null) {
            long anchorKey = anchor.asLong();
            for (Building building : kitchens) {
                for (BlockPos bp : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                    if (bp.asLong() != anchorKey) continue;
                    selected = building;
                    break;
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
                if (dist < best) {
                    best = dist;
                    selected = building;
                }
            }
            if (selected == null) selected = kitchens.get(0);
        }

        Set<Long> bounds = new HashSet<>();
        for (BlockPos bp : (Iterable<BlockPos>) selected.getBlockPosStream()::iterator) {
            bounds.add(bp.asLong());
        }
        return bounds;
    }

    private BlockPos townstead$activeKitchenReference(VillagerEntityMCA villager) {
        if (stationAnchor != null) return stationAnchor;
        BlockPos nearestAnchor = townstead$nearestKitchenAnchor(villager);
        if (nearestAnchor != null) return nearestAnchor;
        return villager.blockPosition();
    }

    private void townstead$insertIntoContainer(Container container, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;
            if (!container.canPlaceItem(i, stack)) continue;
            int limit = Math.min(container.getMaxStackSize(), slot.getMaxStackSize());
            if (slot.getCount() >= limit) continue;
            int move = Math.min(stack.getCount(), limit - slot.getCount());
            if (move <= 0) continue;
            slot.grow(move);
            stack.shrink(move);
            container.setChanged();
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty()) continue;
            if (!container.canPlaceItem(i, stack)) continue;
            int move = Math.min(stack.getCount(), Math.min(container.getMaxStackSize(), stack.getMaxStackSize()));
            if (move <= 0) continue;
            container.setItem(i, stack.copyWithCount(move));
            stack.shrink(move);
            container.setChanged();
        }
    }

    private boolean townstead$isCookStorageCandidate(ServerLevel level, BlockPos pos, BlockEntity be) {
        BlockState state = level.getBlockState(pos);
        if (TownsteadConfig.isProtectedStorage(state)) return false;
        if (NearbyItemSources.isProcessingContainer(level, pos, be)) return false;
        return state.is(FD_KITCHEN_STORAGE_TAG)
                || state.is(FD_KITCHEN_STORAGE_UPGRADED_TAG)
                || state.is(FD_KITCHEN_STORAGE_NETHER_TAG)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL);
    }

    private int townstead$inventoryAcceptance(SimpleContainer inv, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int remaining = stack.getCount();
        int maxPerSlot = Math.min(inv.getMaxStackSize(), stack.getMaxStackSize());

        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                int move = Math.min(remaining, maxPerSlot);
                remaining -= move;
                continue;
            }
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;
            int limit = Math.min(inv.getMaxStackSize(), slot.getMaxStackSize());
            int free = Math.max(0, limit - slot.getCount());
            int move = Math.min(remaining, free);
            remaining -= move;
        }

        return stack.getCount() - remaining;
    }

    private boolean townstead$hotStationMatchesRecipe(ServerLevel level, BlockPos pos, RecipeDef recipe) {
        if (recipe == null || recipe.stationType() != StationType.HOT_STATION) return true;
        Set<ResourceLocation> allowed = recipe.allowedHotStations();
        if (allowed == null || allowed.isEmpty()) return true;
        ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return stationId != null && allowed.contains(stationId);
    }

    private void townstead$consumeStagedInputs(ServerLevel level, RecipeDef recipe) {
        if (stationAnchor == null || recipe == null) return;
        for (Map.Entry<ResourceLocation, Integer> entry : stagedInputs.entrySet()) {
            Item item = BuiltInRegistries.ITEM.get(entry.getKey());
            if (item == Items.AIR) continue;
            townstead$extractFromStation(level, stationAnchor, item, entry.getValue());
        }
    }

    private int townstead$extractFromStation(ServerLevel level, BlockPos pos, Item item, int amount) {
        if (amount <= 0 || pos == null) return 0;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return 0;

        int removed = 0;
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots() && removed < amount; i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (!slot.is(item)) continue;
                int need = amount - removed;
                ItemStack extracted = handler.extractItem(i, need, false);
                removed += extracted.getCount();
            }
            if (removed >= amount) return removed;
        }

        for (Direction dir : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            for (int i = 0; i < handler.getSlots() && removed < amount; i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (!slot.is(item)) continue;
                int need = amount - removed;
                ItemStack extracted = handler.extractItem(i, need, false);
                removed += extracted.getCount();
            }
            if (removed >= amount) return removed;
        }

        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize() && removed < amount; i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.is(item)) continue;
                int need = amount - removed;
                int take = Math.min(need, slot.getCount());
                slot.shrink(take);
                removed += take;
                container.setChanged();
            }
        }
        return removed;
    }

    private ItemStack townstead$resolveHotStationOutput(ServerLevel level, BlockPos pos, RecipeDef recipe) {
        if (recipe == null) return ItemStack.EMPTY;
        ItemStack expected = townstead$outputStack(recipe);
        if (expected.isEmpty()) return ItemStack.EMPTY;
        if (pos == null) {
            townstead$traceOutput("resolve:null_pos item=" + townstead$itemId(expected.getItem()) + " out=" + expected.getCount());
            return expected;
        }

        Item outputItem = expected.getItem();
        int expectedCount = expected.getCount();
        int extractedCount = townstead$extractFromStation(level, pos, outputItem, expectedCount);
        if (extractedCount >= expectedCount) {
            townstead$traceOutput("resolve:station item=" + townstead$itemId(outputItem) + " expected=" + expectedCount + " extracted=" + extractedCount);
            return expected.copyWithCount(extractedCount);
        }

        // Fallback guarantee: never lose completed work if station extraction fails.
        townstead$traceOutput("resolve:fallback item=" + townstead$itemId(outputItem) + " expected=" + expectedCount + " extracted=" + extractedCount);
        return expected;
    }

    private IItemHandler townstead$preferredIngredientHandler(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (FD_COOKING_POT.equals(id) || FD_SKILLET.equals(id) || FD_CUTTING_BOARD.equals(id)) {
            IItemHandler up = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            if (up != null) return up;
        }
        return null;
    }

    private ItemStack townstead$outputStack(RecipeDef recipe) {
        Item item = BuiltInRegistries.ITEM.get(recipe.output());
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, recipe.outputCount());
    }

    private int townstead$count(SimpleContainer inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) total += inv.getItem(i).getCount();
        return total;
    }

    private int townstead$removeUpTo(SimpleContainer inv, Item item, int maxCount) {
        int removed = 0;
        for (int i = 0; i < inv.getContainerSize() && removed < maxCount; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(item)) continue;
            int take = Math.min(maxCount - removed, stack.getCount());
            stack.shrink(take);
            removed += take;
        }
        return removed;
    }

    private boolean townstead$consume(SimpleContainer inv, Item item, int needed) {
        int remaining = needed;
        for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return remaining <= 0;
    }

    private String townstead$unsupportedName(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.get(DataComponents.FOOD) != null) return stack.getHoverName().getString();
        }
        return "";
    }

    private boolean townstead$isStation(BlockState state) {
        return townstead$stationType(state) != null;
    }

    private boolean townstead$isStation(ServerLevel level, BlockPos pos) {
        return townstead$stationType(level, pos) != null;
    }

    private StationType townstead$stationType(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.CAMPFIRES) && townstead$fireSurfaceBlocked(level, pos)) return null;
        return townstead$stationType(state);
    }

    private StationType townstead$stationType(BlockState state) {
        if (state.is(BlockTags.CAMPFIRES)) return StationType.FIRE_STATION;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_CUTTING_BOARD.equals(id)) return StationType.CUTTING_BOARD;
        if (FD_STOVE.equals(id)) return StationType.FIRE_STATION;
        if (FD_SKILLET.equals(id)) return StationType.FIRE_STATION;
        if (FD_COOKING_POT.equals(id)) return StationType.HOT_STATION;
        return null;
    }

    private boolean townstead$fireSurfaceBlocked(ServerLevel level, BlockPos firePos) {
        BlockState above = level.getBlockState(firePos.above());
        ResourceLocation aboveId = BuiltInRegistries.BLOCK.getKey(above.getBlock());
        if (FD_COOKING_POT.equals(aboveId) || FD_SKILLET.equals(aboveId)) return true;
        return !above.canBeReplaced();
    }

    private BlockPos townstead$findStand(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int[] verticalChoices = new int[] {-1, 0, 1};
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            for (int yOffset : verticalChoices) {
                BlockPos c = anchor.relative(dir).offset(0, yOffset, 0);
                if (!level.getBlockState(c).isAir() || !level.getBlockState(c.above()).isAir()) continue;
                BlockPos belowPos = c.below();
                BlockState belowState = level.getBlockState(belowPos);
                if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) continue;
                if (townstead$avoidStandingSurface(belowState)) continue;

                double dist = villager.distanceToSqr(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
                if (yOffset == -1) dist -= 2.0d; // Prefer standing beside elevated counters/stations.
                if (dist < bestDist) {
                    bestDist = dist;
                    best = c.immutable();
                }
            }
        }
        return best;
    }

    private boolean townstead$avoidStandingSurface(BlockState surface) {
        if (townstead$isStation(surface)) return true;
        return surface.is(FD_KITCHEN_STORAGE_TAG)
                || surface.is(FD_KITCHEN_STORAGE_UPGRADED_TAG)
                || surface.is(FD_KITCHEN_STORAGE_NETHER_TAG)
                || surface.is(Blocks.CHEST)
                || surface.is(Blocks.TRAPPED_CHEST)
                || surface.is(Blocks.BARREL);
    }

    private void townstead$setBlocked(ServerLevel level, VillagerEntityMCA villager, long gameTime, BlockedReason reason, String itemName) {
        blocked = reason;
        unsupportedItem = itemName == null ? "" : itemName;
        if (reason == BlockedReason.NONE) {
            lastFailure = "none";
        }
        if (!TownsteadConfig.ENABLE_COOK_REQUEST_CHAT.get()) return;
        if (blocked == BlockedReason.NONE) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) return;
        switch (blocked) {
            case NO_KITCHEN -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_kitchen/" + (1 + level.random.nextInt(4)));
            case NO_INGREDIENTS -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_ingredients/" + (1 + level.random.nextInt(6)));
            case NO_STORAGE -> villager.sendChatToAllAround("dialogue.chat.cook_request.no_storage/" + (1 + level.random.nextInt(4)));
            case UNREACHABLE -> villager.sendChatToAllAround("dialogue.chat.cook_request.unreachable/" + (1 + level.random.nextInt(6)));
            case NO_RECIPE -> villager.sendChatToAllAround("dialogue.chat.cook_request.unsupported_item", unsupportedItem.isBlank() ? "that" : unsupportedItem);
            case NONE -> { }
        }
        nextRequestTick = gameTime + Math.max(200, TownsteadConfig.COOK_REQUEST_INTERVAL_TICKS.get());
    }

    private void townstead$playSound(ServerLevel level) {
        if (stationAnchor == null || stationType == null) return;
        if (stationType == StationType.CUTTING_BOARD) {
            level.playSound(null, stationAnchor, SoundEvents.AXE_STRIP, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.1f);
        } else {
            level.playSound(null, stationAnchor, SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 1.0f);
        }
    }

    private void townstead$noteCompletedRecipe(RecipeDef recipe) {
        if (recipe == null || recipe.output() == null) return;
        if (recipe.output().equals(lastCompletedOutput)) {
            consecutiveCompletedOutput++;
        } else {
            lastCompletedOutput = recipe.output();
            consecutiveCompletedOutput = 1;
        }
    }

    private Activity townstead$currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private void townstead$debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime < nextDebugTick) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;

        String cookName = villager.getName().getString();
        String cookId = villager.getUUID().toString();
        if (cookId.length() > 8) cookId = cookId.substring(0, 8);
        String phase = workPhase.name().toLowerCase();
        String station = stationType == null ? "none" : stationType.name().toLowerCase();
        String recipe = activeRecipe == null ? "none" : activeRecipe.output().toString();
        String blockedState = blocked == null ? "none" : blocked.name().toLowerCase();
        String plan = shiftPlan.isEmpty() ? "0/0" : Math.min(shiftPlanIndex + 1, shiftPlan.size()) + "/" + shiftPlan.size();
        String anchor = stationAnchor == null ? "none" : stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ();
        String msg = "[CookDBG:" + cookName + "#" + cookId + "] phase=" + phase
                + " blocked=" + blockedState
                + " station=" + station
                + " anchor=" + anchor
                + " recipe=" + recipe
                + " plan=" + plan
                + " doneAt=" + cookDoneTick
                + " pending=" + pendingOutput.getCount()
                + " fail=" + lastFailure
                + " out=" + lastOutputTrace
                + " dst=" + lastOutputDest;
        player.sendSystemMessage(Component.literal(msg));
        nextDebugTick = gameTime + 100L;
    }

    private void townstead$traceOutput(String trace) {
        if (trace == null || trace.isBlank()) return;
        lastOutputTrace = trace;
    }

    private String townstead$itemId(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "unknown" : id.toString();
    }

    private BlockedReason townstead$blockedReasonFromFailure() {
        if (lastFailure == null || lastFailure.isBlank() || "none".equals(lastFailure)) {
            return BlockedReason.NO_INGREDIENTS;
        }
        if (lastFailure.startsWith("no_station")) {
            return BlockedReason.NO_KITCHEN;
        }
        if (lastFailure.startsWith("surface_full")) {
            return BlockedReason.UNREACHABLE;
        }
        if (lastFailure.startsWith("surface_recipe")) {
            return BlockedReason.NO_RECIPE;
        }
        if (lastFailure.startsWith("station_rejects") || lastFailure.startsWith("station_partial")) {
            return BlockedReason.NO_RECIPE;
        }
        if (lastFailure.startsWith("missing") || lastFailure.startsWith("consume_failed")) {
            return BlockedReason.NO_INGREDIENTS;
        }
        return BlockedReason.NO_INGREDIENTS;
    }

    private static RecipeDef recipe(
            int minTier,
            StationType stationType,
            String outputId,
            int outputCount,
            int timeTicks,
            boolean needsKnife,
            boolean needsWater,
            Ingredient... inputs
    ) {
        return new RecipeDef(
                minTier,
                stationType,
                ResourceLocation.parse(outputId),
                outputCount,
                timeTicks,
                needsKnife,
                needsWater,
                0,
                List.of(inputs),
                Set.of()
        );
    }

    private static RecipeDef hotRecipe(
            int minTier,
            String outputId,
            int outputCount,
            int timeTicks,
            boolean needsKnife,
            boolean needsWater,
            Set<ResourceLocation> allowedHotStations,
            Ingredient... inputs
    ) {
        return hotRecipe(
                minTier,
                outputId,
                outputCount,
                timeTicks,
                needsKnife,
                needsWater,
                0,
                allowedHotStations,
                inputs
        );
    }

    private static RecipeDef hotRecipe(
            int minTier,
            String outputId,
            int outputCount,
            int timeTicks,
            boolean needsKnife,
            boolean needsWater,
            int bowlsRequired,
            Set<ResourceLocation> allowedHotStations,
            Ingredient... inputs
    ) {
        return new RecipeDef(
                minTier,
                StationType.HOT_STATION,
                ResourceLocation.parse(outputId),
                outputCount,
                timeTicks,
                needsKnife,
                needsWater,
                Math.max(0, bowlsRequired),
                List.of(inputs),
                Set.copyOf(allowedHotStations)
        );
    }

    private static Ingredient in(String itemId, int count) {
        return new Ingredient(ResourceLocation.parse(itemId), count);
    }
}
