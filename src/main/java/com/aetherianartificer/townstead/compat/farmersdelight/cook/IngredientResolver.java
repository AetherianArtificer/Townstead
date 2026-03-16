package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.CookStationClaims;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.RecipeIngredient;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if neoforge {
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
*///?}

import javax.annotation.Nullable;
import java.util.*;

public final class IngredientResolver {
    private IngredientResolver() {}

    //? if >=1.21 {
    private static final ResourceLocation MINECRAFT_BOWL = ResourceLocation.parse("minecraft:bowl");
    private static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "impure_water_container");
    //?} else {
    /*private static final ResourceLocation MINECRAFT_BOWL = new ResourceLocation("minecraft", "bowl");
    private static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
            new ResourceLocation(Townstead.MOD_ID, "impure_water_container");
    *///?}

    // ── Supply snapshot ──

    public static Map<ResourceLocation, Integer> buildSupplySnapshot(
            ServerLevel level,
            VillagerEntityMCA villager,
            Set<ResourceLocation> trackedIds,
            Set<Long> kitchenBounds
    ) {
        Map<ResourceLocation, Integer> supply = new HashMap<>();
        if (trackedIds.isEmpty()) return supply;
        boolean trackImpureWater = trackedIds.contains(TOWNSTEAD_IMPURE_WATER_INPUT);
        ThirstCompatBridge thirstBridge = trackImpureWater ? ThirstBridgeResolver.get() : null;

        // Inventory
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (trackImpureWater && thirstBridge != null
                    && StationHandler.impureWaterScore(stack, thirstBridge) > 0) {
                supply.merge(TOWNSTEAD_IMPURE_WATER_INPUT, stack.getCount(), Integer::sum);
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null || !trackedIds.contains(itemId)) continue;
            supply.merge(itemId, stack.getCount(), Integer::sum);
        }

        // Kitchen containers
        Optional<Village> villageOpt = FarmersDelightCookAssignment.resolveVillage(villager);
        if (villageOpt.isEmpty()) return supply;
        Village village = villageOpt.get();

        Set<Long> visited = new HashSet<>();
        for (Building building : village.getBuildings().values()) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                long key = pos.asLong();
                if (!visited.add(key)) continue;
                if (!StationHandler.isInKitchenWorkArea(kitchenBounds, pos)) continue;
                if (!village.isWithinBorder(pos, 0)) continue;
                if (TownsteadConfig.isProtectedStorage(level.getBlockState(pos))) continue;
                BlockEntity be = level.getBlockEntity(pos);
                if (NearbyItemSources.isProcessingContainer(level, pos, be)) continue;
                if (be == null) continue;

                if (be instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        ItemStack stack = container.getItem(i);
                        if (stack.isEmpty()) continue;
                        if (trackImpureWater && thirstBridge != null
                                && StationHandler.impureWaterScore(stack, thirstBridge) > 0) {
                            supply.merge(TOWNSTEAD_IMPURE_WATER_INPUT, stack.getCount(), Integer::sum);
                        }
                        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                        if (itemId == null || !trackedIds.contains(itemId)) continue;
                        supply.merge(itemId, stack.getCount(), Integer::sum);
                    }
                    continue;
                }

                IItemHandler handler = StationHandler.getItemHandler(be, level, pos, null);
                if (handler == null) continue;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    if (trackImpureWater && thirstBridge != null
                            && StationHandler.impureWaterScore(stack, thirstBridge) > 0) {
                        supply.merge(TOWNSTEAD_IMPURE_WATER_INPUT, stack.getCount(), Integer::sum);
                    }
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (itemId == null || !trackedIds.contains(itemId)) continue;
                    supply.merge(itemId, stack.getCount(), Integer::sum);
                }
            }
        }
        return supply;
    }

    // ── Can fulfill check ──

    public static boolean canFulfill(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds
    ) {
        BlockPos center = stationPos != null ? stationPos : villager.blockPosition();
        if (recipe.purification()) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null || !TownsteadConfig.isCookWaterPurificationEnabled() || !bridge.supportsPurification()) return false;
            if (!StationHandler.isSurfaceFireStation(level, center)) return false;
            if (!StationHandler.surfaceHasFreeSlot(level, center)) return false;
            // For purification, only check if the item is impure water — don't require a
            // campfire cooking recipe, since TWP handles purification via its own event system
            // and the station validity is already confirmed above.
            java.util.function.Predicate<ItemStack> matcher = stack ->
                    StationHandler.impureWaterScore(stack, bridge) > 0;
            if (StationHandler.bestImpureWaterSlot(villager.getInventory(), bridge, matcher) >= 0) return true;
            NearbyItemSources.ContainerSlot nearbySlot = NearbyItemSources.findBestNearbySlot(
                    level, villager, 16, 3, matcher, ItemStack::getCount, center);
            if (nearbySlot != null) return true;
            return findKitchenStorageSlot(level, villager, matcher, kitchenBounds) != null;
        }

        if (recipe.stationType() == StationType.FIRE_STATION && stationPos != null) {
            if (StationHandler.isSurfaceFireStation(level, center) && !StationHandler.surfaceHasFreeSlot(level, center)) return false;
        }
        if (stationPos != null && !StationHandler.stationSupportsRecipe(level, center, recipe)) return false;
        if (recipe.requiresTool() && !knifeAvailable(level, villager, kitchenBounds)) return false;
        if (recipe.bowlsRequired() > 0) {
            int bowlsAlreadyStaged = StationHandler.cookingPotContainerBowlCount(level, center);
            int bowlsNeeded = Math.max(0, recipe.bowlsRequired() - bowlsAlreadyStaged);
            int bowls = StationHandler.count(villager.getInventory(), Items.BOWL);
            NearbyItemSources.ContainerSlot nearbySlot = NearbyItemSources.findBestNearbySlot(
                    level, villager, 16, 3, s -> s.is(Items.BOWL), ItemStack::getCount, center);
            if (nearbySlot != null) bowls += Math.max(1, nearbySlot.score());
            if (bowls < bowlsNeeded) {
                NearbyItemSources.ContainerSlot villageSlot = findKitchenStorageSlot(level, villager, s -> s.is(Items.BOWL), kitchenBounds);
                if (villageSlot != null) bowls += Math.max(1, villageSlot.score());
            }
            if (bowls < bowlsNeeded) return false;
        }

        // Build total supply snapshot: inventory + kitchen containers + station contents
        Set<ResourceLocation> neededIds = new HashSet<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            neededIds.addAll(ingredient.itemIds());
        }
        Map<ResourceLocation, Integer> totalSupply = buildSupplySnapshot(level, villager, neededIds, kitchenBounds);
        // Add station contents for HOT_STATION (items already in the pot count toward supply)
        if (recipe.stationType() == StationType.HOT_STATION && stationPos != null) {
            for (ResourceLocation id : neededIds) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR) continue;
                int inStation = StationHandler.countItemInStation(level, center, item);
                if (inStation > 0) totalSupply.merge(id, inStation, Integer::sum);
            }
        }

        // Check if total supply covers all ingredient needs (with proper claim tracking)
        Map<ResourceLocation, Integer> claimed = new HashMap<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            boolean foundAny = false;
            for (ResourceLocation id : ingredient.itemIds()) {
                int available = totalSupply.getOrDefault(id, 0) - claimed.getOrDefault(id, 0);
                if (available >= ingredient.count()) {
                    claimed.merge(id, ingredient.count(), Integer::sum);
                    foundAny = true;
                    break;
                }
            }
            if (!foundAny) return false;
        }
        return true;
    }

    // ── Can plan with virtual supply ──

    public static boolean canPlanWithVirtual(
            DiscoveredRecipe recipe,
            Map<ResourceLocation, Integer> virtualSupply,
            boolean knifeAvailable,
            boolean waterAvailable
    ) {
        if (recipe.purification()) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null || !TownsteadConfig.isCookWaterPurificationEnabled() || !bridge.supportsPurification()) return false;
            return virtualSupply.getOrDefault(TOWNSTEAD_IMPURE_WATER_INPUT, 0) > 0;
        }
        if (recipe.requiresTool() && !knifeAvailable) return false;
        if (recipe.bowlsRequired() > 0) {
            if (virtualSupply.getOrDefault(MINECRAFT_BOWL, 0) < recipe.bowlsRequired()) return false;
        }
        for (RecipeIngredient ingredient : recipe.inputs()) {
            int available = 0;
            for (ResourceLocation id : ingredient.itemIds()) {
                available += virtualSupply.getOrDefault(id, 0);
            }
            if (available < ingredient.count()) return false;
        }
        return true;
    }

    public static void applyVirtual(DiscoveredRecipe recipe, Map<ResourceLocation, Integer> virtualSupply) {
        if (recipe.bowlsRequired() > 0) {
            int bowls = virtualSupply.getOrDefault(MINECRAFT_BOWL, 0);
            virtualSupply.put(MINECRAFT_BOWL, Math.max(0, bowls - recipe.bowlsRequired()));
        }
        for (RecipeIngredient ingredient : recipe.inputs()) {
            int remaining = ingredient.count();
            for (ResourceLocation id : ingredient.itemIds()) {
                if (remaining <= 0) break;
                int available = virtualSupply.getOrDefault(id, 0);
                int deduct = Math.min(available, remaining);
                if (deduct > 0) {
                    virtualSupply.put(id, available - deduct);
                    remaining -= deduct;
                }
            }
        }
        virtualSupply.merge(recipe.output(), recipe.outputCount(), Integer::sum);
    }

    // ── Pull and consume ──

    public static boolean pullAndConsume(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            @Nullable BlockPos stationAnchor,
            StationType stationType,
            Map<ResourceLocation, Integer> stagedInputs,
            Set<Long> kitchenBounds
    ) {
        BlockPos center = stationAnchor != null ? stationAnchor : villager.blockPosition();

        // Purification path
        if (recipe.purification()) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null || !TownsteadConfig.isCookWaterPurificationEnabled() || !bridge.supportsPurification()) return false;
            // Pull impure water from nearby/kitchen storage into inventory
            java.util.function.Predicate<ItemStack> impureMatcher = stack ->
                    StationHandler.impureWaterScore(stack, bridge) > 0;
            for (int i = 0; i < 8; i++) {
                if (!pullSingleTool(level, villager, impureMatcher, center, kitchenBounds)) break;
            }
            return StationHandler.loadPurificationFireStation(level, villager, stationAnchor, bridge);
        }

        // Knife
        if (recipe.requiresTool() && !StationHandler.hasKnife(villager.getInventory())) {
            if (!pullSingleTool(level, villager, StationHandler::isKnifeStack, center, kitchenBounds)) return false;
        }

        // Bowls
        int bowlsNeededForStage = 0;
        if (recipe.bowlsRequired() > 0) {
            int bowlsAlreadyStaged = StationHandler.cookingPotContainerBowlCount(level, stationAnchor);
            bowlsNeededForStage = Math.max(0, recipe.bowlsRequired() - bowlsAlreadyStaged);
            while (StationHandler.count(villager.getInventory(), Items.BOWL) < bowlsNeededForStage) {
                if (!pullSingleIngredient(level, villager, Items.BOWL, center, kitchenBounds)) return false;
            }
        }

        // Clear cooking pot contents first
        if (stationType == StationType.HOT_STATION) {
            StationHandler.clearCookingPotContents(level, villager, stationAnchor);
        }

        // Aggregate total needs per item across all ingredient entries
        // (e.g. 3 separate tomato entries → need 3 tomatoes total)
        SimpleContainer inv = villager.getInventory();
        Map<ResourceLocation, Integer> totalNeeded = new LinkedHashMap<>();
        Map<ResourceLocation, RecipeIngredient> ingredientByPrimary = new LinkedHashMap<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            ResourceLocation primary = ingredient.primaryId();
            totalNeeded.merge(primary, ingredient.count(), Integer::sum);
            ingredientByPrimary.putIfAbsent(primary, ingredient);
        }

        // Pull ingredients to meet total needs
        for (Map.Entry<ResourceLocation, Integer> entry : totalNeeded.entrySet()) {
            Item item = BuiltInRegistries.ITEM.get(entry.getKey());
            if (item == Items.AIR) continue;
            int needed = entry.getValue();
            while (StationHandler.count(inv, item) < needed) {
                RecipeIngredient template = ingredientByPrimary.get(entry.getKey());
                if (!pullSingleIngredientVariant(level, villager, template, center, kitchenBounds)) return false;
            }
        }

        // Fire station: pull extras + load surface
        if (stationType == StationType.FIRE_STATION
                && StationHandler.isSurfaceFireStation(level, stationAnchor)
                && !recipe.inputs().isEmpty()) {
            RecipeIngredient input = recipe.inputs().get(0);
            Item item = resolveItem(input, inv);
            int ingredientPerLoad = Math.max(1, input.count());
            int freeSlots = StationHandler.surfaceFreeSlotCount(level, stationAnchor);
            ResourceLocation stationId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(stationAnchor).getBlock());
            //? if >=1.21 {
            int maxStack = item.getDefaultMaxStackSize();
            int target = ResourceLocation.parse("farmersdelight:skillet").equals(stationId)
            //?} else {
            /*int maxStack = item.getMaxStackSize();
            int target = new ResourceLocation("farmersdelight", "skillet").equals(stationId)
            *///?}
                    ? maxStack : freeSlots * ingredientPerLoad;
            while (StationHandler.count(inv, item) < target) {
                if (!pullSingleIngredient(level, villager, item, center, kitchenBounds)) break;
            }
            return StationHandler.loadSurfaceFireStation(level, villager, stationAnchor, recipe);
        }

        // Hot station: stage into cooking pot
        stagedInputs.clear();
        if (stationType == StationType.HOT_STATION) {
            // Snapshot station contents BEFORE staging so we don't mistake
            // freshly-staged items for pre-existing ones (fixes tomato sauce bug:
            // 2 tomato entries where the second sees the first's staged tomato)
            Map<ResourceLocation, Integer> stationSnapshot = new HashMap<>();
            if (stationAnchor != null) {
                for (RecipeIngredient ingredient : recipe.inputs()) {
                    for (ResourceLocation id : ingredient.itemIds()) {
                        if (stationSnapshot.containsKey(id)) continue;
                        Item item = BuiltInRegistries.ITEM.get(id);
                        if (item == Items.AIR) continue;
                        int count = StationHandler.countItemInStation(level, stationAnchor, item);
                        if (count > 0) stationSnapshot.put(id, count);
                    }
                }
            }

            Map<ResourceLocation, Integer> stationClaimed = new HashMap<>();
            for (RecipeIngredient ingredient : recipe.inputs()) {
                int neededToStage = ingredient.count();
                if (stationAnchor != null) {
                    for (ResourceLocation id : ingredient.itemIds()) {
                        int inStation = stationSnapshot.getOrDefault(id, 0);
                        int alreadyClaimed = stationClaimed.getOrDefault(id, 0);
                        int available = Math.max(0, inStation - alreadyClaimed);
                        int fromStation = Math.min(neededToStage, available);
                        if (fromStation > 0) {
                            stationClaimed.merge(id, fromStation, Integer::sum);
                            neededToStage -= fromStation;
                        }
                        if (neededToStage <= 0) break;
                    }
                }
                if (neededToStage > 0) {
                    int staged = stageIngredientFromInventory(level, villager, stationAnchor, stationType, ingredient, neededToStage, stagedInputs);
                    if (staged < neededToStage) {
                        rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
                        return false;
                    }
                }
            }
            if (recipe.bowlsRequired() > 0 && bowlsNeededForStage > 0) {
                int stagedBowls = stageBowlsFromInventory(level, villager, stationAnchor, bowlsNeededForStage);
                if (stagedBowls < bowlsNeededForStage) {
                    rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
                    return false;
                }
                stagedInputs.merge(MINECRAFT_BOWL, stagedBowls, Integer::sum);
            }
        }

        // Consume remaining from inventory for non-fire, non-hot stations
        // For hot stations, ingredients are already staged — skip inventory consume
        if (stationType != StationType.HOT_STATION) {
            for (RecipeIngredient ingredient : recipe.inputs()) {
                if (!consumeIngredient(inv, ingredient, ingredient.count())) return false;
            }
        }
        return true;
    }

    // ── Staging ──

    private static int stageIngredientFromInventory(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            StationType stationType,
            RecipeIngredient ingredient,
            int count,
            Map<ResourceLocation, Integer> stagedInputs
    ) {
        if (ingredient == null || count <= 0) return 0;
        int staged = 0;
        for (ResourceLocation id : ingredient.itemIds()) {
            if (staged >= count) break;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            int available = StationHandler.count(villager.getInventory(), item);
            int desired = Math.min(count - staged, available);
            if (desired <= 0) continue;
            int added = stageFromInventory(level, villager, stationAnchor, stationType, item, desired);
            if (added > 0) {
                staged += added;
                stagedInputs.merge(id, added, Integer::sum);
            }
        }
        return staged;
    }

    private static int stageFromInventory(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            StationType stationType,
            Item item,
            int count
    ) {
        if (count <= 0) return 0;
        int removed = StationHandler.removeUpTo(villager.getInventory(), item, count);
        if (removed <= 0) return 0;

        ItemStack toInsert = new ItemStack(item, removed);
        ItemStack remainder;
        //? if >=1.21 {
        ResourceLocation FD_COOKING_POT = ResourceLocation.parse("farmersdelight:cooking_pot");
        //?} else {
        /*ResourceLocation FD_COOKING_POT = new ResourceLocation("farmersdelight", "cooking_pot");
        *///?}
        if (stationType == StationType.HOT_STATION && item != Items.BOWL && stationAnchor != null
                && FD_COOKING_POT.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(stationAnchor).getBlock()))) {
            remainder = StationHandler.insertIntoCookingPotIngredients(level, stationAnchor, toInsert);
        } else {
            remainder = StationHandler.insertIntoStation(level, stationAnchor, toInsert);
        }
        if (!remainder.isEmpty()) villager.getInventory().addItem(remainder);
        return removed - remainder.getCount();
    }

    private static int stageBowlsFromInventory(ServerLevel level, VillagerEntityMCA villager, BlockPos stationAnchor, int count) {
        if (count <= 0) return 0;
        int removed = StationHandler.removeUpTo(villager.getInventory(), Items.BOWL, count);
        if (removed <= 0) return 0;
        ItemStack toInsert = new ItemStack(Items.BOWL, removed);
        ItemStack remainder = StationHandler.insertIntoCookingPotContainerSlot(level, stationAnchor, toInsert, false);
        if (!remainder.isEmpty()) villager.getInventory().addItem(remainder);
        return removed - remainder.getCount();
    }

    public static void rollbackStagedInputs(
            ServerLevel level,
            VillagerEntityMCA villager,
            @Nullable BlockPos stationAnchor,
            Map<ResourceLocation, Integer> stagedInputs
    ) {
        if (stationAnchor == null || stagedInputs.isEmpty()) {
            stagedInputs.clear();
            return;
        }
        for (Map.Entry<ResourceLocation, Integer> entry : stagedInputs.entrySet()) {
            Item item = BuiltInRegistries.ITEM.get(entry.getKey());
            if (item == Items.AIR) continue;
            int removed = StationHandler.extractFromStation(level, stationAnchor, item, entry.getValue());
            if (removed > 0) villager.getInventory().addItem(new ItemStack(item, removed));
        }
        stagedInputs.clear();
    }

    // ── Pull ingredients ──

    private static boolean pullSingleIngredient(
            ServerLevel level,
            VillagerEntityMCA villager,
            Item item,
            BlockPos center,
            Set<Long> kitchenBounds
    ) {
        if (NearbyItemSources.pullSingleToInventory(level, villager, 16, 3, s -> s.is(item), ItemStack::getCount, center)) return true;
        NearbyItemSources.ContainerSlot slot = findKitchenStorageSlot(level, villager, s -> s.is(item), kitchenBounds);
        if (slot == null) return false;
        ItemStack extracted = NearbyItemSources.extractOne(level, slot);
        if (extracted.isEmpty()) return false;
        return addToInventoryOrNearbyStorage(level, villager, extracted, center);
    }

    private static boolean pullSingleIngredientVariant(
            ServerLevel level,
            VillagerEntityMCA villager,
            RecipeIngredient ingredient,
            BlockPos center,
            Set<Long> kitchenBounds
    ) {
        for (ResourceLocation id : ingredient.itemIds()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            if (pullSingleIngredient(level, villager, item, center, kitchenBounds)) return true;
        }
        return false;
    }

    private static boolean pullSingleTool(
            ServerLevel level,
            VillagerEntityMCA villager,
            java.util.function.Predicate<ItemStack> matcher,
            BlockPos center,
            Set<Long> kitchenBounds
    ) {
        if (NearbyItemSources.pullSingleToInventory(level, villager, 16, 3, matcher, ItemStack::getCount, center)) return true;
        NearbyItemSources.ContainerSlot slot = findKitchenStorageSlot(level, villager, matcher, kitchenBounds);
        if (slot == null) return false;
        ItemStack extracted = NearbyItemSources.extractOne(level, slot);
        if (extracted.isEmpty()) return false;
        return addToInventoryOrNearbyStorage(level, villager, extracted, center);
    }

    // ── Output storage ──

    public static int storeOutputInCookStorage(
            ServerLevel level,
            VillagerEntityMCA villager,
            ItemStack stack,
            BlockPos center,
            Set<Long> kitchenBounds
    ) {
        if (stack.isEmpty()) return 0;
        if (kitchenBounds.isEmpty()) return 0;
        BlockPos origin = center != null ? center : villager.blockPosition();
        int totalInserted = 0;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-16, -3, -16), origin.offset(16, 3, 16))) {
            if (stack.isEmpty()) break;
            if (!kitchenBounds.contains(pos.asLong())) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (!StationHandler.isCookStorageCandidate(level, pos, be)) continue;

            int before = stack.getCount();
            if (be instanceof Container container) {
                insertIntoContainer(container, stack);
            }
            if (!stack.isEmpty()) {
                IItemHandler handler = StationHandler.getItemHandler(be, level, pos, null);
                if (handler != null) {
                    ItemStack remainder = StationHandler.insertIntoHandler(handler, stack, false);
                    int inserted = stack.getCount() - remainder.getCount();
                    if (inserted > 0) stack.shrink(inserted);
                }
                for (Direction dir : Direction.values()) {
                    if (stack.isEmpty()) break;
                    handler = StationHandler.getItemHandler(be, level, pos, dir);
                    if (handler == null) continue;
                    ItemStack remainder = StationHandler.insertIntoHandler(handler, stack, false);
                    int inserted = stack.getCount() - remainder.getCount();
                    if (inserted > 0) stack.shrink(inserted);
                }
            }
            totalInserted += before - stack.getCount();
        }
        return totalInserted;
    }

    // ── Kitchen storage slot search ──

    public static @Nullable NearbyItemSources.ContainerSlot findKitchenStorageSlot(
            ServerLevel level,
            VillagerEntityMCA villager,
            java.util.function.Predicate<ItemStack> matcher,
            Set<Long> kitchenBounds
    ) {
        Optional<Village> villageOpt = FarmersDelightCookAssignment.resolveVillage(villager);
        if (villageOpt.isEmpty()) return null;
        Village village = villageOpt.get();

        NearbyItemSources.ContainerSlot best = null;
        Set<Long> visited = new HashSet<>();
        for (Building building : village.getBuildings().values()) {
            for (BlockPos pos : (Iterable<BlockPos>) building.getBlockPosStream()::iterator) {
                long key = pos.asLong();
                if (!visited.add(key)) continue;
                if (!StationHandler.isInKitchenWorkArea(kitchenBounds, pos)) continue;
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
                        if (isBetterSlot(best, dist, score)) {
                            best = new NearbyItemSources.ContainerSlot(pos.immutable(), container, false, i, score, dist, null);
                        }
                    }
                }

                IItemHandler handler = StationHandler.getItemHandler(be, level, pos, null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!matcher.test(stack)) continue;
                        int score = stack.getCount();
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (isBetterSlot(best, dist, score)) {
                            best = new NearbyItemSources.ContainerSlot(pos.immutable(), null, true, i, score, dist, null);
                        }
                    }
                }
                for (Direction side : Direction.values()) {
                    handler = StationHandler.getItemHandler(be, level, pos, side);
                    if (handler == null) continue;
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!matcher.test(stack)) continue;
                        int score = stack.getCount();
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (isBetterSlot(best, dist, score)) {
                            best = new NearbyItemSources.ContainerSlot(pos.immutable(), null, true, i, score, dist, side);
                        }
                    }
                }
            }
        }
        return best;
    }

    // ── Knife / water availability ──

    public static boolean knifeAvailable(ServerLevel level, VillagerEntityMCA villager, Set<Long> kitchenBounds) {
        if (StationHandler.hasKnife(villager.getInventory())) return true;
        return findKitchenStorageSlot(level, villager, StationHandler::isKnifeStack, kitchenBounds) != null;
    }

    public static boolean waterAvailable(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos center, Set<Long> kitchenBounds) {
        if (hasItem(villager.getInventory(), Items.WATER_BUCKET)) return true;
        BlockPos searchCenter = center != null ? center : villager.blockPosition();
        if (findKitchenStorageSlot(level, villager, s -> s.is(Items.WATER_BUCKET), kitchenBounds) != null) return true;
        boolean hasBucket = hasItem(villager.getInventory(), Items.BUCKET)
                || findKitchenStorageSlot(level, villager, s -> s.is(Items.BUCKET), kitchenBounds) != null;
        return hasBucket && hasNearbyWater(level, searchCenter, 24, 4);
    }

    // ── Utility methods ──

    private static boolean isBetterSlot(@Nullable NearbyItemSources.ContainerSlot currentBest, double candidateDist, int candidateScore) {
        if (currentBest == null) return true;
        if (candidateDist < currentBest.distanceSqr() - 4.0d) return true;
        return candidateDist < currentBest.distanceSqr() + 4.0d && candidateScore > currentBest.score();
    }

    private static boolean addToInventoryOrNearbyStorage(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, BlockPos center) {
        if (stack.isEmpty()) return true;
        ItemStack remainder = villager.getInventory().addItem(stack);
        if (remainder.isEmpty()) return true;
        NearbyItemSources.insertIntoNearbyStorage(level, villager, remainder, 16, 3, center != null ? center : villager.blockPosition());
        if (remainder.isEmpty()) return true;
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
        drop.setPickUpDelay(0);
        level.addFreshEntity(drop);
        return false;
    }

    static int countIngredientInInventory(SimpleContainer inv, RecipeIngredient ingredient) {
        if (ingredient == null) return 0;
        int total = 0;
        for (ResourceLocation id : ingredient.itemIds()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            total += StationHandler.count(inv, item);
        }
        return total;
    }

    private static int countIngredientInStation(ServerLevel level, BlockPos pos, RecipeIngredient ingredient) {
        if (ingredient == null) return 0;
        int total = 0;
        for (ResourceLocation id : ingredient.itemIds()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            total += StationHandler.countItemInStation(level, pos, item);
        }
        return total;
    }

    private static boolean consumeIngredient(SimpleContainer inv, RecipeIngredient ingredient, int needed) {
        if (needed <= 0) return true;
        int remaining = needed;
        for (ResourceLocation id : ingredient.itemIds()) {
            if (remaining <= 0) break;
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            int removed = StationHandler.removeUpTo(inv, item, remaining);
            remaining -= removed;
        }
        return remaining <= 0;
    }

    private static Item resolveItem(RecipeIngredient ingredient, SimpleContainer inv) {
        for (ResourceLocation id : ingredient.itemIds()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR && StationHandler.count(inv, item) > 0) return item;
        }
        return BuiltInRegistries.ITEM.get(ingredient.primaryId());
    }

    private static boolean hasItem(SimpleContainer inv, Item item) {
        return StationHandler.count(inv, item) > 0;
    }

    private static boolean hasNearbyWater(ServerLevel level, BlockPos center, int radius, int vertical) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -vertical, -radius),
                center.offset(radius, vertical, radius))) {
            if (level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    private static void insertIntoContainer(Container container, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) continue;
            if (!StationHandler.isSameItemComponents(slot, stack)) continue;
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
            container.setItem(i, StationHandler.copyWithCount(stack, move));
            stack.shrink(move);
            container.setChanged();
        }
    }
}
