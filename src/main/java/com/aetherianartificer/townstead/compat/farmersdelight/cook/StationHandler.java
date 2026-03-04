package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.CookStationClaims;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.RecipeIngredient;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public final class StationHandler {
    private StationHandler() {}

    // ── Block IDs ──

    private static final ResourceLocation FD_CUTTING_BOARD = ResourceLocation.parse("farmersdelight:cutting_board");
    private static final ResourceLocation FD_COOKING_POT = ResourceLocation.parse("farmersdelight:cooking_pot");
    private static final ResourceLocation FD_SKILLET = ResourceLocation.parse("farmersdelight:skillet");
    private static final ResourceLocation FD_STOVE = ResourceLocation.parse("farmersdelight:stove");
    static final int FD_COOKING_POT_CONTAINER_SLOT = 7;
    static final int FD_COOKING_POT_INGREDIENT_SLOT_COUNT = 6;
    static final TagKey<Item> KNIFE_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/knife"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_UPGRADED_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_upgraded"));
    static final TagKey<net.minecraft.world.level.block.Block> FD_KITCHEN_STORAGE_NETHER_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_nether"));

    // ── Reflection fields ──

    private static Class<?> FD_STOVE_BE_CLASS;
    private static Method FD_STOVE_GET_NEXT_EMPTY_SLOT;
    private static Method FD_STOVE_GET_MATCHING_RECIPE;
    private static Method FD_STOVE_ADD_ITEM;
    private static Method FD_STOVE_IS_BLOCKED_ABOVE;
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
        Map<Long, StationSlot> found = new LinkedHashMap<>();
        for (Building building : kitchenBuildings(villager)) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                if (!isInKitchenWorkArea(kitchenBounds, pos)) continue;
                tryAddStation(level, villager, pos, found);
            }
        }
        for (BlockPos anchor : kitchenAnchors(level, villager)) {
            for (BlockPos pos : BlockPos.betweenClosed(
                    anchor.offset(-searchRadius, -verticalRadius, -searchRadius),
                    anchor.offset(searchRadius, verticalRadius, searchRadius))) {
                if (!isInKitchenWorkArea(kitchenBounds, pos)) continue;
                tryAddStation(level, villager, pos, found);
            }
        }
        return new ArrayList<>(found.values());
    }

    private static void tryAddStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos pos,
            Map<Long, StationSlot> found
    ) {
        long key = pos.asLong();
        if (found.containsKey(key)) return;
        StationType type = stationType(level, pos);
        if (type == null) return;
        if (CookStationClaims.isClaimedByOther(level, villager.getUUID(), pos)) return;
        if (findStandingPosition(level, villager, pos.immutable()) == null) return;

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        int capacity = switch (type) {
            case FIRE_STATION -> surfaceFreeSlotCount(level, pos);
            case HOT_STATION -> 1;
            case CUTTING_BOARD -> 1;
        };
        if (capacity <= 0) return;
        found.put(key, new StationSlot(pos.immutable(), type, blockId, capacity));
    }

    // ── Standing position ──

    public static @Nullable BlockPos findStandingPosition(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int[] verticalChoices = {-1, 0, 1};
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                int manhattan = Math.abs(dx) + Math.abs(dz);
                if (manhattan > 3) continue;
                for (int yOffset : verticalChoices) {
                    BlockPos c = anchor.offset(dx, yOffset, dz);
                    if (!level.getBlockState(c).isAir() || !level.getBlockState(c.above()).isAir()) continue;
                    BlockPos belowPos = c.below();
                    BlockState belowState = level.getBlockState(belowPos);
                    if (!belowState.isFaceSturdy(level, belowPos, Direction.UP)) continue;
                    if (avoidStandingSurface(belowState)) continue;
                    double dist = villager.distanceToSqr(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
                    if (yOffset == -1) dist -= 2.0d;
                    if (manhattan > 1) dist += (manhattan - 1) * 1.5d;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = c.immutable();
                    }
                }
            }
        }
        if (best == null) {
            BlockPos current = villager.blockPosition();
            double stationDist = villager.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
            if (stationDist <= 9.0d) {
                BlockState self = level.getBlockState(current);
                BlockState above = level.getBlockState(current.above());
                BlockState below = level.getBlockState(current.below());
                if (self.isAir() && above.isAir()
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

    public static boolean surfaceHasFreeSlot(ServerLevel level, BlockPos pos) {
        return surfaceFreeSlotCount(level, pos) > 0;
    }

    public static int surfaceFreeSlotCount(ServerLevel level, BlockPos pos) {
        if (pos == null) return 0;
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
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null) {
                Integer slot = stoveNextEmptySlot(be);
                return (slot != null && slot >= 0) ? 1 : 0;
            }
            int free = 0;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).isEmpty()) free++;
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
        BlockState state = level.getBlockState(pos);
        if (surfaceBlockedForCooking(level, pos, state)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        if (state.is(BlockTags.CAMPFIRES) && be instanceof CampfireBlockEntity campfire) {
            return campfire.getCookableRecipe(probe.copyWithCount(1)).isPresent();
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (FD_STOVE.equals(id)) {
            if (stoveBlockedAbove(be)) return false;
            if (stoveMatchingRecipe(be, probe.copyWithCount(1)).isPresent()) return true;
            return campfireRecipeForInput(level, probe.copyWithCount(1)).isPresent();
        }
        if (FD_SKILLET.equals(id)) {
            if (!skilletIsHeated(be)) return false;
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) {
                return false;
            }
            return campfireRecipeForInput(level, probe.copyWithCount(1)).isPresent();
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
                Optional<RecipeHolder<CampfireCookingRecipe>> match = campfire.getCookableRecipe(one);
                if (match.isEmpty()) break;
                int cookTime = match.get().value().getCookingTime();
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
                    Integer slot = stoveNextEmptySlot(be);
                    if (slot == null || slot < 0) break;
                    loaded = stoveAddItem(be, one.copy(), match.get(), slot);
                } else if (FD_SKILLET.equals(id)) {
                    if (!skilletIsHeated(be)) break;
                    if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                            && Boolean.TRUE.equals(state.getValue(BlockStateProperties.WATERLOGGED))) break;
                    if (skilletHasStoredStack(be)) break;
                    if (campfireRecipeForInput(level, one).isEmpty()) break;
                    int skilletBatch = Math.min(count(inv, inputItem), inputItem.getDefaultMaxStackSize());
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

    // ── Purification loading ──

    public static boolean loadPurificationFireStation(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos pos,
            ThirstCompatBridge bridge
    ) {
        if (pos == null) return false;
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
                ItemStack oneItem = source.copyWithCount(1);
                // Try recipe match for cook time, fall back to synthetic cook time
                int cookTime = campfire.getCookableRecipe(oneItem)
                        .map(h -> h.value().getCookingTime())
                        .orElse(PURIFICATION_COOK_TIME);
                if (!campfire.placeFood(villager, oneItem, cookTime)) break;
                source.shrink(1);
                loaded++;
            }
        } else if (FD_STOVE.equals(stationId)) {
            if (stoveBlockedAbove(be)) return false;
            while (true) {
                Integer slotIndex = stoveNextEmptySlot(be);
                if (slotIndex == null || slotIndex < 0) break;
                int slot = bestImpureWaterSlot(inv, bridge, impureFilter);
                if (slot < 0) break;
                ItemStack source = inv.getItem(slot);
                if (source.isEmpty()) break;
                ItemStack oneItem = source.copyWithCount(1);
                // Try stove recipe, then campfire recipe fallback
                Optional<?> match = stoveMatchingRecipe(be, oneItem);
                if (match.isEmpty()) match = campfireRecipeForInput(level, oneItem);
                if (match.isEmpty()) {
                    // No recipe at all — stoveAddItem requires one, so skip stove for purification
                    break;
                }
                if (!stoveAddItem(be, oneItem, match.get(), slotIndex)) break;
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
            ItemStack prototype = source.copyWithCount(1);
            int available = countMatchingImpure(inv, prototype, bridge);
            if (available <= 0) return false;
            int batch = Math.min(available, source.getMaxStackSize());
            int inserted = skilletAddItem(level, villager, be, prototype.copyWithCount(batch), pos);
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
            if (!ItemStack.isSameItemSameComponents(stack, prototype)) continue;
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
            if (!ItemStack.isSameItemSameComponents(stack, prototype)) continue;
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
        if (!ensureCuttingBoardReflection()) return false;
        BlockEntity be = level.getBlockEntity(stationAnchor);
        if (be == null || !FD_CUTTING_BOARD_BE_CLASS.isInstance(be)) return false;
        if (knifeStack.isEmpty()) return false;

        try {
            Object placed = FD_CUTTING_BOARD_ADD_ITEM.invoke(be, inputStack);
            if (!(placed instanceof Boolean b && b)) {
                villager.getInventory().addItem(inputStack);
                return false;
            }
            Object processed = FD_CUTTING_BOARD_PROCESS.invoke(be, knifeStack, (Object) null);
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

    // ── Station recipe support checks ──

    public static boolean stationSupportsRecipe(ServerLevel level, BlockPos pos, DiscoveredRecipe recipe) {
        if (recipe == null) return true;
        if (pos == null) return false;
        if (recipe.purification()) {
            return recipe.stationType() == StationType.FIRE_STATION && isSurfaceFireStation(level, pos);
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

    // ── Station content checks ──

    public static boolean stationHasContents(ServerLevel level, BlockPos pos, @Nullable DiscoveredRecipe recipe) {
        if (pos == null) return false;
        ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (recipe != null && recipe.stationType() == StationType.FIRE_STATION && isSurfaceFireStation(level, pos)) {
            return !surfaceHasFreeSlot(level, pos);
        }
        boolean allowPotBowlPrestage =
                FD_COOKING_POT.equals(stationId)
                        && recipe != null
                        && recipe.stationType() == StationType.HOT_STATION
                        && recipe.bowlsRequired() > 0;
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

        IItemHandler handler = preferredIngredientHandler(level, pos);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack slot = handler.getStackInSlot(i);
                if (slot.isEmpty()) continue;
                if (allowPotBowlPrestage && i == FD_COOKING_POT_CONTAINER_SLOT && slot.is(Items.BOWL)) continue;
                if (allowedPrestage != null && allowedPrestage.contains(slot.getItem())) continue;
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
                if (allowedPrestage != null && allowedPrestage.contains(slot.getItem())) continue;
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
                if (allowedPrestage != null && allowedPrestage.contains(slot.getItem())) continue;
                return true;
            }
        }

        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) continue;
                if (allowPotBowlPrestage && i == FD_COOKING_POT_CONTAINER_SLOT && slot.is(Items.BOWL)) continue;
                if (allowedPrestage != null && allowedPrestage.contains(slot.getItem())) continue;
                return true;
            }
        }
        return false;
    }

    // ── Output extraction ──

    public static int extractFromStation(ServerLevel level, BlockPos pos, Item item, int amount) {
        if (amount <= 0 || pos == null) return 0;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return 0;
        int removed = 0;
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots() && removed < amount; i++) {
                if (!handler.getStackInSlot(i).is(item)) continue;
                ItemStack extracted = handler.extractItem(i, amount - removed, false);
                removed += extracted.getCount();
            }
            if (removed >= amount) return removed;
        }
        for (Direction dir : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            for (int i = 0; i < handler.getSlots() && removed < amount; i++) {
                if (!handler.getStackInSlot(i).is(item)) continue;
                ItemStack extracted = handler.extractItem(i, amount - removed, false);
                removed += extracted.getCount();
            }
            if (removed >= amount) return removed;
        }
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
        return removed;
    }

    // ── Station insertion ──

    public static ItemStack insertIntoStation(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || pos == null) return stack;
        if (stack.is(Items.BOWL)) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (FD_COOKING_POT.equals(blockId)) {
                return insertIntoCookingPotContainerSlot(level, pos, stack, false);
            }
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return stack;

        ItemStack remainder = stack.copy();
        IItemHandler handler = preferredIngredientHandler(level, pos);
        if (handler != null) {
            remainder = insertIntoHandler(handler, remainder, false);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        } else {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler != null) {
                remainder = insertIntoHandler(handler, remainder, false);
                if (remainder.isEmpty()) return ItemStack.EMPTY;
            }
            for (Direction dir : Direction.values()) {
                if (remainder.isEmpty()) return ItemStack.EMPTY;
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
                if (handler == null) continue;
                remainder = insertIntoHandler(handler, remainder, false);
            }
        }

        if (!remainder.isEmpty() && be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (remainder.isEmpty()) break;
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && !ItemStack.isSameItemSameComponents(slot, remainder)) continue;
                if (!container.canPlaceItem(i, remainder)) continue;
                int limit = Math.min(container.getMaxStackSize(), remainder.getMaxStackSize());
                int move = slot.isEmpty()
                        ? Math.min(limit, remainder.getCount())
                        : Math.min(limit - slot.getCount(), remainder.getCount());
                if (move <= 0) continue;
                if (slot.isEmpty()) container.setItem(i, remainder.copyWithCount(move));
                else slot.grow(move);
                remainder.shrink(move);
                container.setChanged();
            }
        }
        return remainder;
    }

    public static ItemStack insertIntoCookingPotIngredients(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || pos == null) return stack;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return insertIntoStation(level, pos, stack);

        ItemStack remainder = stack.copy();
        while (!remainder.isEmpty()) {
            ItemStack single = remainder.copyWithCount(1);
            if (!insertSingleIntoCookingPotIngredientSlot(level, pos, single)) break;
            remainder.shrink(1);
        }
        if (!remainder.isEmpty()) {
            remainder = insertIntoStation(level, pos, remainder);
        }
        return remainder;
    }

    private static boolean insertSingleIntoCookingPotIngredientSlot(ServerLevel level, BlockPos pos, ItemStack single) {
        if (single.isEmpty()) return true;
        if (single.getCount() != 1) single = single.copyWithCount(1);
        for (Direction dir : Direction.values()) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            int slots = Math.min(FD_COOKING_POT_INGREDIENT_SLOT_COUNT, handler.getSlots());
            for (int slot = 0; slot < slots; slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) continue;
                ItemStack rem = handler.insertItem(slot, single.copy(), false);
                if (rem.isEmpty()) return true;
            }
        }
        return false;
    }

    public static ItemStack insertIntoCookingPotContainerSlot(ServerLevel level, BlockPos pos, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || pos == null) return stack;
        if (!stack.is(Items.BOWL)) return stack;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return stack;

        ItemStack remainder = stack.copy();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            if (FD_COOKING_POT_CONTAINER_SLOT >= handler.getSlots()) continue;
            remainder = handler.insertItem(FD_COOKING_POT_CONTAINER_SLOT, remainder, simulate);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }

    public static int cookingPotContainerBowlCount(ServerLevel level, BlockPos pos) {
        if (pos == null) return 0;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!FD_COOKING_POT.equals(blockId)) return 0;
        int best = 0;
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) continue;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            if (FD_COOKING_POT_CONTAINER_SLOT >= handler.getSlots()) continue;
            ItemStack slot = handler.getStackInSlot(FD_COOKING_POT_CONTAINER_SLOT);
            if (slot.is(Items.BOWL)) best = Math.max(best, slot.getCount());
        }
        return best;
    }

    public static int countItemInStation(ServerLevel level, BlockPos pos, Item item) {
        if (pos == null || item == Items.AIR) return 0;
        int total = 0;
        IItemHandler handler = preferredIngredientHandler(level, pos);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).is(item)) total += handler.getStackInSlot(i).getCount();
            }
            return total;
        }
        handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).is(item)) total += handler.getStackInSlot(i).getCount();
            }
            return total;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(item)) total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    // ── Collect surface cook drops ──

    public static List<ItemStack> collectSurfaceCookDrops(ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds) {
        if (pos == null) return List.of();
        AABB area = new AABB(pos).inflate(1.5, 1.0, 1.5);
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
        boolean clearedAny = false;

        Direction[] directions = new Direction[Direction.values().length + 1];
        directions[0] = null;
        System.arraycopy(Direction.values(), 0, directions, 1, Direction.values().length);
        Set<Integer> handlerIds = new HashSet<>();
        for (Direction dir : directions) {
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, dir);
            if (handler == null) continue;
            if (!handlerIds.add(System.identityHashCode(handler))) continue;
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).isEmpty()) continue;
                ItemStack extracted = handler.extractItem(i, handler.getStackInSlot(i).getCount(), false);
                if (extracted.isEmpty()) continue;
                clearedAny = true;
                ItemStack remainder = villager.getInventory().addItem(extracted);
                if (!remainder.isEmpty()) {
                    ItemEntity drop = new ItemEntity(
                            level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
                    drop.setPickUpDelay(0);
                    level.addFreshEntity(drop);
                }
            }
        }
        if (be instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack slot = container.getItem(i);
                if (slot.isEmpty()) continue;
                ItemStack taken = slot.copy();
                container.setItem(i, ItemStack.EMPTY);
                container.setChanged();
                clearedAny = true;
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
        if (clearRemainingViaReflection(be, villager, level)) clearedAny = true;

        return clearedAny;
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
        return state.is(FD_KITCHEN_STORAGE_TAG)
                || state.is(FD_KITCHEN_STORAGE_UPGRADED_TAG)
                || state.is(FD_KITCHEN_STORAGE_NETHER_TAG)
                || state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.BARREL);
    }

    // ── Knife helpers ──

    public static boolean isKnifeStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(KNIFE_TAG)) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return false;
        return id.getPath().contains("knife");
    }

    public static boolean hasKnife(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isKnifeStack(inv.getItem(i))) return true;
        }
        return false;
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
            IItemHandler up = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            if (up != null) return up;
        }
        return null;
    }

    public static Optional<RecipeHolder<CampfireCookingRecipe>> campfireRecipeForInput(ServerLevel level, ItemStack stack) {
        if (level == null || stack == null || stack.isEmpty()) return Optional.empty();
        for (RecipeHolder<CampfireCookingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            CampfireCookingRecipe recipe = holder.value();
            if (recipe.getIngredients().isEmpty()) continue;
            if (recipe.getIngredients().get(0).test(stack)) return Optional.of(holder);
        }
        return Optional.empty();
    }

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

    // ── Stove reflection ──

    private static boolean ensureStoveReflection() {
        if (FD_STOVE_BE_CLASS != null && FD_STOVE_GET_NEXT_EMPTY_SLOT != null
                && FD_STOVE_GET_MATCHING_RECIPE != null && FD_STOVE_ADD_ITEM != null
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
            FD_STOVE_BE_CLASS = null; FD_STOVE_GET_NEXT_EMPTY_SLOT = null;
            FD_STOVE_GET_MATCHING_RECIPE = null; FD_STOVE_ADD_ITEM = null;
            FD_STOVE_IS_BLOCKED_ABOVE = null;
            return false;
        }
    }

    static Integer stoveNextEmptySlot(BlockEntity be) {
        if (be == null || !ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return null;
        try { Object v = FD_STOVE_GET_NEXT_EMPTY_SLOT.invoke(be); return v instanceof Integer i ? i : null; }
        catch (Throwable ignored) { return null; }
    }

    static Optional<?> stoveMatchingRecipe(BlockEntity be, ItemStack stack) {
        if (be == null || stack.isEmpty() || !ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return Optional.empty();
        try { Object v = FD_STOVE_GET_MATCHING_RECIPE.invoke(be, stack.copy()); if (v instanceof Optional<?> o) return o; }
        catch (Throwable ignored) {}
        return Optional.empty();
    }

    static boolean stoveAddItem(BlockEntity be, ItemStack stack, Object recipeHolder, int slot) {
        if (be == null || stack.isEmpty() || recipeHolder == null) return false;
        if (!ensureStoveReflection() || !FD_STOVE_BE_CLASS.isInstance(be)) return false;
        try { Object v = FD_STOVE_ADD_ITEM.invoke(be, stack, recipeHolder, slot); return v instanceof Boolean b && b; }
        catch (Throwable ignored) { return false; }
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
