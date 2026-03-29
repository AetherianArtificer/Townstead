package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.mojang.authlib.GameProfile;
import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.WorkPathing;
import com.aetherianartificer.townstead.compat.farmersdelight.CookStationClaims;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.ProducerStationSessions;
import com.aetherianartificer.townstead.compat.farmersdelight.ProducerStationState;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.RecipeIngredient;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
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

public final class StationHandler {
    private StationHandler() {}
    private static final GameProfile TOWNSTEAD_COOK_PROFILE =
            new GameProfile(UUID.fromString("7d0d7ac4-9d5a-4afc-bcaa-7e6bb86a7a4d"), "[TownsteadCook]");

    // ── Cross-version capability helpers ──

    static @Nullable IItemHandler getItemHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return getItemHandler(be, level, pos, side);
    }

    static @Nullable IItemHandler getItemHandler(BlockEntity be, ServerLevel level, BlockPos pos, @Nullable Direction side) {
        //? if neoforge {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        //?} else if forge {
        /*if (be == null) return null;
        if (side != null) return be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        *///?}
    }

    private static boolean handlerHasUnexpectedContents(IItemHandler handler, @Nullable Set<Item> allowedPrestage, @Nullable Item allowedContainerPrestage) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            if (allowedContainerPrestage != null && i == FD_COOKING_POT_CONTAINER_SLOT && slot.is(allowedContainerPrestage)) continue;
            if (allowedPrestage != null && allowedPrestage.contains(slot.getItem())) continue;
            return true;
        }
        return false;
    }

    //? if >=1.21 {
    static ItemStack copyOne(ItemStack stack) { return stack.copyWithCount(1); }
    static ItemStack copyWithCount(ItemStack stack, int count) { return stack.copyWithCount(count); }
    static boolean isSameItemComponents(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameComponents(a, b); }
    //?} else {
    /*static ItemStack copyOne(ItemStack stack) { ItemStack c = stack.copy(); c.setCount(1); return c; }
    static ItemStack copyWithCount(ItemStack stack, int count) { ItemStack c = stack.copy(); c.setCount(count); return c; }
    static boolean isSameItemComponents(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameTags(a, b); }
    *///?}

    // ── Block IDs ──

    //? if >=1.21 {
    private static final ResourceLocation FD_CUTTING_BOARD = ResourceLocation.parse("farmersdelight:cutting_board");
    private static final ResourceLocation FD_COOKING_POT = ResourceLocation.parse("farmersdelight:cooking_pot");
    private static final ResourceLocation FD_SKILLET = ResourceLocation.parse("farmersdelight:skillet");
    private static final ResourceLocation FD_STOVE = ResourceLocation.parse("farmersdelight:stove");
    //?} else {
    /*private static final ResourceLocation FD_CUTTING_BOARD = new ResourceLocation("farmersdelight", "cutting_board");
    private static final ResourceLocation FD_COOKING_POT = new ResourceLocation("farmersdelight", "cooking_pot");
    private static final ResourceLocation FD_SKILLET = new ResourceLocation("farmersdelight", "skillet");
    private static final ResourceLocation FD_STOVE = new ResourceLocation("farmersdelight", "stove");
    *///?}
    static final int FD_COOKING_POT_CONTAINER_SLOT = 7;
    static final int FD_COOKING_POT_INGREDIENT_SLOT_COUNT = 6;
    //? if >=1.21 {
    static final TagKey<Item> KNIFE_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/knife"));
    static final TagKey<Item> KNIFE_TAG_C = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/knives"));
    static final TagKey<Item> KNIFE_TAG_FORGE = TagKey.create(Registries.ITEM, ResourceLocation.parse("forge:tools/knives"));
    static final TagKey<Item> KNIFE_TAG_FD = TagKey.create(Registries.ITEM, ResourceLocation.parse("farmersdelight:tools/knives"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_UPGRADED_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_upgraded"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_NETHER_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_nether"));
    //?} else {
    /*static final TagKey<Item> KNIFE_TAG = TagKey.create(Registries.ITEM, new ResourceLocation("c", "tools/knife"));
    static final TagKey<Item> KNIFE_TAG_C = TagKey.create(Registries.ITEM, new ResourceLocation("c", "tools/knives"));
    static final TagKey<Item> KNIFE_TAG_FORGE = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "tools/knives"));
    static final TagKey<Item> KNIFE_TAG_FD = TagKey.create(Registries.ITEM, new ResourceLocation("farmersdelight", "tools/knives"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_TAG =
            TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "compat/farmersdelight/kitchen_storage"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_UPGRADED_TAG =
            TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "compat/farmersdelight/kitchen_storage_upgraded"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_NETHER_TAG =
            TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "compat/farmersdelight/kitchen_storage_nether"));
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
    private static Class<?> FD_CUTTING_BOARD_BE_CLASS;
    private static Method FD_CUTTING_BOARD_ADD_ITEM;
    private static Method FD_CUTTING_BOARD_PROCESS;
    private static Method FD_CUTTING_BOARD_REMOVE_ITEM;

    // ── Station slot record ──

    public record StationSlot(BlockPos pos, StationType type, ResourceLocation blockId, int capacity) {}

    // ── Station type identification ──

    public static @Nullable StationType stationType(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.CAMPFIRES) && fireSurfaceBlocked(level, pos)) return null;
        return stationType(state);
    }

    public static @Nullable StationType stationType(BlockState state) {
        if (state.is(BlockTags.CAMPFIRES)) return StationType.FIRE_STATION;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_CUTTING_BOARD.equals(id)) return StationType.CUTTING_BOARD;
        if (FD_STOVE.equals(id)) return StationType.FIRE_STATION;
        if (FD_SKILLET.equals(id)) return StationType.FIRE_STATION;
        if (FD_COOKING_POT.equals(id)) return StationType.HOT_STATION;
        return null;
    }

    public static boolean isStation(ServerLevel level, BlockPos pos) {
        return stationType(level, pos) != null;
    }

    public static boolean isStation(BlockState state) {
        return stationType(state) != null;
    }

    // ── Station discovery ──

    public static List<StationSlot> discoverStations(
            ServerLevel level,
            VillagerEntityMCA villager,
            Set<Long> kitchenBounds,
            int searchRadius,
            int verticalRadius
    ) {
        List<StationSlot> discovered = new ArrayList<>();
        for (StationSlot slot : KitchenStationIndex.snapshot(level, kitchenBounds).stations()) {
            if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), slot.pos())) continue;
            if (findStandingPosition(level, villager, slot.pos()) == null) continue;
            discovered.add(slot);
        }
        return discovered;
    }

    private static void tryAddStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos pos,
            Map<Long, StationSlot> found
    ) {
        pos = canonicalStationAnchor(level, pos);
        long key = pos.asLong();
        if (found.containsKey(key)) return;
        StationType type = stationType(level, pos);
        if (type == null) return;
        if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), pos)) return;
        if (findStandingPosition(level, villager, pos.immutable()) == null) return;

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        int capacity = switch (type) {
            case FIRE_STATION -> surfaceDiscoveryCapacity(level, pos);
            case HOT_STATION -> 1;
            case CUTTING_BOARD -> 1;
        };
        if (capacity <= 0) return;
        found.put(key, new StationSlot(pos.immutable(), type, blockId, capacity));
    }

    // ── Standing position ──

    public static @Nullable BlockPos findStandingPosition(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        BlockPos best = WorkPathing.nearestStandCandidate(level, villager, anchor, null);
        if (best == null) {
            BlockPos current = villager.blockPosition();
            double stationDist = villager.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
            if (stationDist <= 9.0d) {
                BlockState below = level.getBlockState(current.below());
                if (WorkPathing.isSafeStandPosition(level, current)
                        && below.isFaceSturdy(level, current.below(), Direction.UP)
                        && !avoidStandingSurface(below)) {
                    best = current.immutable();
                }
            }
        }
        return best;
    }

    private static boolean avoidStandingSurface(BlockState surface) {
        if (isStation(surface)) return true;
        return surface.is(FD_KITCHEN_STORAGE_TAG)
                || surface.is(FD_KITCHEN_STORAGE_UPGRADED_TAG)
                || surface.is(FD_KITCHEN_STORAGE_NETHER_TAG)
                || surface.is(Blocks.CHEST)
                || surface.is(Blocks.TRAPPED_CHEST)
                || surface.is(Blocks.BARREL);
    }

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

    public static int surfaceDiscoveryCapacity(ServerLevel level, BlockPos pos) {
        if (pos == null) return 0;
        pos = canonicalStationAnchor(level, pos);
        BlockState state = level.getBlockState(pos);
        if (surfaceBlockedForCooking(level, pos, state)) return 0;
        if (state.is(BlockTags.CAMPFIRES)) return surfaceFreeSlotCount(level, pos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            return surfaceFreeSlotCount(level, pos);
        }
        if (FD_SKILLET.equals(id)) {
            return surfaceFreeSlotCount(level, pos);
        }
        return 0;
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
            IItemHandler handler = getItemHandler(level, pos, null);
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
            return campfire.getCookableRecipe(probe).isPresent();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (stoveBlockedAbove(be)) return false;
            if (stoveMatchingRecipe(be, probe).isPresent()) return true;
            return campfireRecipeForInput(level, probe).isPresent();
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                return false;
            }
            return campfireRecipeForInput(level, probe).isPresent();
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
            return campfire.getCookableRecipe(copyOne(probe)).isPresent();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (stoveBlockedAbove(be)) return false;
            if (stoveMatchingRecipe(be, copyOne(probe)).isPresent()) return true;
            return campfireRecipeForInput(level, copyOne(probe)).isPresent();
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                return false;
            }
            return campfireRecipeForInput(level, copyOne(probe)).isPresent();
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
                if (match.isEmpty()) break;
                int cookTime = match.get().value().getCookingTime();
                //?} else {
                /*Optional<CampfireCookingRecipe> match = campfire.getCookableRecipe(one);
                if (match.isEmpty()) break;
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
                    if (match.isEmpty()) break;
                    loaded = stoveAddItemAnySlot(be, one.copy(), match.get());
                } else if (FD_SKILLET.equals(id)) {
                    if (!skilletIsHeated(be)) break;
                    if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                            && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) break;
                    if (skilletHasStoredStack(be)) break;
                    if (campfireRecipeForInput(level, one).isEmpty()) break;
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

        ItemStack one = copyWithCount(stack, ingredientPerLoad);
        BlockState state = level.getBlockState(stationAnchor);
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (surfaceBlockedForCooking(level, stationAnchor, state)) return false;

        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            //? if >=1.21 {
            Optional<RecipeHolder<CampfireCookingRecipe>> match = campfire.getCookableRecipe(one);
            if (match.isEmpty()) return false;
            return campfire.placeFood(villager, one, match.get().value().getCookingTime());
            //?} else {
            /*Optional<CampfireCookingRecipe> match = campfire.getCookableRecipe(one);
            if (match.isEmpty()) return false;
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
            return match.isPresent() && stoveAddItemAnySlot(be, one, match.get());
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) return false;
            if (skilletHasStoredStack(be)) return false;
            if (campfireRecipeForInput(level, one).isEmpty()) return false;
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
        if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), pos)) return false;
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
                int slot = bestImpureWaterSlot(inv, bridge, impureFilter);
                if (slot < 0) break;
                ItemStack source = inv.getItem(slot);
                if (source.isEmpty()) break;
                ItemStack oneItem = copyOne(source);
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
                int slot = bestImpureWaterSlot(inv, bridge, impureFilter);
                if (slot < 0) break;
                ItemStack source = inv.getItem(slot);
                if (source.isEmpty()) break;
                ItemStack oneItem = copyOne(source);
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
            int slot = bestImpureWaterSlot(inv, bridge, impureFilter);
            if (slot < 0) return false;
            ItemStack source = inv.getItem(slot);
            if (source.isEmpty()) return false;
            ItemStack prototype = copyOne(source);
            int available = countMatchingImpure(inv, prototype, bridge);
            if (available <= 0) return false;
            int batch = Math.min(available, source.getMaxStackSize());
            int inserted = skilletAddItem(level, villager, be, copyWithCount(prototype, batch), pos);
            if (inserted <= 0) return false;
            loaded += consumeMatchingImpure(inv, prototype, bridge, inserted);
        }
        return loaded > 0;
    }

    static int bestImpureWaterSlot(
            SimpleContainer inv,
            ThirstCompatBridge bridge,
            java.util.function.Predicate<ItemStack> extraFilter
    ) {
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!extraFilter.test(stack)) continue;
            int score = impureWaterScore(stack, bridge);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestScore > 0 ? bestSlot : -1;
    }

    public static int impureWaterScore(ItemStack stack, ThirstCompatBridge bridge) {
        if (stack.isEmpty()) return 0;
        if (!bridge.itemRestoresThirst(stack) || !bridge.isDrink(stack)) return 0;
        if (!bridge.isPurityWaterContainer(stack)) return 0;
        int purity = Math.max(0, Math.min(3, bridge.purity(stack)));
        if (purity >= 3) return 0;
        int impurity = 3 - purity;
        return (impurity * 100) + Math.max(0, bridge.hydration(stack));
    }

    private static int countMatchingImpure(SimpleContainer inv, ItemStack prototype, ThirstCompatBridge bridge) {
        if (prototype.isEmpty()) return 0;
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isSameItemComponents(stack, prototype)) continue;
            if (impureWaterScore(stack, bridge) <= 0) continue;
            total += stack.getCount();
        }
        return total;
    }

    private static int consumeMatchingImpure(SimpleContainer inv, ItemStack prototype, ThirstCompatBridge bridge, int amount) {
        if (prototype.isEmpty() || amount <= 0) return 0;
        int consumed = 0;
        for (int i = 0; i < inv.getContainerSize() && consumed < amount; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isSameItemComponents(stack, prototype)) continue;
            if (impureWaterScore(stack, bridge) <= 0) continue;
            int move = Math.min(amount - consumed, stack.getCount());
            stack.shrink(move);
            consumed += move;
        }
        return consumed;
    }

    // ── Cutting board interaction ──

    public static boolean cuttingBoardProcess(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            ItemStack inputStack,
            ItemStack knifeStack
    ) {
        if (stationAnchor == null || inputStack.isEmpty()) return false;
        if (knifeStack.isEmpty()) return false;

        BlockState state = level.getBlockState(stationAnchor);
        ServerPlayer actor = cuttingBoardActor(level);
        if (actor != null) {
            ItemStack previousMain = actor.getMainHandItem().copy();
            ItemStack previousOff = actor.getOffhandItem().copy();
            try {
                actor.setItemInHand(InteractionHand.MAIN_HAND, knifeStack.copy());
                actor.setItemInHand(InteractionHand.OFF_HAND, inputStack.copy());
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stationAnchor), Direction.UP, stationAnchor, false);
                InteractionResult placed = invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.OFF_HAND, hit);
                if (!placed.consumesAction()) {
                    return false;
                }
                InteractionResult processed = invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.MAIN_HAND, hit);
                if (processed == InteractionResult.SUCCESS) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Fall through to the direct block-entity path below.
            } finally {
                actor.setItemInHand(InteractionHand.MAIN_HAND, previousMain);
                actor.setItemInHand(InteractionHand.OFF_HAND, previousOff);
            }
        }

        if (!ensureCuttingBoardReflection()) return false;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return false;
        try {
            Object placed = FD_CUTTING_BOARD_ADD_ITEM.invoke(be, inputStack);
            if (!(placed instanceof Boolean b && b)) {
                villager.getInventory().addItem(inputStack);
                return false;
            }
            Object processed = FD_CUTTING_BOARD_PROCESS.invoke(be, knifeStack, cuttingBoardActor(level));
            if (processed instanceof Boolean ok && ok) {
                return true;
            }
            try { FD_CUTTING_BOARD_REMOVE_ITEM.invoke(be); } catch (Throwable ignored) {}
            villager.getInventory().addItem(new ItemStack(inputStack.getItem(), 1));
        } catch (Throwable t) {
            villager.getInventory().addItem(new ItemStack(inputStack.getItem(), 1));
        }
        return false;
    }

    public static boolean placeCuttingBoardInput(
            ServerLevel level,
            BlockPos stationAnchor,
            ItemStack inputStack
    ) {
        if (stationAnchor == null || inputStack.isEmpty()) return false;
        BlockState state = level.getBlockState(stationAnchor);
        ServerPlayer actor = cuttingBoardActor(level);
        if (actor != null) {
            ItemStack previousMain = actor.getMainHandItem().copy();
            ItemStack previousOff = actor.getOffhandItem().copy();
            try {
                actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                actor.setItemInHand(InteractionHand.OFF_HAND, inputStack.copy());
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stationAnchor), Direction.UP, stationAnchor, false);
                InteractionResult placed = invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.OFF_HAND, hit);
                return placed.consumesAction();
            } catch (Throwable ignored) {
                // Fall through to BE path.
            } finally {
                actor.setItemInHand(InteractionHand.MAIN_HAND, previousMain);
                actor.setItemInHand(InteractionHand.OFF_HAND, previousOff);
            }
        }

        if (!ensureCuttingBoardReflection()) return false;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return false;
        try {
            Object placed = FD_CUTTING_BOARD_ADD_ITEM.invoke(be, inputStack);
            return placed instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean processCuttingBoardStoredItem(
            ServerLevel level,
            BlockPos stationAnchor,
            ItemStack knifeStack
    ) {
        if (stationAnchor == null || knifeStack.isEmpty()) return false;

        BlockState state = level.getBlockState(stationAnchor);
        ServerPlayer actor = cuttingBoardActor(level);
        if (actor != null) {
            ItemStack previousMain = actor.getMainHandItem().copy();
            ItemStack previousOff = actor.getOffhandItem().copy();
            try {
                actor.setItemInHand(InteractionHand.MAIN_HAND, knifeStack.copy());
                actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stationAnchor), Direction.UP, stationAnchor, false);
                InteractionResult processed = invokeCuttingBoardBlockUse(state, level, stationAnchor, actor, InteractionHand.MAIN_HAND, hit);
                return processed == InteractionResult.SUCCESS;
            } catch (Throwable ignored) {
                // Fall through to BE path.
            } finally {
                actor.setItemInHand(InteractionHand.MAIN_HAND, previousMain);
                actor.setItemInHand(InteractionHand.OFF_HAND, previousOff);
            }
        }

        if (!ensureCuttingBoardReflection()) return false;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return false;
        try {
            Object processed = FD_CUTTING_BOARD_PROCESS.invoke(be, knifeStack, cuttingBoardActor(level));
            return processed instanceof Boolean ok && ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static InteractionResult invokeCuttingBoardBlockUse(
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

    public static ItemStack collectCuttingBoardOutput(ServerLevel level, BlockPos stationAnchor) {
        if (stationAnchor == null) return ItemStack.EMPTY;
        if (!ensureCuttingBoardReflection()) return ItemStack.EMPTY;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return ItemStack.EMPTY;
        try {
            Object removed = FD_CUTTING_BOARD_REMOVE_ITEM.invoke(be);
            if (removed instanceof ItemStack stack && !stack.isEmpty()) {
                return stack;
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    private static @Nullable ServerPlayer cuttingBoardActor(ServerLevel level) {
        if (level == null) return null;
        try {
            return FakePlayerFactory.get(level, TOWNSTEAD_COOK_PROFILE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ── Station recipe support checks ──

    public static boolean stationSupportsRecipe(ServerLevel level, BlockPos pos, DiscoveredRecipe recipe) {
        if (recipe == null) return true;
        if (pos == null) return false;
        if (recipe.purification()) {
            return recipe.stationType() == StationType.FIRE_STATION && supportsPurificationAt(level, pos);
        }
        if (recipe.stationType() == StationType.FIRE_STATION) {
            if (isSurfaceFireStation(level, pos)) {
                return surfaceCanCookRecipeInput(level, pos, recipe);
            }
            return true;
        }
        if (recipe.stationType() == StationType.HOT_STATION) {
            ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (!FD_COOKING_POT.equals(stationId)) return false;
        }
        if (recipe.stationType() == StationType.CUTTING_BOARD) {
            return level.getBlockEntity(pos) != null;
        }
        return true;
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

    // ── Station content checks ──

    public static boolean stationHasContents(ServerLevel level, BlockPos pos, @Nullable DiscoveredRecipe recipe) {
        if (pos == null) return false;
        pos = canonicalStationAnchor(level, pos);
        ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (recipe != null && recipe.stationType() == StationType.FIRE_STATION && isSurfaceFireStation(level, pos)) {
            return !surfaceHasFreeSlot(level, pos);
        }
        Item allowedContainerPrestage = Items.AIR;
        if (FD_COOKING_POT.equals(stationId)
                && recipe != null
                && recipe.stationType() == StationType.HOT_STATION
                && recipe.containerItemId() != null
                && recipe.containerCount() > 0) {
            Item containerItem = BuiltInRegistries.ITEM.get(recipe.containerItemId());
            if (containerItem != Items.AIR) allowedContainerPrestage = containerItem;
        }
        Set<Item> allowedPrestage = null;
        if (recipe != null && recipe.stationType() == StationType.HOT_STATION) {
            allowedPrestage = new HashSet<>();
            for (RecipeIngredient ingredient : recipe.inputs()) {
                for (ResourceLocation id : ingredient.itemIds()) {
                    Item item = BuiltInRegistries.ITEM.get(id);
                    if (item != Items.AIR) allowedPrestage.add(item);
                }
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        StorageSearchContext searchContext = new StorageSearchContext(level);

        IItemHandler handler = preferredIngredientHandler(level, pos);
        if (handler != null) {
            if (handlerHasUnexpectedContents(handler, allowedPrestage, allowedContainerPrestage == Items.AIR ? null : allowedContainerPrestage)) return true;
            return false;
        }

        final Set<Item> allowedPrestageFinal = allowedPrestage;
        final Item allowedContainerPrestageFinal = allowedContainerPrestage;
        final boolean[] found = new boolean[1];
        searchContext.forEachUniqueItemHandler(pos, (side, uniqueHandler) -> {
            if (found[0]) return;
            if (handlerHasUnexpectedContents(uniqueHandler, allowedPrestageFinal, allowedContainerPrestageFinal == Items.AIR ? null : allowedContainerPrestageFinal)) {
                found[0] = true;
            }
        });
        if (found[0]) return true;

        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) continue;
                if (allowedContainerPrestage != Items.AIR && i == FD_COOKING_POT_CONTAINER_SLOT && slot.is(allowedContainerPrestage)) continue;
                if (allowedPrestage != null && allowedPrestage.contains(slot.getItem())) continue;
                return true;
            }
        }
        return false;
    }

    // ── Output extraction ──

    public static int extractFromStation(ServerLevel level, BlockPos pos, Item item, int amount) {
        if (amount <= 0 || pos == null) return 0;
        pos = canonicalStationAnchor(level, pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return 0;
        final int[] removedRef = new int[1];
        StorageSearchContext searchContext = new StorageSearchContext(level);
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            if (removedRef[0] >= amount) return;
            for (int i = 0; i < handler.getSlots() && removedRef[0] < amount; i++) {
                if (!handler.getStackInSlot(i).is(item)) continue;
                ItemStack extracted = handler.extractItem(i, amount - removedRef[0], false);
                removedRef[0] += extracted.getCount();
            }
        });
        int removed = removedRef[0];
        if (removed >= amount) return removed;
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize() && removed < amount; i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.is(item)) continue;
                int take = Math.min(amount - removed, slot.getCount());
                slot.shrink(take);
                removed += take;
                container.setChanged();
            }
        }
        if (removed > 0) {
            KitchenStationIndex.invalidate(level, pos);
        }
        return removed;
    }

    public static boolean canExtractFromStation(ServerLevel level, BlockPos pos, Item item, int amount) {
        if (amount <= 0 || pos == null || item == Items.AIR) return false;
        pos = canonicalStationAnchor(level, pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        int found = 0;
        StorageSearchContext searchContext = new StorageSearchContext(level);
        final int[] foundRef = new int[1];
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            if (foundRef[0] >= amount) return;
            for (int i = 0; i < handler.getSlots() && foundRef[0] < amount; i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (!slot.is(item)) continue;
                foundRef[0] += slot.getCount();
            }
        });
        found = foundRef[0];
        if (found >= amount) return true;
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize() && found < amount; i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.is(item)) continue;
                found += slot.getCount();
            }
        }
        return found >= amount;
    }

    // ── Station insertion ──

    public static ItemStack insertIntoStation(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || pos == null) return stack;
        pos = canonicalStationAnchor(level, pos);
        if (stack.is(Items.BOWL)) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (FD_COOKING_POT.equals(blockId)) {
                return insertIntoCookingPotContainerSlot(level, pos, stack, false);
            }
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return stack;

        ItemStack remainder = stack.copy();
        StorageSearchContext searchContext = new StorageSearchContext(level);
        IItemHandler handler = preferredIngredientHandler(level, pos);
        if (handler != null) {
            remainder = insertIntoHandler(handler, remainder, false);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        } else {
            final ItemStack[] remainderRef = new ItemStack[]{remainder};
            searchContext.forEachUniqueItemHandler(pos, (dir, uniqueHandler) -> {
                if (remainderRef[0].isEmpty()) return;
                remainderRef[0] = insertIntoHandler(uniqueHandler, remainderRef[0], false);
            });
            remainder = remainderRef[0];
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }

        if (!remainder.isEmpty() && be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (remainder.isEmpty()) break;
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && !isSameItemComponents(slot, remainder)) continue;
                if (!container.canPlaceItem(i, remainder)) continue;
                int limit = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize());
                int move = slot.isEmpty()
                        ? Math.min(limit, remainder.getCount())
                        : Math.min(limit - slot.getCount(), remainder.getCount());
                if (move <= 0) continue;
                if (slot.isEmpty()) container.setItem(i, copyWithCount(remainder, move));
                else slot.grow(move);
                remainder.shrink(move);
                container.setChanged();
            }
        }
        if (remainder.getCount() != stack.getCount()) {
            KitchenStationIndex.invalidate(level, pos);
        }
        return remainder;
    }

    public static ItemStack insertIntoCookingPotIngredients(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || pos == null) return stack;
        pos = canonicalStationAnchor(level, pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return insertIntoStation(level, pos, stack);

        ItemStack remainder = stack.copy();
        while (!remainder.isEmpty()) {
            ItemStack single = copyOne(remainder);
            if (!insertSingleIntoCookingPotIngredientSlot(level, pos, single)) break;
            remainder.shrink(1);
        }
        if (!remainder.isEmpty()) {
            remainder = insertIntoStation(level, pos, remainder);
        }
        return remainder;
    }

    public static boolean insertIntoCookingPotNextIngredientSlot(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || pos == null) return false;
        pos = canonicalStationAnchor(level, pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return false;

        final ItemStack stackCopy = stack.copy();
        IItemHandler preferred = preferredIngredientHandler(level, pos);
        if (preferred != null && insertIntoFirstEmptyPotIngredientSlot(preferred, stackCopy)) {
            KitchenStationIndex.invalidate(level, pos);
            return true;
        }
        StorageSearchContext searchContext = new StorageSearchContext(level);
        final boolean[] inserted = new boolean[1];
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            if (inserted[0]) return;
            inserted[0] = insertIntoFirstEmptyPotIngredientSlot(handler, stackCopy);
        });
        if (inserted[0]) {
            KitchenStationIndex.invalidate(level, pos);
        }
        return inserted[0];
    }

    private static boolean insertSingleIntoCookingPotIngredientSlot(ServerLevel level, BlockPos pos, ItemStack single) {
        if (single.isEmpty()) return true;
        if (single.getCount() != 1) single = copyOne(single);
        final ItemStack singleCopy = single;
        IItemHandler preferred = preferredIngredientHandler(level, pos);
        if (preferred != null && insertIntoFirstEmptyPotIngredientSlot(preferred, singleCopy)) {
            KitchenStationIndex.invalidate(level, pos);
            return true;
        }
        StorageSearchContext searchContext = new StorageSearchContext(level);
        final boolean[] inserted = new boolean[1];
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            if (inserted[0]) return;
            inserted[0] = insertIntoFirstEmptyPotIngredientSlot(handler, singleCopy);
        });
        if (inserted[0]) {
            KitchenStationIndex.invalidate(level, pos);
        }
        return inserted[0];
    }

    private static boolean insertIntoFirstEmptyPotIngredientSlot(IItemHandler handler, ItemStack stack) {
        if (handler == null || stack.isEmpty()) return false;
        int slots = Math.min(FD_COOKING_POT_INGREDIENT_SLOT_COUNT, handler.getSlots());
        for (int slot = 0; slot < slots; slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) continue;
            ItemStack rem = handler.insertItem(slot, stack.copy(), false);
            if (rem.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean stationHasAnyContents(ServerLevel level, BlockPos pos, @Nullable StationType stationType) {
        if (pos == null || stationType == null) return false;
        pos = canonicalStationAnchor(level, pos);
        if (stationType == StationType.FIRE_STATION) {
            return !surfaceHasFreeSlot(level, pos);
        }
        return stationHasContents(level, pos, null);
    }

    public static ProducerStationState classifyProducerStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos pos,
            @Nullable StationType stationType,
            @Nullable DiscoveredRecipe expectedRecipe,
            @Nullable ProducerStationSessions.SessionSnapshot session
    ) {
        if (level == null || villager == null || pos == null || stationType == null) return ProducerStationState.BLOCKED;
        if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), pos)) return ProducerStationState.BLOCKED;

        boolean hasContents = stationHasAnyContents(level, pos, stationType);
        boolean ownsSession = session != null && session.isOwner(villager.getUUID());

        if (stationType == StationType.HOT_STATION && expectedRecipe != null) {
            Item outputItem = BuiltInRegistries.ITEM.get(expectedRecipe.output());
            if (outputItem != Items.AIR
                    && countItemInStation(level, pos, outputItem) >= expectedRecipe.outputCount()
                    && canExtractFromStation(level, pos, outputItem, expectedRecipe.outputCount())) {
                return ProducerStationState.FINISHED_OUTPUT;
            }
        }
        if (stationType == StationType.HOT_STATION && stationHasCollectibleOutput(level, pos, ModRecipeRegistry.allOutputIds(level))) {
            return ProducerStationState.FINISHED_OUTPUT;
        }

        if (stationType == StationType.FIRE_STATION && expectedRecipe != null) {
            if (surfaceHasCollectibleOutput(level, pos, Set.of(expectedRecipe.output()))) {
                return ProducerStationState.FINISHED_OUTPUT;
            }
        }
        if (stationType == StationType.FIRE_STATION && surfaceHasCollectibleOutput(level, pos, ModRecipeRegistry.allOutputIds(level))) {
            return ProducerStationState.FINISHED_OUTPUT;
        }

        if (stationType == StationType.FIRE_STATION) {
            int freeSurfaceSlots = surfaceFreeSlotCount(level, pos);
            if (freeSurfaceSlots > 0) {
                if (ownsSession) return ProducerStationState.OWNED_STAGED;
                return ProducerStationState.EMPTY_READY;
            }
        }

        if (!hasContents) return ProducerStationState.EMPTY_READY;
        if (ownsSession) return ProducerStationState.OWNED_STAGED;
        if (expectedRecipe != null && stationType == StationType.HOT_STATION && cookingPotMatchesRecipe(level, pos, expectedRecipe)) {
            return ProducerStationState.COMPATIBLE_PARTIAL;
        }
        return ProducerStationState.FOREIGN_CONTENTS;
    }

    public static boolean cleanupForeignProducerStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos pos,
            @Nullable StationType stationType,
            Set<Long> storageBounds
    ) {
        if (level == null || villager == null || pos == null || stationType == null) return false;
        boolean movedAny = false;

        Set<ResourceLocation> outputIds = ModRecipeRegistry.allOutputIds(level);
        List<ItemStack> drops = collectSurfaceCookDrops(level, pos, outputIds);
        for (ItemStack drop : drops) {
            IngredientResolver.storeOutputInCookStorage(level, villager, drop, pos, storageBounds);
            if (!drop.isEmpty()) {
                ItemStack remainder = villager.getInventory().addItem(drop);
                if (!remainder.isEmpty()) {
                    villager.spawnAtLocation(remainder);
                }
            }
            movedAny = true;
        }

        if (stationType == StationType.HOT_STATION) {
            movedAny |= clearCookingPotContents(level, villager, pos);
        }
        return movedAny || !stationHasAnyContents(level, pos, stationType);
    }

    public static boolean surfaceHasCollectibleOutput(ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds) {
        if (pos == null || outputIds == null || outputIds.isEmpty()) return false;
        pos = canonicalStationAnchor(level, pos);
        AABB area = new AABB(pos).inflate(3.0, 2.0, 3.0);
        return !level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && outputIds.contains(id);
        }).isEmpty();
    }

    public static boolean stationHasCollectibleOutput(ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds) {
        if (pos == null || outputIds == null || outputIds.isEmpty()) return false;
        pos = canonicalStationAnchor(level, pos);
        for (ResourceLocation outputId : outputIds) {
            Item item = BuiltInRegistries.ITEM.get(outputId);
            if (item == Items.AIR) continue;
            if (countItemInStation(level, pos, item) <= 0) continue;
            if (canExtractFromStation(level, pos, item, 1)) return true;
        }
        return false;
    }

    public static ItemStack insertIntoCookingPotContainerSlot(ServerLevel level, BlockPos pos, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || pos == null) return stack;
        pos = canonicalStationAnchor(level, pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return stack;

        final ItemStack[] remainderRef = new ItemStack[]{stack.copy()};
        StorageSearchContext searchContext = new StorageSearchContext(level);
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            if (remainderRef[0].isEmpty()) return;
            if (FD_COOKING_POT_CONTAINER_SLOT >= handler.getSlots()) return;
            remainderRef[0] = handler.insertItem(FD_COOKING_POT_CONTAINER_SLOT, remainderRef[0], simulate);
        });
        ItemStack remainder = remainderRef[0];
        if (!simulate && remainder.getCount() != stack.getCount()) {
            KitchenStationIndex.invalidate(level, pos);
        }
        if (remainder.isEmpty()) return ItemStack.EMPTY;
        return remainder;
    }

    public static int cookingPotContainerItemCount(ServerLevel level, BlockPos pos, Item item) {
        if (pos == null || item == Items.AIR) return 0;
        pos = canonicalStationAnchor(level, pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return 0;
        final int[] bestRef = new int[1];
        StorageSearchContext searchContext = new StorageSearchContext(level);
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            if (FD_COOKING_POT_CONTAINER_SLOT >= handler.getSlots()) return;
            ItemStack slot = handler.getStackInSlot(FD_COOKING_POT_CONTAINER_SLOT);
            if (slot.is(item)) bestRef[0] = Math.max(bestRef[0], slot.getCount());
        });
        return bestRef[0];
    }

    public static boolean cookingPotMatchesRecipe(ServerLevel level, BlockPos pos, DiscoveredRecipe recipe) {
        if (level == null || pos == null || recipe == null || recipe.stationType() != StationType.HOT_STATION) return false;
        pos = canonicalStationAnchor(level, pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        Object source = recipe.source();
        if (source == null) return false;
        //? if >=1.21 {
        if (source instanceof RecipeHolder<?> holder) source = holder.value();
        //?}
        try {
            Method inventoryMethod = be.getClass().getMethod("getInventory");
            Object inventory = inventoryMethod.invoke(be);
            if (inventory == null) return false;
            Object wrapper = RecipeWrapper.class.getConstructors()[0].newInstance(inventory);
            for (Method method : source.getClass().getMethods()) {
                if (!method.getName().equals("matches") || method.getParameterCount() != 2) continue;
                try {
                    Object result = method.invoke(source, wrapper, level);
                    if (result instanceof Boolean bool) return bool;
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static String describeCookingPotInputs(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "";
        pos = canonicalStationAnchor(level, pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return "";
        IItemHandler input = preferredIngredientHandler(level, pos);
        if (input == null) return "";
        List<String> parts = new ArrayList<>();
        for (int slot = 0; slot < FD_COOKING_POT_INGREDIENT_SLOT_COUNT; slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            parts.add(stack.getHoverName().getString() + " x" + stack.getCount());
        }
        final String[] containerRef = new String[1];
        StorageSearchContext searchContext = new StorageSearchContext(level);
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            if (containerRef[0] != null) return;
            if (FD_COOKING_POT_CONTAINER_SLOT >= handler.getSlots()) return;
            ItemStack stack = handler.getStackInSlot(FD_COOKING_POT_CONTAINER_SLOT);
            if (stack.isEmpty()) return;
            containerRef[0] = "container: " + stack.getHoverName().getString() + " x" + stack.getCount();
        });
        if (containerRef[0] != null) parts.add(containerRef[0]);
        return String.join(", ", parts);
    }

    public static int countItemInStation(ServerLevel level, BlockPos pos, Item item) {
        if (pos == null || item == Items.AIR) return 0;
        pos = canonicalStationAnchor(level, pos);
        final int[] totalRef = new int[1];
        final boolean[] sawHandlerRef = new boolean[1];
        StorageSearchContext searchContext = new StorageSearchContext(level);
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            sawHandlerRef[0] = true;
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.is(item)) {
                    totalRef[0] += stack.getCount();
                }
            }
        });
        BlockEntity be = level.getBlockEntity(pos);
        if (!sawHandlerRef[0] && be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(item)) totalRef[0] += container.getItem(i).getCount();
            }
        }
        return totalRef[0];
    }

    public static List<ItemStack> extractMatchingStationStacks(ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds) {
        if (level == null || pos == null || outputIds == null || outputIds.isEmpty()) return List.of();
        pos = canonicalStationAnchor(level, pos);
        List<ItemStack> extracted = new ArrayList<>();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return extracted;

        StorageSearchContext searchContext = new StorageSearchContext(level);
        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (slot.isEmpty()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(slot.getItem());
                if (id == null || !outputIds.contains(id)) continue;
                ItemStack taken = handler.extractItem(i, slot.getCount(), false);
                if (!taken.isEmpty()) extracted.add(taken);
            }
        });

        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(slot.getItem());
                if (id == null || !outputIds.contains(id)) continue;
                extracted.add(slot.copy());
                container.setItem(i, ItemStack.EMPTY);
                container.setChanged();
            }
        }
        if (!extracted.isEmpty()) {
            KitchenStationIndex.invalidate(level, pos);
        }
        return extracted;
    }

    // ── Collect surface cook drops ──

    public static List<ItemStack> collectSurfaceCookDrops(ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds) {
        if (pos == null) return List.of();
        AABB area = new AABB(pos).inflate(3.0, 2.0, 3.0);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && outputIds.contains(id);
        });
        if (drops.isEmpty()) return List.of();
        List<ItemStack> collected = new ArrayList<>();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) continue;
            drop.discard();
            collected.add(stack);
        }
        return collected;
    }

    // ── Clear station contents ──

    public static boolean clearCookingPotContents(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (pos == null) return false;
        if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), pos)) return false;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return false;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        final boolean[] clearedAnyRef = new boolean[1];
        StorageSearchContext searchContext = new StorageSearchContext(level);

        searchContext.forEachUniqueItemHandler(pos, (dir, handler) -> {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).isEmpty()) continue;
                ItemStack extracted = handler.extractItem(i, handler.getStackInSlot(i).getCount(), false);
                if (extracted.isEmpty()) continue;
                clearedAnyRef[0] = true;
                ItemStack remainder = villager.getInventory().addItem(extracted);
                if (!remainder.isEmpty()) {
                    ItemEntity drop = new ItemEntity(
                            level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
                    drop.setPickUpDelay(0);
                    level.addFreshEntity(drop);
                }
            }
        });
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) continue;
                ItemStack taken = slot.copy();
                container.setItem(i, ItemStack.EMPTY);
                container.setChanged();
                clearedAnyRef[0] = true;
                ItemStack remainder = villager.getInventory().addItem(taken);
                if (!remainder.isEmpty()) {
                    ItemEntity drop = new ItemEntity(
                            level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
                    drop.setPickUpDelay(0);
                    level.addFreshEntity(drop);
                }
            }
        }

        // Reflection fallback: clear remaining items (e.g. bowl in container slot)
        // that IItemHandler and Container couldn't extract
        if (clearRemainingViaReflection(be, villager, level)) clearedAnyRef[0] = true;

        if (clearedAnyRef[0]) {
            KitchenStationIndex.invalidate(level, pos);
        }
        return clearedAnyRef[0];
    }

    private static boolean clearRemainingViaReflection(BlockEntity be, VillagerEntityMCA villager, ServerLevel level) {
        boolean cleared = false;
        try {
            for (Field field : be.getClass().getDeclaredFields()) {
                if (!IItemHandler.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object handler = field.get(be);
                if (handler == null) continue;
                // Use setStackInSlot via reflection (available on ItemStackHandler)
                Method setStack = null;
                try {
                    setStack = handler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                } catch (NoSuchMethodException ignored) { continue; }
                Method getSlots = handler.getClass().getMethod("getSlots");
                Method getStack = handler.getClass().getMethod("getStackInSlot", int.class);
                int slots = (int) getSlots.invoke(handler);
                for (int i = 0; i < slots; i++) {
                    ItemStack slot = (ItemStack) getStack.invoke(handler, i);
                    if (slot == null || slot.isEmpty()) continue;
                    ItemStack taken = slot.copy();
                    setStack.invoke(handler, i, ItemStack.EMPTY);
                    cleared = true;
                    ItemStack remainder = villager.getInventory().addItem(taken);
                    if (!remainder.isEmpty()) {
                        ItemEntity drop = new ItemEntity(
                                level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
                        drop.setPickUpDelay(0);
                        level.addFreshEntity(drop);
                    }
                }
                if (cleared) {
                    be.setChanged();
                    break;
                }
            }
        } catch (Throwable ignored) {}
        return cleared;
    }

    // ── Kitchen building/anchor helpers ──

    public static List<Building> kitchenBuildings(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return List.of();
        Optional<Building> assigned = FarmersDelightCookAssignment.assignedKitchen(level, villager);
        if (assigned.isPresent()) return List.of(assigned.get());
        Optional<Village> village = FarmersDelightCookAssignment.resolveVillage(villager);
        if (village.isEmpty()) return List.of();
        return village.get().getBuildings().values().stream()
                .filter(b -> FarmersDelightCookAssignment.isKitchenType(b.getType()))
                .toList();
    }

    public static List<BlockPos> kitchenAnchors(ServerLevel level, VillagerEntityMCA villager) {
        return kitchenBuildings(villager).stream()
                .map(Building::getCenter)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    // ── Kitchen bounds ──

    public static boolean isInKitchenWorkArea(Set<Long> kitchenBounds, BlockPos pos) {
        if (pos == null || kitchenBounds == null || kitchenBounds.isEmpty()) return true;
        if (kitchenBounds.contains(pos.asLong())) return true;
        int margin = 2;
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -margin; dz <= margin; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (kitchenBounds.contains(pos.offset(dx, dy, dz).asLong())) return true;
                }
            }
        }
        return false;
    }

    public static boolean isCookStorageCandidate(ServerLevel level, BlockPos pos, BlockEntity be) {
        BlockState state = level.getBlockState(pos);
        if (TownsteadConfig.isProtectedStorage(state)) return false;
        if (NearbyItemSources.isProcessingContainer(level, pos, be)) return false;
        if (state.is(FD_KITCHEN_STORAGE_TAG)
                || state.is(FD_KITCHEN_STORAGE_UPGRADED_TAG)
                || state.is(FD_KITCHEN_STORAGE_NETHER_TAG)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL)) {
            return true;
        }
        if (be instanceof Container container) {
            return container.getContainerSize() > 0;
        }
        if (getItemHandler(be, level, pos, null) != null) {
            return true;
        }
        for (Direction dir : Direction.values()) {
            if (getItemHandler(be, level, pos, dir) != null) {
                return true;
            }
        }
        return false;
    }

    // ── Knife helpers ──

    public static boolean isKnifeStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(KNIFE_TAG) || stack.is(KNIFE_TAG_C) || stack.is(KNIFE_TAG_FORGE) || stack.is(KNIFE_TAG_FD)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_knife") || path.contains("/knife") || path.contains("knife");
    }

    public static boolean hasKnife(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isKnifeStack(inv.getItem(i))) return true;
        }
        return false;
    }

    public static String describeKnifeLikeInventory(SimpleContainer inv) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = id == null ? stack.getItem().toString() : id.toString();
            String path = id == null ? "" : id.getPath();
            if (isKnifeStack(stack) || path.contains("knife") || path.contains("blade") || path.contains("cleaver")) {
                parts.add(itemId + " x" + stack.getCount() + " match=" + isKnifeStack(stack));
            }
        }
        return parts.isEmpty() ? "<none>" : String.join(", ", parts);
    }

    // ── Inventory helpers ──

    public static int count(SimpleContainer inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) total += inv.getItem(i).getCount();
        }
        return total;
    }

    public static boolean consume(SimpleContainer inv, Item item, int needed) {
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

    public static int removeUpTo(SimpleContainer inv, Item item, int maxCount) {
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

    // ── Handler helpers ──

    public static ItemStack insertIntoHandler(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int i = 0; i < handler.getSlots(); i++) {
            remainder = handler.insertItem(i, remainder, simulate);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }

    public static IItemHandler preferredIngredientHandler(ServerLevel level, BlockPos pos) {
        if (pos == null) return null;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (FD_COOKING_POT.equals(id) || FD_SKILLET.equals(id) || FD_CUTTING_BOARD.equals(id)) {
            IItemHandler up = getItemHandler(level, pos, Direction.UP);
            if (up != null) return up;
        }
        return null;
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

    static boolean fireSurfaceBlocked(ServerLevel level, BlockPos firePos) {
        BlockState above = level.getBlockState(firePos.above());
        ResourceLocation aboveId = BuiltInRegistries.BLOCK.getKey(above.getBlock());
        if (FD_COOKING_POT.equals(aboveId) || FD_SKILLET.equals(aboveId)) return true;
        return !above.canBeReplaced();
    }

    static boolean surfaceBlockedForCooking(ServerLevel level, BlockPos pos, BlockState state) {
        if (pos == null || state == null) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_SKILLET.equals(id)) return false;
        if (state.is(BlockTags.CAMPFIRES) || FD_STOVE.equals(id)) {
            return fireSurfaceBlocked(level, pos);
        }
        return false;
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

    // ── Cutting board reflection ──

    private static boolean ensureCuttingBoardReflection() {
        if (FD_CUTTING_BOARD_BE_CLASS != null && FD_CUTTING_BOARD_ADD_ITEM != null
                && FD_CUTTING_BOARD_PROCESS != null && FD_CUTTING_BOARD_REMOVE_ITEM != null) {
            return true;
        }
        try {
            Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
            FD_CUTTING_BOARD_BE_CLASS = Class.forName("vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity");
            FD_CUTTING_BOARD_ADD_ITEM = FD_CUTTING_BOARD_BE_CLASS.getMethod("addItem", ItemStack.class);
            FD_CUTTING_BOARD_PROCESS = FD_CUTTING_BOARD_BE_CLASS.getMethod("processStoredItemUsingTool", ItemStack.class, playerClass);
            FD_CUTTING_BOARD_REMOVE_ITEM = FD_CUTTING_BOARD_BE_CLASS.getMethod("removeItem");
            return true;
        } catch (Throwable ignored) {
            FD_CUTTING_BOARD_BE_CLASS = null; FD_CUTTING_BOARD_ADD_ITEM = null;
            FD_CUTTING_BOARD_PROCESS = null; FD_CUTTING_BOARD_REMOVE_ITEM = null;
            return false;
        }
    }
}
