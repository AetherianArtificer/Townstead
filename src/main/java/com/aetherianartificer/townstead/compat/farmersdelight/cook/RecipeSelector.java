package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.RecipeIngredient;
import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.StationType;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RecipeSelector {
    private static final long PLANNING_CACHE_TTL_TICKS = 40L;
    private static final Map<PlanningKey, PlanningData> PLANNING_CACHE = new HashMap<>();

    public record ScoredRecipe(DiscoveredRecipe recipe, double score) {}

    private RecipeSelector() {}

    public static @Nullable DiscoveredRecipe pickRecipe(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType targetStationType,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds,
            Map<ResourceLocation, Long> recipeCooldownUntil
    ) {
        return pickRecipe(level, villager, targetStationType, stationPos, kitchenBounds, recipeCooldownUntil, false, false);
    }

    public static @Nullable DiscoveredRecipe pickRecipe(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType targetStationType,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds,
            Map<ResourceLocation, Long> recipeCooldownUntil,
            boolean excludeBeverages,
            boolean beveragesOnly
    ) {
        List<ScoredRecipe> viableRecipes = viableRecipes(
                level, villager, targetStationType, stationPos, kitchenBounds, recipeCooldownUntil, excludeBeverages, beveragesOnly);
        if (viableRecipes.isEmpty()) return null;
        if (viableRecipes.size() == 1) return viableRecipes.get(0).recipe();
        List<DiscoveredRecipe> viable = viableRecipes.stream().map(ScoredRecipe::recipe).toList();
        List<Double> scores = viableRecipes.stream().map(ScoredRecipe::score).toList();
        return weightedRandomPick(viable, scores);
    }

    public static List<ScoredRecipe> candidateRecipes(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType targetStationType,
            Set<Long> kitchenBounds,
            Map<ResourceLocation, Long> recipeCooldownUntil,
            boolean excludeBeverages,
            boolean beveragesOnly
    ) {
        if (targetStationType == null) return List.of();
        List<DiscoveredRecipe> recipes = stationRecipes(level, targetStationType, excludeBeverages, beveragesOnly);
        if (recipes.isEmpty()) return List.of();

        PlanningData planning = planningData(level, excludeBeverages, beveragesOnly);

        KitchenStorageIndex.Snapshot kitchenSnapshot = KitchenStorageIndex.snapshot(level, villager, kitchenBounds);
        Map<ResourceLocation, Integer> outputStock = IngredientResolver.buildSupplySnapshot(
                level, villager, planning.trackedIds(), kitchenBounds, kitchenSnapshot);
        boolean waterAvailable = IngredientResolver.waterAvailable(level, villager, null, kitchenBounds);
        long cookSeed = villager.getUUID().getLeastSignificantBits();
        long now = level.getGameTime();
        Map<ResourceLocation, Boolean> toolAvailableByRecipe = new HashMap<>();
        List<ScoredRecipe> candidates = new ArrayList<>();
        for (DiscoveredRecipe recipe : recipes) {
            Long cooldownUntil = recipeCooldownUntil.get(recipe.output());
            if (cooldownUntil != null && cooldownUntil > now) continue;
            Map<ResourceLocation, Integer> virtualSupply = new HashMap<>(outputStock);
            boolean candidatePlanable = IngredientResolver.canPlanWithVirtual(
                    recipe,
                    virtualSupply,
                    toolAvailable(level, villager, recipe, kitchenBounds, toolAvailableByRecipe),
                    waterAvailable
            );
            double chainOpportunity = 0.0d;
            if (candidatePlanable) {
                IngredientResolver.applyVirtual(recipe, virtualSupply);
                chainOpportunity = computeChainOpportunity(
                        planning.recipes(),
                        recipe,
                        outputStock,
                        virtualSupply,
                        level,
                        villager,
                        kitchenBounds,
                        toolAvailableByRecipe,
                        waterAvailable
                );
            }
            double score = scoreRecipe(
                    recipe,
                    outputStock.getOrDefault(recipe.output(), 0),
                    planning.chainDemand().getOrDefault(recipe.output(), 0),
                    chainOpportunity,
                    cookSeed
            );
            candidates.add(new ScoredRecipe(recipe, score));
        }
        return List.copyOf(candidates);
    }

    public static List<ScoredRecipe> viableRecipes(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType targetStationType,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds,
            Map<ResourceLocation, Long> recipeCooldownUntil,
            boolean excludeBeverages,
            boolean beveragesOnly
    ) {
        if (targetStationType == null) return List.of();
        List<ScoredRecipe> candidates = candidateRecipes(
                level, villager, targetStationType, kitchenBounds, recipeCooldownUntil, excludeBeverages, beveragesOnly);
        if (candidates.isEmpty()) return List.of();
        KitchenStorageIndex.Snapshot kitchenSnapshot = KitchenStorageIndex.snapshot(level, villager, kitchenBounds);
        List<ScoredRecipe> viable = new ArrayList<>();
        for (ScoredRecipe candidate : candidates) {
            DiscoveredRecipe recipe = candidate.recipe();
            if (stationPos != null && !StationHandler.stationSupportsRecipe(level, stationPos, recipe)) continue;
            if (!IngredientResolver.canFulfill(level, villager, recipe, stationPos, kitchenBounds, kitchenSnapshot)) continue;
            viable.add(candidate);
        }
        return List.copyOf(viable);
    }

    public static double scoreRecipe(
            DiscoveredRecipe recipe,
            int currentStock,
            int chainDemand,
            double chainOpportunity,
            long cookSeed
    ) {
        if (recipe.purification()) {
            ThirstCompatBridge bridge = ThirstBridgeResolver.get();
            if (bridge == null || !TownsteadConfig.isCookWaterPurificationEnabled() || !bridge.supportsPurification()) {
                return Double.NEGATIVE_INFINITY;
            }
            // Fall through to standard scoring — potions have no nutrition/saturation,
            // so scarcity will be the primary driver, competing fairly with other beverages.
        }

        // ── Primary factor: recipe quality (nutrition, meal value, complexity) ──
        Item outputItem = BuiltInRegistries.ITEM.get(recipe.output());
        ItemStack outputStack = outputItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(outputItem, recipe.outputCount());
        //? if >=1.21 {
        FoodProperties food = outputStack.isEmpty() ? null : outputStack.get(DataComponents.FOOD);
        //?} else {
        /*FoodProperties food = outputStack.isEmpty() ? null : outputStack.getFoodProperties(null);
        *///?}
        //? if >=1.21 {
        double nutrition = food != null ? food.nutrition() : 0.0d;
        double saturation = food != null ? food.saturation() : 0.0d;
        //?} else {
        /*double nutrition = food != null ? food.getNutrition() : 0.0d;
        double saturation = food != null ? food.getSaturationModifier() : 0.0d;
        *///?}

        return scoreRecipeWithFoodStats(recipe, nutrition, saturation, currentStock, chainDemand, chainOpportunity, cookSeed);
    }

    static double scoreRecipeWithFoodStats(
            DiscoveredRecipe recipe,
            double nutrition,
            double saturation,
            int currentStock,
            int chainDemand,
            double chainOpportunity,
            long cookSeed
    ) {
        return RecipeScoring.scoreRecipeModel(
                recipe.beverage(),
                recipe.inputs().size(),
                recipe.cookTimeTicks(),
                recipe.requiresTool(),
                recipe.containerCount(),
                recipe.tier(),
                recipe.id().hashCode(),
                nutrition,
                saturation,
                currentStock,
                chainDemand,
                chainOpportunity,
                cookSeed
        );
    }

    public static double recipeCooldownPenalty(
            ServerLevel level,
            DiscoveredRecipe recipe,
            Map<ResourceLocation, Long> recipeCooldownUntil
    ) {
        long now = level.getGameTime();
        Long until = recipeCooldownUntil.get(recipe.output());
        if (until == null || until <= now) return 0.0d;
        long remaining = Math.max(0L, until - now);
        double ratio = Math.min(1.0d, remaining / 200.0d);
        return 8.0d + (8.0d * ratio);
    }

    private static DiscoveredRecipe weightedRandomPick(List<DiscoveredRecipe> viable, List<Double> scores) {
        double maxScore = Double.NEGATIVE_INFINITY;
        for (double s : scores) maxScore = Math.max(maxScore, s);
        double[] weights = new double[scores.size()];
        double totalWeight = 0;
        for (int i = 0; i < scores.size(); i++) {
            // Keep choices varied, but heavily bias toward the better-scored dishes.
            double delta = scores.get(i) - maxScore;
            weights[i] = Math.exp(delta * 0.20d);
            weights[i] = Math.max(weights[i], 0.02d);
            totalWeight += weights[i];
        }
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (int i = 0; i < viable.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) return viable.get(i);
        }
        return viable.get(viable.size() - 1);
    }

    private static double computeChainOpportunity(
            List<DiscoveredRecipe> planningRecipes,
            DiscoveredRecipe rootRecipe,
            Map<ResourceLocation, Integer> baseSupply,
            Map<ResourceLocation, Integer> afterSupply,
            ServerLevel level,
            VillagerEntityMCA villager,
            Set<Long> kitchenBounds,
            Map<ResourceLocation, Boolean> toolAvailableByRecipe,
            boolean waterAvailable
    ) {
        double bonus = 0.0d;
        for (DiscoveredRecipe followup : planningRecipes) {
            if (followup.id().equals(rootRecipe.id())) continue;
            boolean toolAvailable = toolAvailable(level, villager, followup, kitchenBounds, toolAvailableByRecipe);
            boolean before = IngredientResolver.canPlanWithVirtual(followup, baseSupply, toolAvailable, waterAvailable);
            boolean after = IngredientResolver.canPlanWithVirtual(followup, afterSupply, toolAvailable, waterAvailable);
            if (!before && after) {
                bonus += chainRecipeValue(followup);
            }
        }
        return bonus;
    }

    private static double chainRecipeValue(DiscoveredRecipe recipe) {
        Item outputItem = BuiltInRegistries.ITEM.get(recipe.output());
        ItemStack outputStack = outputItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(outputItem, recipe.outputCount());
        //? if >=1.21 {
        FoodProperties food = outputStack.isEmpty() ? null : outputStack.get(DataComponents.FOOD);
        //?} else {
        /*FoodProperties food = outputStack.isEmpty() ? null : outputStack.getFoodProperties(null);
        *///?}
        //? if >=1.21 {
        double nutrition = food != null ? food.nutrition() : 0.0d;
        double saturation = food != null ? food.saturation() : 0.0d;
        //?} else {
        /*double nutrition = food != null ? food.getNutrition() : 0.0d;
        double saturation = food != null ? food.getSaturationModifier() : 0.0d;
        *///?}
        double value = nutrition * 1.5d + saturation * 3.0d + recipe.inputs().size() * 0.75d;
        if (recipe.beverage()) value += 4.0d;
        return Math.max(1.0d, value);
    }

    private static List<DiscoveredRecipe> stationRecipes(
            ServerLevel level,
            StationType targetStationType,
            boolean excludeBeverages,
            boolean beveragesOnly
    ) {
        if (beveragesOnly) {
            return ModRecipeRegistry.getBeverageRecipesForStation(level, targetStationType);
        }
        if (excludeBeverages) {
            return ModRecipeRegistry.getFoodRecipesForStation(level, targetStationType);
        }
        return ModRecipeRegistry.getRecipesForStation(level, targetStationType);
    }

    private static List<DiscoveredRecipe> planningRecipes(ServerLevel level, boolean excludeBeverages, boolean beveragesOnly) {
        if (beveragesOnly) {
            return ModRecipeRegistry.getBeverageRecipes(level);
        }
        if (excludeBeverages) {
            return ModRecipeRegistry.getFoodRecipes(level);
        }
        return ModRecipeRegistry.getRecipes(level);
    }

    private static PlanningData planningData(ServerLevel level, boolean excludeBeverages, boolean beveragesOnly) {
        PlanningKey key = new PlanningKey(level.dimension().location(), excludeBeverages, beveragesOnly);
        long now = level.getGameTime();
        PlanningData current = PLANNING_CACHE.get(key);
        if (current != null && current.expiresAt() > now) {
            return current;
        }
        List<DiscoveredRecipe> planningRecipes = planningRecipes(level, excludeBeverages, beveragesOnly);
        Set<ResourceLocation> trackedIds = new HashSet<>();
        Map<ResourceLocation, Integer> chainDemand = new HashMap<>();
        for (DiscoveredRecipe recipe : planningRecipes) {
            trackedIds.add(recipe.output());
            if (recipe.containerItemId() != null) trackedIds.add(recipe.containerItemId());
            for (RecipeIngredient ingredient : recipe.inputs()) {
                for (ResourceLocation id : ingredient.itemIds()) {
                    trackedIds.add(id);
                    chainDemand.merge(id, 1, Integer::sum);
                }
            }
        }
        PlanningData rebuilt = new PlanningData(
                List.copyOf(planningRecipes),
                Set.copyOf(trackedIds),
                Map.copyOf(chainDemand),
                now + PLANNING_CACHE_TTL_TICKS
        );
        PLANNING_CACHE.put(key, rebuilt);
        return rebuilt;
    }

    private static boolean toolAvailable(
            ServerLevel level,
            VillagerEntityMCA villager,
            DiscoveredRecipe recipe,
            Set<Long> kitchenBounds,
            Map<ResourceLocation, Boolean> toolAvailableByRecipe
    ) {
        if (!recipe.requiresTool()) return true;
        return toolAvailableByRecipe.computeIfAbsent(
                recipe.id(),
                unused -> IngredientResolver.recipeToolAvailable(level, villager, recipe, kitchenBounds)
        );
    }

    private record PlanningKey(ResourceLocation dimension, boolean excludeBeverages, boolean beveragesOnly) {}

    private record PlanningData(
            List<DiscoveredRecipe> recipes,
            Set<ResourceLocation> trackedIds,
            Map<ResourceLocation, Integer> chainDemand,
            long expiresAt
    ) {}

}
