package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.station.WorksiteStationIndex;

import com.aetherianartificer.townstead.work.station.Stations;

import com.aetherianartificer.townstead.work.station.StationProtocols;
import com.aetherianartificer.townstead.work.station.StationRecipeMatch;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;

import com.mojang.authlib.GameProfile;
import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.work.WorkPathing;
import com.aetherianartificer.townstead.work.producer.ProducerStationClaims;
import com.aetherianartificer.townstead.work.producer.ProducerStationSessions;
import com.aetherianartificer.townstead.work.producer.ProducerStationState;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCompat;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.storage.StorageSearchContext;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
//? if >=1.21 {
import net.minecraft.world.item.crafting.RecipeHolder;
//?}
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.ItemEntity;
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
*///?}
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

final class FarmersDelightStationInternals {

    static @Nullable ServerPlayer cuttingBoardActor(ServerLevel level) {
        if (level == null) return null;
        try {
            return FakePlayerFactory.get(level, TOWNSTEAD_COOK_PROFILE);
        } catch (Throwable ignored) {
            return null;
        }
    }
    private FarmersDelightStationInternals() {}
    private static final GameProfile TOWNSTEAD_COOK_PROFILE =
            new GameProfile(UUID.fromString("7d0d7ac4-9d5a-4afc-bcaa-7e6bb86a7a4d"), "[TownsteadCook]");

    // ── Block IDs ──

    // Operational-protocol block ids (skillet/board/stove reflection paths and fire-surface
    // heuristics). Which blocks ARE stations is decided by workstation defs, never these.
    //? if >=1.21 {
    private static final ResourceLocation FD_SKILLET = ResourceLocation.parse("farmersdelight:skillet");
    private static final ResourceLocation FD_STOVE = ResourceLocation.parse("farmersdelight:stove");
    //?} else {
    /*private static final ResourceLocation FD_CUTTING_BOARD = new ResourceLocation("farmersdelight", "cutting_board");
    private static final ResourceLocation FD_SKILLET = new ResourceLocation("farmersdelight", "skillet");
    private static final ResourceLocation FD_STOVE = new ResourceLocation("farmersdelight", "stove");
    *///?}
    // ── Reflection fields ──

    private static Class<?> FD_STOVE_BE_CLASS;
    private static Method FD_STOVE_GET_NEXT_EMPTY_SLOT;
    private static Method FD_STOVE_GET_MATCHING_RECIPE;
    private static Method FD_STOVE_ADD_ITEM;
    private static Method FD_STOVE_IS_BLOCKED_ABOVE;
    private static Method FD_STOVE_GET_INVENTORY;
    private static Class<?> FD_SKILLET_BE_CLASS;
    private static Method FD_SKILLET_HAS_STORED_STACK;
    private static Method FD_SKILLET_ADD_ITEM_TO_COOK;
    private static Method FD_SKILLET_IS_HEATED;

    // ── Surface fire station operations ──

    public static boolean isSurfaceFireStation(ServerLevel level, BlockPos pos) {
        if (pos == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.CAMPFIRES)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return FD_STOVE.equals(id) || FD_SKILLET.equals(id);
    }

    public static BlockPos canonicalStationAnchor(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return pos;
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!FD_STOVE.equals(id)) return pos;
        return canonicalStoveAnchor(level, pos);
    }

    public static boolean surfaceHasFreeSlot(ServerLevel level, BlockPos pos) {
        return surfaceFreeSlotCount(level, pos) > 0;
    }

    public static int surfaceFreeSlotCount(ServerLevel level, BlockPos pos) {
        if (pos == null) return 0;
        pos = canonicalStationAnchor(level, pos);
        BlockState state = level.getBlockState(pos);
        if (surfaceBlockedForCooking(level, pos, state)) return 0;
        BlockEntity be = level.getBlockEntity(pos);
        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            int free = 0;
            for (ItemStack slot : campfire.getItems()) {
                if (slot.isEmpty()) free++;
            }
            return free;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (stoveBlockedAbove(be)) return 0;
            IItemHandler handler = com.aetherianartificer.townstead.work.station.BlockInventories.itemHandler(level, pos, null);
            if (handler == null) {
                Integer slot = stoveNextEmptySlot(be);
                if (slot != null && slot >= 0) return 1;
                int reflectedFree = stoveReflectedFreeSlotCount(be);
                if (reflectedFree > 0) return reflectedFree;
                // FD stove internals vary enough across versions that reflective slot discovery can fail
                // even for an empty, usable stove. Discovery should still treat an unblocked stove as loadable.
                return 6;
            }
            int free = 0;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).isEmpty()) free++;
            }
            if (handler.getSlots() <= 1) {
                int reflectedFree = stoveReflectedFreeSlotCount(be);
                if (reflectedFree > 0) return reflectedFree;
                Integer nextEmpty = stoveNextEmptySlot(be);
                if ((nextEmpty != null && nextEmpty >= 0) || free > 0) {
                    // Some FD builds expose the stove through a single aggregate handler slot.
                    // Treat a usable unblocked stove as its real multi-slot capacity for loading.
                    return 6;
                }
            }
            return free;
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return 0;
            return skilletHasStoredStack(be) ? 0 : 1;
        }
        return 0;
    }

    public static boolean surfaceCanCookRecipeInput(ServerLevel level, BlockPos pos, DiscoveredRecipe recipe) {
        if (recipe == null || recipe.inputs().isEmpty()) return false;
        pos = canonicalStationAnchor(level, pos);
        BlockState state = level.getBlockState(pos);
        if (surfaceBlockedForCooking(level, pos, state)) return false;
        RecipeIngredient input = recipe.inputs().get(0);
        Item inputItem = BuiltInRegistries.ITEM.get(input.primaryId());
        if (inputItem == Items.AIR) return false;
        ItemStack probe = new ItemStack(inputItem, 1);

        BlockEntity be = level.getBlockEntity(pos);
        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            return campfire.getCookableRecipe(probe)
                    .filter(match -> StationRecipeMatch.produces(level, match, recipe.output()))
                    .isPresent();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (stoveBlockedAbove(be)) return false;
            Optional<?> stoveMatch = stoveMatchingRecipe(be, probe);
            if (stoveMatch.isPresent()) {
                return StationRecipeMatch.produces(level, stoveMatch.get(), recipe.output());
            }
            return campfireRecipeForInput(level, probe)
                    .filter(match -> StationRecipeMatch.produces(level, match, recipe.output()))
                    .isPresent();
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                return false;
            }
            return campfireRecipeForInput(level, probe)
                    .filter(match -> StationRecipeMatch.produces(level, match, recipe.output()))
                    .isPresent();
        }
        return false;
    }

    public static boolean surfaceCanCookInputStack(ServerLevel level, BlockPos pos, ItemStack probe) {
        if (level == null || pos == null || probe == null || probe.isEmpty()) return false;
        pos = canonicalStationAnchor(level, pos);
        BlockState state = level.getBlockState(pos);
        if (surfaceBlockedForCooking(level, pos, state)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            return campfire.getCookableRecipe(com.aetherianartificer.townstead.work.station.StationInventoryOps.copyOne(probe)).isPresent();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (stoveBlockedAbove(be)) return false;
            if (stoveMatchingRecipe(be, com.aetherianartificer.townstead.work.station.StationInventoryOps.copyOne(probe)).isPresent()) return true;
            return campfireRecipeForInput(level, com.aetherianartificer.townstead.work.station.StationInventoryOps.copyOne(probe)).isPresent();
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                return false;
            }
            return campfireRecipeForInput(level, com.aetherianartificer.townstead.work.station.StationInventoryOps.copyOne(probe)).isPresent();
        }
        return false;
    }

    public static boolean loadSurfaceFireStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            DiscoveredRecipe recipe
    ) {
        if (recipe == null || stationAnchor == null || recipe.inputs().isEmpty()) return false;
        stationAnchor = canonicalStationAnchor(level, stationAnchor);
        RecipeIngredient input = recipe.inputs().get(0);
        Item inputItem = BuiltInRegistries.ITEM.get(input.primaryId());
        if (inputItem == Items.AIR) return false;
        SimpleContainer inv = villager.getInventory();
        if (count(inv, inputItem) <= 0) return false;

        int freeSlots = surfaceFreeSlotCount(level, stationAnchor);
        if (freeSlots <= 0) return false;
        int ingredientPerLoad = Math.max(1, input.count());
        int availableInput = count(inv, inputItem);
        int maxLoads = Math.min(freeSlots, availableInput / ingredientPerLoad);
        if (maxLoads <= 0) return false;

        ItemStack one = new ItemStack(inputItem, ingredientPerLoad);
        BlockState state = level.getBlockState(stationAnchor);
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (surfaceBlockedForCooking(level, stationAnchor, state)) return false;
        int loadedCount = 0;
        for (int attempt = 0; attempt < maxLoads; attempt++) {
            boolean loaded = false;
            int consumedAmount = ingredientPerLoad;
            if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
                //? if >=1.21 {
                Optional<RecipeHolder<CampfireCookingRecipe>> match = campfire.getCookableRecipe(one);
                if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) break;
                int cookTime = match.get().value().getCookingTime();
                //?} else {
                /*Optional<CampfireCookingRecipe> match = campfire.getCookableRecipe(one);
                if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) break;
                int cookTime = match.get().getCookingTime();
                *///?}
                loaded = campfire.placeFood(villager, one.copy(), cookTime);
            } else {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (FD_STOVE.equals(id)) {
                    if (stoveBlockedAbove(be)) break;
                    Optional<?> match = stoveMatchingRecipe(be, one);
                    if (match.isEmpty()) {
                        // Fallback: use campfire recipe (stoves can cook campfire recipes)
                        match = campfireRecipeForInput(level, one);
                    }
                    if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) break;
                    loaded = stoveAddItemAnySlot(be, one.copy(), match.get());
                } else if (FD_SKILLET.equals(id)) {
                    if (!skilletIsHeated(be)) break;
                    if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                            && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) break;
                    if (skilletHasStoredStack(be)) break;
                    Optional<?> match = campfireRecipeForInput(level, one);
                    if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) break;
                    //? if >=1.21 {
                    int skilletBatch = Math.min(count(inv, inputItem), inputItem.getDefaultMaxStackSize());
                    //?} else {
                    /*int skilletBatch = Math.min(count(inv, inputItem), inputItem.getMaxStackSize());
                    *///?}
                    if (skilletBatch <= 0) break;
                    int inserted = skilletAddItem(level, villager, be, new ItemStack(inputItem, skilletBatch), stationAnchor);
                    if (inserted >= ingredientPerLoad) {
                        loaded = true;
                        consumedAmount = inserted;
                    } else break;
                }
            }
            if (!loaded) break;
            if (!consume(inv, inputItem, consumedAmount)) return loadedCount > 0;
            loadedCount++;
        }
        return loadedCount > 0;
    }

    public static boolean loadSurfaceFireStationItem(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            DiscoveredRecipe recipe,
            ItemStack stack
    ) {
        if (recipe == null || stationAnchor == null || stack == null || stack.isEmpty() || recipe.inputs().isEmpty()) return false;
        stationAnchor = canonicalStationAnchor(level, stationAnchor);
        RecipeIngredient input = recipe.inputs().get(0);
        int ingredientPerLoad = Math.max(1, input.count());
        if (stack.getCount() < ingredientPerLoad) return false;

        ItemStack one = com.aetherianartificer.townstead.work.station.StationInventoryOps.copyWithCount(stack, ingredientPerLoad);
        BlockState state = level.getBlockState(stationAnchor);
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (surfaceBlockedForCooking(level, stationAnchor, state)) return false;

        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            //? if >=1.21 {
            Optional<RecipeHolder<CampfireCookingRecipe>> match = campfire.getCookableRecipe(one);
            if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) return false;
            return campfire.placeFood(villager, one, match.get().value().getCookingTime());
            //?} else {
            /*Optional<CampfireCookingRecipe> match = campfire.getCookableRecipe(one);
            if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) return false;
            return campfire.placeFood(villager, one, match.get().getCookingTime());
            *///?}
        }

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (stoveBlockedAbove(be)) return false;
            Optional<?> match = stoveMatchingRecipe(be, one);
            if (match.isEmpty()) {
                match = campfireRecipeForInput(level, one);
            }
            return match.isPresent()
                    && StationRecipeMatch.produces(level, match.get(), recipe.output())
                    && stoveAddItemAnySlot(be, one, match.get());
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) return false;
            if (skilletHasStoredStack(be)) return false;
            Optional<?> match = campfireRecipeForInput(level, one);
            if (match.isEmpty() || !StationRecipeMatch.produces(level, match.get(), recipe.output())) return false;
            return skilletAddItem(level, villager, be, one, stationAnchor) >= ingredientPerLoad;
        }
        return false;
    }

    // ── Purification loading ──

    public static boolean loadPurificationFireStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos pos,
            ThirstCompatBridge bridge
    ) {
        if (pos == null) return false;
        pos = canonicalStationAnchor(level, pos);
        if (ProducerStationClaims.isClaimedByOther(level, villager.getUUID(), pos)) return false;
        if (!isSurfaceFireStation(level, pos)) return false;

        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (surfaceBlockedForCooking(level, pos, state)) return false;

        ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        SimpleContainer inv = villager.getInventory();
        int loaded = 0;

        // For purification, don't require a campfire cooking recipe match —
        // TWP handles purity via its own event system. Just check for impure water.
        java.util.function.Predicate<ItemStack> impureFilter = stack -> !stack.isEmpty();
        int PURIFICATION_COOK_TIME = 100;

        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            int free = surfaceFreeSlotCount(level, pos);
            for (int i = 0; i < free; i++) {
                int slot = com.aetherianartificer.townstead.work.recipe.WaterPurificationItems.bestSlot(inv, bridge, impureFilter);
                if (slot < 0) break;
                ItemStack source = inv.getItem(slot);
                if (source.isEmpty()) break;
                ItemStack oneItem = com.aetherianartificer.townstead.work.station.StationInventoryOps.copyOne(source);
                // Try recipe match for cook time, fall back to synthetic cook time
                //? if >=1.21 {
                int cookTime = campfire.getCookableRecipe(oneItem)
                        .map(h -> h.value().getCookingTime())
                        .orElse(PURIFICATION_COOK_TIME);
                //?} else {
                /*int cookTime = campfire.getCookableRecipe(oneItem)
                        .map(h -> h.getCookingTime())
                        .orElse(PURIFICATION_COOK_TIME);
                *///?}
                if (!campfire.placeFood(villager, oneItem, cookTime)) break;
                source.shrink(1);
                loaded++;
            }
        } else if (FD_STOVE.equals(stationId)) {
            if (stoveBlockedAbove(be)) return false;
            while (true) {
                int slot = com.aetherianartificer.townstead.work.recipe.WaterPurificationItems.bestSlot(inv, bridge, impureFilter);
                if (slot < 0) break;
                ItemStack source = inv.getItem(slot);
                if (source.isEmpty()) break;
                ItemStack oneItem = com.aetherianartificer.townstead.work.station.StationInventoryOps.copyOne(source);
                // Try stove recipe, then campfire recipe fallback
                Optional<?> match = stoveMatchingRecipe(be, oneItem);
                if (match.isEmpty()) match = campfireRecipeForInput(level, oneItem);
                if (match.isEmpty()) {
                    // No recipe at all — stoveAddItem requires one, so skip stove for purification
                    break;
                }
                if (!stoveAddItemAnySlot(be, oneItem, match.get())) break;
                source.shrink(1);
                loaded++;
            }
        } else if (FD_SKILLET.equals(stationId)) {
            if (!skilletIsHeated(be) || skilletHasStoredStack(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) return false;
            int slot = com.aetherianartificer.townstead.work.recipe.WaterPurificationItems.bestSlot(inv, bridge, impureFilter);
            if (slot < 0) return false;
            ItemStack source = inv.getItem(slot);
            if (source.isEmpty()) return false;
            ItemStack prototype = com.aetherianartificer.townstead.work.station.StationInventoryOps.copyOne(source);
            int available = com.aetherianartificer.townstead.work.recipe.WaterPurificationItems.countMatching(inv, prototype, bridge);
            if (available <= 0) return false;
            int batch = Math.min(available, source.getMaxStackSize());
            int inserted = skilletAddItem(level, villager, be,
                    com.aetherianartificer.townstead.work.station.StationInventoryOps.copyWithCount(prototype, batch), pos);
            if (inserted <= 0) return false;
            loaded += com.aetherianartificer.townstead.work.recipe.WaterPurificationItems.consumeMatching(inv, prototype, bridge, inserted);
        }
        return loaded > 0;
    }

    // ── Cutting board interaction ──



    // FD <1.3 returned boolean (true = placed). FD 1.3+ returns the leftover ItemStack
    // (empty = fully placed). Reflection callers must accept both shapes.


    static InteractionResult invokeCuttingBoardBlockUse(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            ServerPlayer actor,
            InteractionHand hand,
            BlockHitResult hit
    ) throws ReflectiveOperationException {
        Object block = state.getBlock();
        ItemStack held = actor.getItemInHand(hand);
        for (Method method : block.getClass().getMethods()) {
            String name = method.getName();
            Class<?>[] params = method.getParameterTypes();
            if ("use".equals(name) && params.length == 6) {
                Object result = method.invoke(block, state, level, pos, actor, hand, hit);
                if (result instanceof InteractionResult interactionResult) {
                    return interactionResult;
                }
            }
            if ("useItemOn".equals(name) && params.length == 7) {
                Object result = method.invoke(block, held, state, level, pos, actor, hand, hit);
                if (result instanceof InteractionResult interactionResult) {
                    return interactionResult;
                }
            }
        }
        return InteractionResult.PASS;
    }



    public static boolean supportsPurificationAt(ServerLevel level, BlockPos pos) {
        if (pos == null) return false;
        pos = canonicalStationAnchor(level, pos);
        if (!isSurfaceFireStation(level, pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (surfaceBlockedForCooking(level, pos, state)) return false;
        ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (state.is(BlockTags.CAMPFIRES)) {
            return surfaceHasFreeSlot(level, pos);
        }
        if (FD_SKILLET.equals(stationId)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return false;
            if (!skilletIsHeated(be) || skilletHasStoredStack(be)) return false;
            return !(state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED)));
        }
        if (FD_STOVE.equals(stationId)) {
            return false;
        }
        return false;
    }

    //? if >=1.21 {
    public static Optional<RecipeHolder<CampfireCookingRecipe>> campfireRecipeForInput(ServerLevel level, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return Optional.empty();
        for (RecipeHolder<CampfireCookingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            CampfireCookingRecipe recipe = holder.value();
            if (recipe.getIngredients().isEmpty()) continue;
            if (recipe.getIngredients().get(0).test(stack)) return Optional.of(holder);
        }
        return Optional.empty();
    }
    //?} else {
    /*public static Optional<CampfireCookingRecipe> campfireRecipeForInput(ServerLevel level, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return Optional.empty();
        for (CampfireCookingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            if (recipe.getIngredients().isEmpty()) continue;
            if (recipe.getIngredients().get(0).test(stack)) return Optional.of(recipe);
        }
        return Optional.empty();
    }
    *///?}

    // ── Fire surface helpers ──

    static boolean surfaceBlockedForCooking(ServerLevel level, BlockPos pos, BlockState state) {
        if (pos == null || state == null) return true;
        return Stations.coverBlocksWork(level, pos, state);
    }

    private static BlockPos canonicalStoveAnchor(ServerLevel level, BlockPos origin) {
        BlockPos best = origin;
        if (isStoveBlockEntity(level.getBlockEntity(origin))) {
            best = origin;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos cursor = origin.relative(direction);
            while (cursor.distManhattan(origin) <= 6) {
                BlockState state = level.getBlockState(cursor);
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                if (!FD_STOVE.equals(id)) break;
                if (isStoveBlockEntity(level.getBlockEntity(cursor)) && comparePos(cursor, best) < 0) {
                    best = cursor.immutable();
                }
                cursor = cursor.relative(direction);
            }
        }
        return best.immutable();
    }

    private static boolean isStoveBlockEntity(@Nullable BlockEntity be) {
        return be != null && ensureStoveReflection() && FD_STOVE_BE_CLASS != null && FD_STOVE_BE_CLASS.isInstance(be);
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
        return Integer.compare(a.getX(), b.getX());
    }

    // ── Stove reflection ──

    private static boolean ensureStoveReflection() {
        if (FD_STOVE_BE_CLASS != null && FD_STOVE_GET_NEXT_EMPTY_SLOT != null
                && FD_STOVE_GET_MATCHING_RECIPE != null && FD_STOVE_ADD_ITEM != null
                && FD_STOVE_IS_BLOCKED_ABOVE != null && FD_STOVE_GET_INVENTORY != null) {
            return true;
        }
        try {
            FD_STOVE_BE_CLASS = Class.forName("vectorwing.farmersdelight.common.block.entity.StoveBlockEntity");
            FD_STOVE_GET_NEXT_EMPTY_SLOT = FD_STOVE_BE_CLASS.getMethod("getNextEmptySlot");
            FD_STOVE_GET_INVENTORY = FD_STOVE_BE_CLASS.getMethod("getInventory");
            //? if >=1.21 {
            FD_STOVE_GET_MATCHING_RECIPE = FD_STOVE_BE_CLASS.getMethod("getMatchingRecipe", ItemStack.class);
            Class<?> recipeHolderClass = Class.forName("net.minecraft.world.item.crafting.RecipeHolder");
            FD_STOVE_ADD_ITEM = FD_STOVE_BE_CLASS.getMethod("addItem", ItemStack.class, recipeHolderClass, int.class);
            //?} else {
            /*FD_STOVE_GET_MATCHING_RECIPE = FD_STOVE_BE_CLASS.getMethod("getMatchingRecipe", net.minecraft.world.Container.class, int.class);
            FD_STOVE_ADD_ITEM = FD_STOVE_BE_CLASS.getMethod("addItem", ItemStack.class, CampfireCookingRecipe.class, int.class);
            *///?}
            FD_STOVE_IS_BLOCKED_ABOVE = FD_STOVE_BE_CLASS.getMethod("isStoveBlockedAbove");
            return true;
        } catch (Throwable ignored) {
            FD_STOVE_BE_CLASS = null; FD_STOVE_GET_NEXT_EMPTY_SLOT = null;
            FD_STOVE_GET_MATCHING_RECIPE = null; FD_STOVE_ADD_ITEM = null;
            FD_STOVE_IS_BLOCKED_ABOVE = null; FD_STOVE_GET_INVENTORY = null;
            return false;
        }
    }

    static Integer stoveNextEmptySlot(BlockEntity be) {
        if (be == null || !ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return null;
        try { Object v = FD_STOVE_GET_NEXT_EMPTY_SLOT.invoke(be); return v instanceof Integer i ? i : null; }
        catch (Throwable ignored) { return null; }
    }

    private static Integer stoveReflectedNextEmptySlot(BlockEntity be) {
        List<ItemStack> slots = stoveReflectedSlots(be);
        if (slots.isEmpty()) return null;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).isEmpty()) return i;
        }
        return null;
    }

    private static int stoveReflectedFreeSlotCount(BlockEntity be) {
        List<ItemStack> slots = stoveReflectedSlots(be);
        if (slots.isEmpty()) return 0;
        int free = 0;
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) free++;
        }
        return free;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> stoveReflectedSlots(@Nullable BlockEntity be) {
        if (be == null) return List.of();
        Class<?> type = be.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(be);
                    if (value instanceof Container container) {
                        List<ItemStack> slots = new ArrayList<>();
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            slots.add(container.getItem(i));
                        }
                        if (!slots.isEmpty() && slots.size() <= 6) return slots;
                    }
                    if (value instanceof List<?> list && !list.isEmpty()) {
                        boolean itemStackList = true;
                        List<ItemStack> slots = new ArrayList<>();
                        for (Object entry : list) {
                            if (!(entry instanceof ItemStack stack)) {
                                itemStackList = false;
                                break;
                            }
                            slots.add(stack);
                        }
                        if (itemStackList && !slots.isEmpty() && slots.size() <= 6) {
                            return slots;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            type = type.getSuperclass();
        }
        return List.of();
    }

    static Optional<?> stoveMatchingRecipe(BlockEntity be, ItemStack stack) {
        if (be == null || stack.isEmpty() || !ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return Optional.empty();
        try {
            //? if >=1.21 {
            Object v = FD_STOVE_GET_MATCHING_RECIPE.invoke(be, stack.copy());
            //?} else {
            /*Object inventory = FD_STOVE_GET_INVENTORY.invoke(be);
            Integer slot = stoveNextEmptySlot(be);
            if (!(inventory instanceof net.minecraft.world.Container container) || slot == null || slot < 0) return Optional.empty();
            container.setItem(slot, stack.copy());
            Object v;
            try {
                v = FD_STOVE_GET_MATCHING_RECIPE.invoke(be, container, slot);
            } finally {
                container.setItem(slot, ItemStack.EMPTY);
            }
            *///?}
            if (v instanceof Optional<?> o) return o;
        }
        catch (Throwable ignored) {}
        return Optional.empty();
    }

    static boolean stoveAddItem(BlockEntity be, ItemStack stack, Object recipeHolder, int slot) {
        if (be == null || stack.isEmpty() || recipeHolder == null) return false;
        if (!ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return false;
        try { Object v = FD_STOVE_ADD_ITEM.invoke(be, stack, recipeHolder, slot); return v instanceof Boolean b && b; }
        catch (Throwable ignored) { return false; }
    }

    private static boolean stoveAddItemAnySlot(BlockEntity be, ItemStack stack, Object recipeHolder) {
        if (be == null || stack.isEmpty() || recipeHolder == null) return false;
        LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
        Integer reported = stoveNextEmptySlot(be);
        if (reported != null && reported >= 0) {
            candidates.add(reported);
        }
        Integer reflected = stoveReflectedNextEmptySlot(be);
        if (reflected != null && reflected >= 0) {
            candidates.add(reflected);
        }
        List<ItemStack> reflectedSlots = stoveReflectedSlots(be);
        for (int i = 0; i < reflectedSlots.size(); i++) {
            if (reflectedSlots.get(i).isEmpty()) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            for (int i = 0; i < 6; i++) {
                candidates.add(i);
            }
        }
        for (Integer slot : candidates) {
            if (slot == null || slot < 0) continue;
            if (stoveAddItem(be, stack, recipeHolder, slot)) {
                return true;
            }
        }
        return false;
    }

    static boolean stoveBlockedAbove(BlockEntity be) {
        if (be == null || !ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return false;
        try { Object v = FD_STOVE_IS_BLOCKED_ABOVE.invoke(be); return v instanceof Boolean b && b; }
        catch (Throwable ignored) { return false; }
    }

    // ── Skillet reflection ──

    private static boolean ensureSkilletReflection() {
        if (FD_SKILLET_BE_CLASS != null && FD_SKILLET_HAS_STORED_STACK != null
                && FD_SKILLET_ADD_ITEM_TO_COOK != null && FD_SKILLET_IS_HEATED != null) {
            return true;
        }
        try {
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            FD_SKILLET_BE_CLASS = Class.forName("vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity");
            FD_SKILLET_HAS_STORED_STACK = FD_SKILLET_BE_CLASS.getMethod("hasStoredStack");
            FD_SKILLET_ADD_ITEM_TO_COOK = FD_SKILLET_BE_CLASS.getMethod("addItemToCook", ItemStack.class, playerClass);
            FD_SKILLET_IS_HEATED = FD_SKILLET_BE_CLASS.getMethod("isHeated");
            return true;
        } catch (Throwable ignored) {
            FD_SKILLET_BE_CLASS = null; FD_SKILLET_HAS_STORED_STACK = null;
            FD_SKILLET_ADD_ITEM_TO_COOK = null; FD_SKILLET_IS_HEATED = null;
            return false;
        }
    }

    static boolean skilletHasStoredStack(BlockEntity be) {
        if (be == null || !ensureSkilletReflection() || !FD_SKILLET_BE_CLASS.isInstance(be)) return false;
        try { Object v = FD_SKILLET_HAS_STORED_STACK.invoke(be); return v instanceof Boolean b && b; }
        catch (Throwable ignored) { return false; }
    }

    static int skilletAddItem(ServerLevel level, VillagerEntityMCA villager, BlockEntity be, ItemStack stack, BlockPos stationPos) {
        if (be == null || stack.isEmpty() || !ensureSkilletReflection() || !FD_SKILLET_BE_CLASS.isInstance(be)) return 0;
        try {
            Object player = level.getNearestPlayer(villager, 48.0d);
            Object value = FD_SKILLET_ADD_ITEM_TO_COOK.invoke(be, stack.copy(), player);
            if (value instanceof ItemStack remainder) return Math.max(0, stack.getCount() - remainder.getCount());
        } catch (Throwable ignored) {}
        return 0;
    }

    static boolean skilletIsHeated(BlockEntity be) {
        if (be == null || !ensureSkilletReflection() || !FD_SKILLET_BE_CLASS.isInstance(be)) return false;
        try { Object v = FD_SKILLET_IS_HEATED.invoke(be); return v instanceof Boolean b && b; }
        catch (Throwable ignored) { return false; }
    }


    private static int count(SimpleContainer inventory, Item item) {
        return com.aetherianartificer.townstead.work.station.StationInventoryOps.count(inventory, item);
    }

    private static boolean consume(SimpleContainer inventory, Item item, int needed) {
        return com.aetherianartificer.townstead.work.station.StationInventoryOps.consume(inventory, item, needed);
    }
}
