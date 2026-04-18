package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.RecipeIngredient;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
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
    private static final String KITCHEN_SLOT_CLAIM_CATEGORY = "kitchen_supply";
    private static final int KITCHEN_SLOT_CLAIM_TTL_TICKS = 40;

    public record PullResult(boolean success, String detail, List<String> diagnostics) {
        static PullResult successResult() {
            return new PullResult(true, "", List.of());
        }

        static PullResult failure(String detail) {
            return new PullResult(false, detail == null ? "" : detail, List.of());
        }

        static PullResult failure(String detail, List<String> diagnostics) {
            return new PullResult(false, detail == null ? "" : detail, diagnostics == null ? List.of() : List.copyOf(diagnostics));
        }
    }

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
        return buildSupplySnapshot(level, villager, trackedIds, kitchenBounds, KitchenStorageIndex.snapshot(level, villager, kitchenBounds));
    }

    public static Map<ResourceLocation, Integer> buildSupplySnapshot(
            ServerLevel level,
            VillagerEntityMCA villager,
            Set<ResourceLocation> trackedIds,
            Set<Long> kitchenBounds,
            KitchenStorageIndex.Snapshot kitchenSnapshot
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
        Map<ResourceLocation, Integer> kitchenSupply = kitchenSnapshot.supply(trackedIds, trackImpureWater, thirstBridge);
        for (Map.Entry<ResourceLocation, Integer> entry : kitchenSupply.entrySet()) {
            supply.merge(entry.getKey(), entry.getValue(), Integer::sum);
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
        return canFulfill(level, villager, recipe, stationPos, kitchenBounds, KitchenStorageIndex.snapshot(level, villager, kitchenBounds));
    }

    public static boolean canFulfill(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds,
            KitchenStorageIndex.Snapshot kitchenSnapshot
    ) {
        return canFulfill(level, villager, recipe, stationPos, kitchenBounds, kitchenSnapshot, null);
    }

    public static boolean canFulfill(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds,
            KitchenStorageIndex.Snapshot kitchenSnapshot,
            @Nullable Map<ResourceLocation, Boolean> toolAvailableByRecipe
    ) {
        BlockPos center = stationPos != null ? stationPos : villager.blockPosition();
        if (recipe.purification()) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null || !TownsteadConfig.isCookWaterPurificationEnabled() || !bridge.supportsPurification()) return false;
            if (!StationHandler.supportsPurificationAt(level, center)) return false;
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
        if (recipe.requiresTool()) {
            boolean toolAvailable = toolAvailableByRecipe != null
                    ? toolAvailableByRecipe.computeIfAbsent(
                            recipe.id(),
                            unused -> recipeToolAvailable(level, villager, recipe, kitchenBounds, kitchenSnapshot, false))
                    : recipeToolAvailable(level, villager, recipe, kitchenBounds);
            if (!toolAvailable) return false;
        }
        if (recipe.containerItemId() != null && recipe.containerCount() > 0) {
            Item containerItem = BuiltInRegistries.ITEM.get(recipe.containerItemId());
            if (containerItem == Items.AIR) return false;
            int containerAlreadyStaged = StationHandler.cookingPotContainerItemCount(level, center, containerItem);
            int containersNeeded = Math.max(0, recipe.containerCount() - containerAlreadyStaged);
            int containers = StationHandler.count(villager.getInventory(), containerItem);
            NearbyItemSources.ContainerSlot nearbySlot = NearbyItemSources.findBestNearbySlot(
                    level, villager, 16, 3, s -> s.is(containerItem), ItemStack::getCount, center);
            if (nearbySlot != null) containers += Math.max(1, nearbySlot.score());
            if (containers < containersNeeded) {
                NearbyItemSources.ContainerSlot villageSlot = findKitchenStorageSlot(level, villager, s -> s.is(containerItem), kitchenBounds);
                if (villageSlot != null) containers += Math.max(1, villageSlot.score());
            }
            if (containers < containersNeeded) {
                containers += countKitchenStorageLive(level, villager, s -> s.is(containerItem), kitchenBounds, containersNeeded - containers);
            }
            if (containers < containersNeeded) return false;
        }

        // Build total supply snapshot: inventory + kitchen containers + station contents
        Set<ResourceLocation> neededIds = new HashSet<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            neededIds.addAll(ingredient.itemIds());
        }
        Map<ResourceLocation, Integer> totalSupply = buildSupplySnapshot(level, villager, neededIds, kitchenBounds, kitchenSnapshot);
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
            if (!foundAny) {
                int liveAvailable = countIngredientLive(level, villager, ingredient, kitchenBounds, ingredient.count());
                if (liveAvailable >= ingredient.count()) {
                    foundAny = true;
                }
            }
            if (!foundAny) return false;
        }
        return true;
    }

    public static String describeMissingRequirements(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds
    ) {
        return describeMissingRequirements(level, villager, recipe, stationPos, kitchenBounds,
                KitchenStorageIndex.snapshot(level, villager, kitchenBounds));
    }

    public static String describeMissingRequirements(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds,
            KitchenStorageIndex.Snapshot kitchenSnapshot
    ) {
        BlockPos center = stationPos != null ? stationPos : villager.blockPosition();
        Map<String, Integer> missing = new LinkedHashMap<>();

        if (recipe.purification()) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null || !TownsteadConfig.isCookWaterPurificationEnabled() || !bridge.supportsPurification()) {
                return "a water purification source";
            }
            if (!StationHandler.supportsPurificationAt(level, center)) {
                return "a heated skillet or campfire with space";
            }
            java.util.function.Predicate<ItemStack> matcher = stack ->
                    StationHandler.impureWaterScore(stack, bridge) > 0;
            boolean hasImpureWater = StationHandler.bestImpureWaterSlot(villager.getInventory(), bridge, matcher) >= 0
                    || NearbyItemSources.findBestNearbySlot(level, villager, 16, 3, matcher, ItemStack::getCount, center) != null
                    || findKitchenStorageSlot(level, villager, matcher, kitchenBounds) != null;
            if (!hasImpureWater) return "impure water";
            return "";
        }

        if (recipe.requiresTool() && !recipeToolAvailable(level, villager, recipe, kitchenBounds)) {
            missing.merge("Knife", 1, Integer::sum);
        }

        if (recipe.containerItemId() != null && recipe.containerCount() > 0) {
            Item containerItem = BuiltInRegistries.ITEM.get(recipe.containerItemId());
            if (containerItem == Items.AIR) {
                missing.merge(recipe.containerItemId().toString(), recipe.containerCount(), Integer::sum);
            } else {
                int staged = StationHandler.cookingPotContainerItemCount(level, center, containerItem);
                int available = StationHandler.count(villager.getInventory(), containerItem);
                NearbyItemSources.ContainerSlot nearbySlot = NearbyItemSources.findBestNearbySlot(
                        level, villager, 16, 3, s -> s.is(containerItem), ItemStack::getCount, center);
                if (nearbySlot != null) available += Math.max(1, nearbySlot.score());
                NearbyItemSources.ContainerSlot kitchenSlot = findKitchenStorageSlot(level, villager, s -> s.is(containerItem), kitchenBounds);
                if (kitchenSlot != null) available += Math.max(1, kitchenSlot.score());
                int missingCount = Math.max(0, recipe.containerCount() - staged - available);
                if (missingCount > 0) {
                    missing.merge(itemDisplayName(containerItem), missingCount, Integer::sum);
                }
            }
        }

        Set<ResourceLocation> neededIds = new HashSet<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            neededIds.addAll(ingredient.itemIds());
        }
        Map<ResourceLocation, Integer> totalSupply = buildSupplySnapshot(level, villager, neededIds, kitchenBounds, kitchenSnapshot);
        if (recipe.stationType() == StationType.HOT_STATION && stationPos != null) {
            for (ResourceLocation id : neededIds) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item == Items.AIR) continue;
                int inStation = StationHandler.countItemInStation(level, center, item);
                if (inStation > 0) totalSupply.merge(id, inStation, Integer::sum);
            }
        }

        Map<ResourceLocation, Integer> claimed = new HashMap<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            int bestAvailable = 0;
            ResourceLocation bestId = null;
            boolean foundAny = false;
            for (ResourceLocation id : ingredient.itemIds()) {
                int available = totalSupply.getOrDefault(id, 0) - claimed.getOrDefault(id, 0);
                if (available > bestAvailable) {
                    bestAvailable = available;
                    bestId = id;
                }
                if (available >= ingredient.count()) {
                    claimed.merge(id, ingredient.count(), Integer::sum);
                    foundAny = true;
                    break;
                }
            }
            if (!foundAny) {
                int missingCount = Math.max(1, ingredient.count() - bestAvailable);
                missing.merge(ingredientDisplayName(ingredient, bestId), missingCount, Integer::sum);
            }
        }

        return formatMissingRequirements(missing);
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
        if (recipe.containerItemId() != null && recipe.containerCount() > 0) {
            if (virtualSupply.getOrDefault(recipe.containerItemId(), 0) < recipe.containerCount()) return false;
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
        if (recipe.containerItemId() != null && recipe.containerCount() > 0) {
            int containers = virtualSupply.getOrDefault(recipe.containerItemId(), 0);
            virtualSupply.put(recipe.containerItemId(), Math.max(0, containers - recipe.containerCount()));
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
        return pullAndConsumeDetailed(level, villager, recipe, stationAnchor, stationType, stagedInputs, kitchenBounds).success();
    }

    public static PullResult pullAndConsumeDetailed(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            @Nullable BlockPos stationAnchor,
            StationType stationType,
            Map<ResourceLocation, Integer> stagedInputs,
            Set<Long> kitchenBounds
    ) {
        BlockPos center = stationAnchor != null ? stationAnchor : villager.blockPosition();
        List<String> diagnostics = new ArrayList<>();

        // Purification path
        if (recipe.purification()) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null || !TownsteadConfig.isCookWaterPurificationEnabled() || !bridge.supportsPurification()) {
                return PullResult.failure("a water purification source");
            }
            // Pull impure water from nearby/kitchen storage into inventory
            java.util.function.Predicate<ItemStack> impureMatcher = stack ->
                    StationHandler.impureWaterScore(stack, bridge) > 0;
            for (int i = 0; i < 8; i++) {
                if (!pullSingleTool(level, villager, impureMatcher, center, kitchenBounds)) break;
            }
            if (StationHandler.loadPurificationFireStation(level, villager, stationAnchor, bridge)) {
                return PullResult.successResult();
            }
            return PullResult.failure("a heated skillet or campfire with space");
        }

        // Knife
        if (recipe.requiresTool() && !villagerHasRecipeTool(villager, recipe)) {
            if (!pullSingleTool(level, villager, stack -> ModRecipeRegistry.recipeToolMatches(recipe, stack), center, kitchenBounds)) {
                return PullResult.failure("Knife");
            }
        }

        // Bowls
        Item recipeContainerItem = recipe.containerItemId() == null ? Items.AIR : BuiltInRegistries.ITEM.get(recipe.containerItemId());
        int containersNeededForStage = 0;
        if (recipeContainerItem != Items.AIR && recipe.containerCount() > 0) {
            int containersAlreadyStaged = StationHandler.cookingPotContainerItemCount(level, stationAnchor, recipeContainerItem);
            containersNeededForStage = Math.max(0, recipe.containerCount() - containersAlreadyStaged);
            while (StationHandler.count(villager.getInventory(), recipeContainerItem) < containersNeededForStage) {
                if (!pullSingleIngredient(level, villager, recipeContainerItem, center, kitchenBounds)) {
                    return PullResult.failure(itemDisplayName(recipeContainerItem));
                }
            }
        }

        // Aggregate total needs per item across all ingredient entries
        // Preserve recipe entry order for hot-station staging, but still pull the
        // total required amount for repeated equivalent entries before staging.
        // Example: [bean][bean][bean][bean] must pull four beans total, then stage
        // them one entry at a time into four slots.
        SimpleContainer inv = villager.getInventory();
        Map<RecipeIngredient, Integer> totalNeededByIngredient = new LinkedHashMap<>();
        for (RecipeIngredient ingredient : recipe.inputs()) {
            totalNeededByIngredient.merge(ingredient, ingredient.count(), Integer::sum);
        }
        for (Map.Entry<RecipeIngredient, Integer> entry : totalNeededByIngredient.entrySet()) {
            RecipeIngredient ingredient = entry.getKey();
            int needed = entry.getValue();
            while (countIngredientInInventory(inv, ingredient) < needed) {
                if (!pullSingleIngredientVariant(level, villager, ingredient, center, kitchenBounds)) {
                    return PullResult.failure(ingredientDisplayName(ingredient, null), diagnostics);
                }
            }
        }

        // Fire station: pull extras + load surface
        if (stationType == StationType.FIRE_STATION
                && StationHandler.isSurfaceFireStation(level, stationAnchor)
                && !recipe.inputs().isEmpty()) {
            RecipeIngredient input = recipe.inputs().get(0);
            Item item = resolveItem(input, inv);
            if (item == Items.AIR) {
                for (ResourceLocation id : input.itemIds()) {
                    Item candidate = BuiltInRegistries.ITEM.get(id);
                    if (candidate != Items.AIR) {
                        item = candidate;
                        break;
                    }
                }
            }
            if (item == Items.AIR) {
                return PullResult.failure(ingredientDisplayName(input, null), diagnostics);
            }
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
            if (StationHandler.loadSurfaceFireStation(level, villager, stationAnchor, recipe)) {
                return PullResult.successResult();
            }
            while (StationHandler.surfaceHasFreeSlot(level, stationAnchor)) {
                ItemStack extracted = extractSingleForStaging(level, villager, item, kitchenBounds);
                if (extracted.isEmpty()) break;
                if (!StationHandler.loadSurfaceFireStationItem(level, villager, stationAnchor, recipe, extracted)) {
                    addToInventoryOrNearbyStorage(level, villager, extracted, center);
                    break;
                }
            }
            if (!StationHandler.surfaceHasFreeSlot(level, stationAnchor)) {
                return PullResult.successResult();
            }
            return PullResult.failure(townsteadStationLoadDetail(recipe));
        }

        // Hot station: stage into cooking pot
        stagedInputs.clear();
        if (stationType == StationType.HOT_STATION) {
            KitchenStorageIndex.Snapshot kitchenSnapshot = KitchenStorageIndex.snapshot(level, villager, kitchenBounds);
            diagnostics.add("hot recipe entries=" + recipe.inputs().size()
                    + " grouped=" + totalNeededByIngredient.size()
                    + " container=" + (recipe.containerItemId() == null ? "none" : recipe.containerItemId() + " x" + recipe.containerCount()));
            for (RecipeIngredient ingredient : recipe.inputs()) {
                KitchenStorageIndex.ExtractionPlan plan = kitchenSnapshot.planIngredientExtraction(ingredient, ingredient.count());
                diagnostics.add("ingredient req=" + ingredientDisplayName(ingredient, null)
                        + " x" + ingredient.count()
                        + " inv=" + countIngredientInInventory(inv, ingredient)
                        + " plan=" + describeExtractionPlan(plan));
                int staged = stageIngredientDirect(level, villager, stationAnchor, ingredient, ingredient.count(),
                        stagedInputs, kitchenBounds, center, kitchenSnapshot, diagnostics);
                if (staged < ingredient.count()) {
                    rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
                    return PullResult.failure(ingredientDisplayName(ingredient, null), diagnostics);
                }
            }
            if (recipeContainerItem != Items.AIR && containersNeededForStage > 0) {
                KitchenStorageIndex.ExtractionPlan plan = kitchenSnapshot.planItemExtraction(recipe.containerItemId(), containersNeededForStage);
                diagnostics.add("container req=" + itemDisplayName(recipeContainerItem)
                        + " x" + containersNeededForStage
                        + " inv=" + StationHandler.count(villager.getInventory(), recipeContainerItem)
                        + " plan=" + describeExtractionPlan(plan));
                int stagedContainers = stageContainerDirect(level, villager, stationAnchor, recipeContainerItem,
                        containersNeededForStage, kitchenBounds, center, kitchenSnapshot, diagnostics);
                if (stagedContainers < containersNeededForStage) {
                    rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
                    return PullResult.failure(itemDisplayName(recipeContainerItem), diagnostics);
                }
                stagedInputs.merge(recipe.containerItemId(), stagedContainers, Integer::sum);
            }
            if (stationAnchor != null && !StationHandler.cookingPotMatchesRecipe(level, stationAnchor, recipe)) {
                String staged = StationHandler.describeCookingPotInputs(level, stationAnchor);
                rollbackStagedInputs(level, villager, stationAnchor, stagedInputs);
                diagnostics.add("pot match failed: " + (staged == null || staged.isBlank() ? "<blank>" : staged));
                return PullResult.failure(staged == null || staged.isBlank() ? "the pot contents" : staged, diagnostics);
            }
        }

        // Consume remaining from inventory for station types that do not need
        // task-local handoff. Cutting-board recipes keep the input in inventory
        // until CookWorkTask moves one item into heldCuttingInput for the actual
        // board interaction in the COOK phase.
        if (stationType != StationType.HOT_STATION && stationType != StationType.CUTTING_BOARD) {
            for (RecipeIngredient ingredient : recipe.inputs()) {
                if (!consumeIngredient(inv, ingredient, ingredient.count())) {
                    return PullResult.failure(ingredientDisplayName(ingredient, null), diagnostics);
                }
            }
        }
        return PullResult.successResult();
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
            boolean inserted = StationHandler.insertIntoCookingPotNextIngredientSlot(level, stationAnchor, toInsert);
            remainder = inserted ? ItemStack.EMPTY : toInsert;
        } else {
            remainder = StationHandler.insertIntoStation(level, stationAnchor, toInsert);
        }
        if (!remainder.isEmpty()) villager.getInventory().addItem(remainder);
        return removed - remainder.getCount();
    }

    private static int stageContainerFromInventory(ServerLevel level, VillagerEntityMCA villager, BlockPos stationAnchor, Item item, int count) {
        if (count <= 0) return 0;
        int removed = StationHandler.removeUpTo(villager.getInventory(), item, count);
        if (removed <= 0) return 0;
        ItemStack toInsert = new ItemStack(item, removed);
        ItemStack remainder = StationHandler.insertIntoCookingPotContainerSlot(level, stationAnchor, toInsert, false);
        if (!remainder.isEmpty()) villager.getInventory().addItem(remainder);
        return removed - remainder.getCount();
    }

    private static int stageIngredientDirect(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            RecipeIngredient ingredient,
            int count,
            Map<ResourceLocation, Integer> stagedInputs,
            Set<Long> kitchenBounds,
            BlockPos center,
            KitchenStorageIndex.Snapshot kitchenSnapshot,
            List<String> diagnostics
    ) {
        if (ingredient == null || count <= 0) return 0;
        int staged = stageIngredientFromInventory(level, villager, stationAnchor, StationType.HOT_STATION, ingredient, count, stagedInputs);
        if (staged > 0) {
            diagnostics.add("ingredient inv-stage=" + ingredientDisplayName(ingredient, null) + " inserted=" + staged);
        }
        int remaining = count - staged;
        if (remaining <= 0) return staged;

        KitchenStorageIndex.ExtractionPlan plan = kitchenSnapshot.planIngredientExtraction(ingredient, remaining);
        for (KitchenStorageIndex.PlannedExtraction extraction : plan.slots()) {
            if (remaining <= 0) break;
            Item item = BuiltInRegistries.ITEM.get(extraction.itemId());
            if (item == Items.AIR) continue;
            int inserted = stageIngredientFromPlannedSlot(level, villager, stationAnchor, extraction, remaining, center, diagnostics);
            if (inserted <= 0) continue;
            staged += inserted;
            remaining -= inserted;
            stagedInputs.merge(extraction.itemId(), inserted, Integer::sum);
        }
        return staged;
    }

    private static int stageContainerDirect(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            Item item,
            int count,
            Set<Long> kitchenBounds,
            BlockPos center,
            KitchenStorageIndex.Snapshot kitchenSnapshot,
            List<String> diagnostics
    ) {
        int staged = stageContainerFromInventory(level, villager, stationAnchor, item, count);
        if (staged > 0) {
            diagnostics.add("container inv-stage=" + itemDisplayName(item) + " inserted=" + staged);
        }
        int remaining = count - staged;
        if (remaining <= 0) return staged;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) return staged;
        KitchenStorageIndex.ExtractionPlan plan = kitchenSnapshot.planItemExtraction(itemId, remaining);
        for (KitchenStorageIndex.PlannedExtraction extraction : plan.slots()) {
            if (remaining <= 0) break;
            int inserted = stageContainerFromPlannedSlot(level, villager, stationAnchor, extraction, remaining, center, diagnostics);
            if (inserted <= 0) continue;
            staged += inserted;
            remaining -= inserted;
        }
        return staged;
    }

    private static boolean stageSingleIngredientFromInventory(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            Item item
    ) {
        if (StationHandler.count(villager.getInventory(), item) <= 0) return false;
        return stageFromInventory(level, villager, stationAnchor, StationType.HOT_STATION, item, 1) == 1;
    }

    private static boolean stageSingleIngredientFromStorage(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            Item item,
            Set<Long> kitchenBounds,
            BlockPos center
    ) {
        ItemStack extracted = extractSingleForStaging(level, villager, item, kitchenBounds);
        if (extracted.isEmpty()) return false;
        if (StationHandler.insertIntoCookingPotNextIngredientSlot(level, stationAnchor, extracted)) {
            return true;
        }
        return addToInventoryOrNearbyStorage(level, villager, extracted, center);
    }

    private static int stageIngredientFromPlannedSlot(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            KitchenStorageIndex.PlannedExtraction extraction,
            int remaining,
            BlockPos center,
            List<String> diagnostics
    ) {
        int requested = Math.min(remaining, extraction.count());
        ItemStack extracted = extractUpTo(level, extraction.slot(), requested);
        diagnostics.add("ingredient extract source=" + describeContainerSlot(extraction.slot())
                + " item=" + extraction.itemId()
                + " requested=" + requested
                + " extracted=" + describeStack(extracted));
        if (extracted.isEmpty()) return 0;
        KitchenStorageIndex.invalidate(level, extraction.slot().pos());
        int inserted = stageExtractedIngredient(level, stationAnchor, extracted);
        diagnostics.add("ingredient stage item=" + extraction.itemId()
                + " extractedCount=" + extracted.getCount()
                + " inserted=" + inserted
                + " pot=" + StationHandler.describeCookingPotInputs(level, stationAnchor));
        if (inserted < extracted.getCount()) {
            ItemStack remainder = StationHandler.copyWithCount(extracted, extracted.getCount() - inserted);
            addToInventoryOrNearbyStorage(level, villager, remainder, center);
        }
        return inserted;
    }

    private static boolean stageSingleContainerFromStorage(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            Item item,
            Set<Long> kitchenBounds,
            BlockPos center
    ) {
        ItemStack extracted = extractSingleForStaging(level, villager, item, kitchenBounds);
        if (extracted.isEmpty()) return false;
        ItemStack remainder = StationHandler.insertIntoCookingPotContainerSlot(level, stationAnchor, extracted, false);
        if (remainder.isEmpty()) return true;
        return addToInventoryOrNearbyStorage(level, villager, remainder, center);
    }

    private static int stageContainerFromPlannedSlot(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos stationAnchor,
            KitchenStorageIndex.PlannedExtraction extraction,
            int remaining,
            BlockPos center,
            List<String> diagnostics
    ) {
        int requested = Math.min(remaining, extraction.count());
        ItemStack extracted = extractUpTo(level, extraction.slot(), requested);
        diagnostics.add("container extract source=" + describeContainerSlot(extraction.slot())
                + " item=" + extraction.itemId()
                + " requested=" + requested
                + " extracted=" + describeStack(extracted));
        if (extracted.isEmpty()) return 0;
        KitchenStorageIndex.invalidate(level, extraction.slot().pos());
        ItemStack remainder = StationHandler.insertIntoCookingPotContainerSlot(level, stationAnchor, extracted, false);
        int inserted = extracted.getCount() - remainder.getCount();
        diagnostics.add("container stage item=" + extraction.itemId()
                + " extractedCount=" + extracted.getCount()
                + " inserted=" + inserted
                + " remainder=" + describeStack(remainder)
                + " pot=" + StationHandler.describeCookingPotInputs(level, stationAnchor));
        if (!remainder.isEmpty()) {
            addToInventoryOrNearbyStorage(level, villager, remainder, center);
        }
        return inserted;
    }

    private static ItemStack extractSingleForStaging(
            ServerLevel level,
            VillagerEntityMCA villager,
            Item item,
            Set<Long> kitchenBounds
    ) {
        NearbyItemSources.ContainerSlot slot = findClaimedKitchenStorageSlot(level, villager, s -> s.is(item), kitchenBounds);
        if (slot == null) {
            slot = findKitchenStorageSlotLive(level, villager, s -> s.is(item), kitchenBounds);
            if (slot == null) return ItemStack.EMPTY;
        }
        ItemStack extracted = NearbyItemSources.extractOne(level, slot);
        ConsumableTargetClaims.releaseSlot(level, villager.getUUID(), KITCHEN_SLOT_CLAIM_CATEGORY, slot);
        if (!extracted.isEmpty()) {
            KitchenStorageIndex.invalidate(level, slot.pos());
        }
        return extracted;
    }

    private static ItemStack extractUpTo(ServerLevel level, NearbyItemSources.ContainerSlot slotRef, int count) {
        if (slotRef == null || count <= 0 || slotRef.slot() < 0 || slotRef.pos() == null) return ItemStack.EMPTY;

        if (slotRef.isItemHandler()) {
            BlockEntity be = level.getBlockEntity(slotRef.pos());
            if (be == null) return ItemStack.EMPTY;
            Direction side = slotRef.side();
            IItemHandler handler = side != null ? StationHandler.getItemHandler(be, level, slotRef.pos(), side) : null;
            ItemStack extracted = extractUpToFromHandler(handler, slotRef.slot(), count);
            if (!extracted.isEmpty()) return extracted;

            handler = StationHandler.getItemHandler(be, level, slotRef.pos(), null);
            extracted = extractUpToFromHandler(handler, slotRef.slot(), count);
            if (!extracted.isEmpty()) return extracted;

            for (Direction dir : Direction.values()) {
                if (side != null && dir == side) continue;
                handler = StationHandler.getItemHandler(be, level, slotRef.pos(), dir);
                extracted = extractUpToFromHandler(handler, slotRef.slot(), count);
                if (!extracted.isEmpty()) return extracted;
            }
            return ItemStack.EMPTY;
        }

        Container container = slotRef.container();
        if (container == null || slotRef.slot() >= container.getContainerSize()) return ItemStack.EMPTY;
        ItemStack stack = container.getItem(slotRef.slot());
        if (stack.isEmpty()) return ItemStack.EMPTY;
        int moved = Math.min(count, stack.getCount());
        ItemStack extracted = StationHandler.copyWithCount(stack, moved);
        stack.shrink(moved);
        container.setChanged();
        return extracted;
    }

    private static ItemStack extractUpToFromHandler(IItemHandler handler, int slot, int count) {
        if (handler == null || slot < 0 || slot >= handler.getSlots() || count <= 0) return ItemStack.EMPTY;
        return handler.extractItem(slot, count, false);
    }

    private static int stageExtractedIngredient(ServerLevel level, BlockPos stationAnchor, ItemStack extracted) {
        if (extracted.isEmpty()) return 0;
        int inserted = 0;
        while (inserted < extracted.getCount()) {
            ItemStack single = StationHandler.copyOne(extracted);
            if (!StationHandler.insertIntoCookingPotNextIngredientSlot(level, stationAnchor, single)) break;
            inserted++;
        }
        return inserted;
    }

    private static String describeExtractionPlan(KitchenStorageIndex.ExtractionPlan plan) {
        if (plan == null) return "<none>";
        if (plan.slots().isEmpty()) return "total=" + plan.totalAvailable() + " slots=<none>";
        List<String> parts = new ArrayList<>();
        for (KitchenStorageIndex.PlannedExtraction extraction : plan.slots()) {
            parts.add(extraction.itemId() + " x" + extraction.count() + " from " + describeContainerSlot(extraction.slot()));
        }
        return "total=" + plan.totalAvailable() + " slots=" + String.join(", ", parts);
    }

    private static String describeContainerSlot(@Nullable NearbyItemSources.ContainerSlot slot) {
        if (slot == null || slot.pos() == null) return "<none>";
        String mode = slot.isItemHandler() ? "handler" : "container";
        return mode + "@" + slot.pos().getX() + "," + slot.pos().getY() + "," + slot.pos().getZ()
                + "#slot" + slot.slot()
                + (slot.side() == null ? "" : ":" + slot.side().getName());
    }

    private static String describeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "<empty>";
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (itemId == null ? stack.getItem().toString() : itemId.toString()) + " x" + stack.getCount();
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
        NearbyItemSources.ContainerSlot slot = findClaimedKitchenStorageSlot(level, villager, s -> s.is(item), kitchenBounds);
        if (slot == null) {
            slot = findKitchenStorageSlotLive(level, villager, s -> s.is(item), kitchenBounds);
            if (slot == null) return false;
        }
        ItemStack extracted = NearbyItemSources.extractOne(level, slot);
        ConsumableTargetClaims.releaseSlot(level, villager.getUUID(), KITCHEN_SLOT_CLAIM_CATEGORY, slot);
        if (extracted.isEmpty()) {
            slot = findKitchenStorageSlotLive(level, villager, s -> s.is(item), kitchenBounds);
            if (slot == null) return false;
            extracted = NearbyItemSources.extractOne(level, slot);
        }
        if (extracted.isEmpty()) return false;
        KitchenStorageIndex.invalidate(level, slot.pos());
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
        NearbyItemSources.ContainerSlot slot = findClaimedKitchenStorageSlot(level, villager, matcher, kitchenBounds);
        if (slot == null) {
            slot = findKitchenStorageSlotLive(level, villager, matcher, kitchenBounds);
            if (slot == null) return false;
        }
        ItemStack extracted = NearbyItemSources.extractOne(level, slot);
        ConsumableTargetClaims.releaseSlot(level, villager.getUUID(), KITCHEN_SLOT_CLAIM_CATEGORY, slot);
        if (extracted.isEmpty()) {
            slot = findKitchenStorageSlotLive(level, villager, matcher, kitchenBounds);
            if (slot == null) return false;
            extracted = NearbyItemSources.extractOne(level, slot);
        }
        if (extracted.isEmpty()) return false;
        KitchenStorageIndex.invalidate(level, slot.pos());
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
        StorageSearchContext searchContext = new StorageSearchContext(level);

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-16, -3, -16), origin.offset(16, 3, 16))) {
            if (stack.isEmpty()) break;
            if (!kitchenBounds.contains(pos.asLong())) continue;
            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            BlockEntity be = observed.blockEntity();
            if (!StationHandler.isCookStorageCandidate(level, pos, be)) continue;

            int before = stack.getCount();
            boolean mutated = false;
            if (be instanceof Container container) {
                insertIntoContainer(container, stack);
                mutated |= stack.getCount() != before;
            }
            if (!stack.isEmpty()) {
                final ItemStack[] remainingRef = new ItemStack[]{stack};
                searchContext.forEachUniqueItemHandler(observed.pos(), (side, handler) -> {
                    if (remainingRef[0].isEmpty()) return;
                    ItemStack remainder = StationHandler.insertIntoHandler(handler, remainingRef[0], false);
                    int inserted = remainingRef[0].getCount() - remainder.getCount();
                    if (inserted > 0) remainingRef[0].shrink(inserted);
                });
                stack = remainingRef[0];
            }
            totalInserted += before - stack.getCount();
            mutated |= stack.getCount() != before;
            if (mutated) {
                KitchenStorageIndex.invalidate(level, observed.pos());
            }
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
        return KitchenStorageIndex.snapshot(level, villager, kitchenBounds).findBestSlot(villager, matcher);
    }

    public static String describeKitchenStorage(
            ServerLevel level,
            Set<Long> kitchenBounds,
            @Nullable Set<ResourceLocation> highlightIds
    ) {
        if (level == null || kitchenBounds == null || kitchenBounds.isEmpty()) return "<none>";
        List<String> entries = new ArrayList<>();
        int listed = 0;
        for (long key : kitchenBounds) {
            if (listed >= 12) break;
            BlockPos pos = BlockPos.of(key);
            BlockEntity be = level.getBlockEntity(pos);
            if (!StationHandler.isCookStorageCandidate(level, pos, be)) continue;

            List<String> contents = new ArrayList<>();
            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (stack.isEmpty()) continue;
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (highlightIds != null && id != null && !highlightIds.contains(id)) continue;
                    contents.add(stack.getHoverName().getString() + " x" + stack.getCount());
                    if (contents.size() >= 4) break;
                }
            }
            if (contents.isEmpty()) continue;
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            entries.add((blockId == null ? "block" : blockId.getPath()) + "@"
                    + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + "=" + String.join(", ", contents));
            listed++;
        }
        if (entries.isEmpty()) return "<none>";
        return String.join("; ", entries);
    }

    private static @Nullable NearbyItemSources.ContainerSlot findKitchenStorageSlotLive(
            ServerLevel level,
            VillagerEntityMCA villager,
            java.util.function.Predicate<ItemStack> matcher,
            Set<Long> kitchenBounds
    ) {
        NearbyItemSources.ContainerSlot[] bestRef = new NearbyItemSources.ContainerSlot[1];
        StorageSearchContext searchContext = new StorageSearchContext(level);
        Set<Long> visited = new HashSet<>();
        for (BlockPos pos : KitchenStorageIndex.candidateStoragePositions(level, kitchenBounds)) {
            if (!visited.add(pos.asLong())) continue;
            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            BlockEntity be = observed.blockEntity();
            if (be == null) continue;
            if (!StationHandler.isCookStorageCandidate(level, observed.pos(), be)) continue;

            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!matcher.test(stack)) continue;
                    int score = stack.getCount();
                    double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (isBetterSlot(bestRef[0], dist, score)) {
                        bestRef[0] = new NearbyItemSources.ContainerSlot(observed.pos().immutable(), container, false, i, score, dist, null);
                    }
                }
                continue;
            }

            searchContext.forEachUniqueItemHandler(observed.pos(), (side, handler) -> {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!matcher.test(stack)) continue;
                    int score = stack.getCount();
                    double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (isBetterSlot(bestRef[0], dist, score)) {
                        bestRef[0] = new NearbyItemSources.ContainerSlot(observed.pos().immutable(), null, true, i, score, dist, side);
                    }
                }
            });
        }
        return bestRef[0];
    }

    private static int countKitchenStorageLive(
            ServerLevel level,
            VillagerEntityMCA villager,
            java.util.function.Predicate<ItemStack> matcher,
            Set<Long> kitchenBounds,
            int maxNeeded
    ) {
        if (level == null || villager == null || matcher == null || kitchenBounds == null || kitchenBounds.isEmpty() || maxNeeded <= 0) {
            return 0;
        }
        int total = 0;
        StorageSearchContext searchContext = new StorageSearchContext(level);
        Set<Long> visited = new HashSet<>();
        for (BlockPos pos : KitchenStorageIndex.candidateStoragePositions(level, kitchenBounds)) {
            if (!visited.add(pos.asLong())) continue;
            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            BlockEntity be = observed.blockEntity();
            if (be == null) continue;
            if (!StationHandler.isCookStorageCandidate(level, observed.pos(), be)) continue;

            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!matcher.test(stack)) continue;
                    total += stack.getCount();
                    if (total >= maxNeeded) return total;
                }
                continue;
            }

            final int[] totalRef = new int[]{total};
            searchContext.forEachUniqueItemHandler(observed.pos(), (side, handler) -> {
                if (totalRef[0] >= maxNeeded) return;
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (!matcher.test(stack)) continue;
                    totalRef[0] += stack.getCount();
                    if (totalRef[0] >= maxNeeded) return;
                }
            });
            total = totalRef[0];
            if (total >= maxNeeded) return total;
        }
        return total;
    }

    private static int countIngredientLive(
            ServerLevel level,
            VillagerEntityMCA villager,
            RecipeIngredient ingredient,
            Set<Long> kitchenBounds,
            int needed
    ) {
        if (ingredient == null || needed <= 0) return 0;
        int total = 0;
        for (ResourceLocation id : ingredient.itemIds()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            total += countKitchenStorageLive(level, villager, stack -> stack.is(item), kitchenBounds, needed - total);
            if (total >= needed) return total;
        }
        return total;
    }

    private static @Nullable NearbyItemSources.ContainerSlot findClaimedKitchenStorageSlot(
            ServerLevel level,
            VillagerEntityMCA villager,
            java.util.function.Predicate<ItemStack> matcher,
            Set<Long> kitchenBounds
    ) {
        NearbyItemSources.ContainerSlot slot = findKitchenStorageSlot(level, villager, matcher, kitchenBounds);
        if (slot == null) return null;
        if (ConsumableTargetClaims.isClaimedByOtherSlot(level, villager.getUUID(), KITCHEN_SLOT_CLAIM_CATEGORY, slot)) {
            return null;
        }
        long claimUntil = level.getGameTime() + KITCHEN_SLOT_CLAIM_TTL_TICKS;
        if (!ConsumableTargetClaims.tryClaimSlot(level, villager.getUUID(), KITCHEN_SLOT_CLAIM_CATEGORY, slot, claimUntil)) {
            return null;
        }
        return slot;
    }

    // ── Knife / water availability ──

    public static boolean recipeToolAvailable(ServerLevel level, VillagerEntityMCA villager, DiscoveredRecipe recipe, Set<Long> kitchenBounds) {
        return recipeToolAvailable(level, villager, recipe, kitchenBounds, KitchenStorageIndex.snapshot(level, villager, kitchenBounds), true);
    }

    private static boolean recipeToolAvailable(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            Set<Long> kitchenBounds,
            KitchenStorageIndex.Snapshot kitchenSnapshot,
            boolean includeLiveFallback
    ) {
        if (villagerHasRecipeTool(villager, recipe)) return true;
        java.util.function.Predicate<ItemStack> matcher = stack -> ModRecipeRegistry.recipeToolMatches(recipe, stack);
        if (kitchenSnapshot.findBestSlot(villager, matcher) != null) return true;
        if (!includeLiveFallback) return false;
        return findKitchenStorageSlotLive(level, villager, matcher, kitchenBounds) != null;
    }

    private static boolean villagerHasRecipeTool(VillagerEntityMCA villager, DiscoveredRecipe recipe) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (ModRecipeRegistry.recipeToolMatches(recipe, inv.getItem(i))) return true;
        }
        return false;
    }

    public static boolean waterAvailable(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos center, Set<Long> kitchenBounds) {
        if (hasItem(villager.getInventory(), Items.WATER_BUCKET)) return true;
        BlockPos searchCenter = center != null ? center : villager.blockPosition();
        if (findKitchenStorageSlot(level, villager, s -> s.is(Items.WATER_BUCKET), kitchenBounds) != null) return true;
        if (findKitchenStorageSlotLive(level, villager, s -> s.is(Items.WATER_BUCKET), kitchenBounds) != null) return true;
        boolean hasBucket = hasItem(villager.getInventory(), Items.BUCKET)
                || findKitchenStorageSlot(level, villager, s -> s.is(Items.BUCKET), kitchenBounds) != null
                || findKitchenStorageSlotLive(level, villager, s -> s.is(Items.BUCKET), kitchenBounds) != null;
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

    private static String formatMissingRequirements(Map<String, Integer> missing) {
        if (missing.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            int count = entry.getValue();
            if (count > 1) {
                parts.add(count + " " + entry.getKey());
            } else {
                parts.add(entry.getKey());
            }
        }
        return String.join(", ", parts);
    }

    private static String ingredientDisplayName(RecipeIngredient ingredient, @Nullable ResourceLocation preferredId) {
        if (preferredId != null) {
            Item preferred = BuiltInRegistries.ITEM.get(preferredId);
            if (preferred != Items.AIR) return itemDisplayName(preferred);
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ResourceLocation id : ingredient.itemIds()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) continue;
            names.add(itemDisplayName(item));
        }
        if (names.isEmpty()) return ingredient.primaryId().toString();
        if (names.size() == 1) return names.iterator().next();
        return "one of: " + String.join(" / ", names);
    }

    private static String itemDisplayName(Item item) {
        return item.getDefaultInstance().getHoverName().getString();
    }

    private static String townsteadStationLoadDetail(DiscoveredRecipe recipe) {
        if (recipe == null) return "that recipe";
        Item outputItem = BuiltInRegistries.ITEM.get(recipe.output());
        if (outputItem != Items.AIR) {
            return itemDisplayName(outputItem);
        }
        return recipe.output().toString();
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
