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
        List<DiscoveredRecipe> recipes;
        if (beveragesOnly) {
            recipes = ModRecipeRegistry.getBeverageRecipesForStation(level, targetStationType);
        } else if (excludeBeverages) {
            recipes = ModRecipeRegistry.getFoodRecipesForStation(level, targetStationType);
        } else {
            recipes = ModRecipeRegistry.getRecipesForStation(level, targetStationType);
        }
        if (recipes.isEmpty()) return List.of();

        List<DiscoveredRecipe> planningRecipes = ModRecipeRegistry.getRecipes(level).stream()
                .filter(r -> beveragesOnly ? r.beverage() : (!excludeBeverages || !r.beverage()))
                .toList();

        Set<ResourceLocation> trackedIds = new HashSet<>();
        Map<ResourceLocation, Integer> chainDemand = new HashMap<>();
        for (DiscoveredRecipe recipe : planningRecipes) {
            trackedIds.add(recipe.output());
            if (recipe.containerItemId() != null) trackedIds.add(recipe.containerItemId());
            for (RecipeIngredient ing : recipe.inputs()) {
                for (ResourceLocation id : ing.itemIds()) {
                    trackedIds.add(id);
                    chainDemand.merge(id, 1, Integer::sum);
                }
            }
        }

        KitchenStorageIndex.Snapshot kitchenSnapshot = KitchenStorageIndex.snapshot(level, villager, kitchenBounds);
        Map<ResourceLocation, Integer> outputStock = IngredientResolver.buildSupplySnapshot(level, villager, trackedIds, kitchenBounds, kitchenSnapshot);
        boolean waterAvailable = IngredientResolver.waterAvailable(level, villager, null, kitchenBounds);
        long cookSeed = villager.getUUID().getLeastSignificantBits();
        long now = level.getGameTime();
        List<ScoredRecipe> candidates = new ArrayList<>();
        for (DiscoveredRecipe recipe : recipes) {
            Long cooldownUntil = recipeCooldownUntil.get(recipe.output());
            if (cooldownUntil != null && cooldownUntil > now) continue;
            Map<ResourceLocation, Integer> virtualSupply = new HashMap<>(outputStock);
            boolean candidatePlanable = IngredientResolver.canPlanWithVirtual(
                    recipe,
                    virtualSupply,
                    !recipe.requiresTool() || IngredientResolver.recipeToolAvailable(level, villager, recipe, kitchenBounds),
                    waterAvailable
            );
            double chainOpportunity = 0.0d;
            if (candidatePlanable) {
                IngredientResolver.applyVirtual(recipe, virtualSupply);
                chainOpportunity = computeChainOpportunity(
                        planningRecipes,
                        recipe,
                        outputStock,
                        virtualSupply,
                        level,
                        villager,
                        kitchenBounds,
                        waterAvailable
                );
            }
            double score = scoreRecipe(
                    recipe,
                    outputStock.getOrDefault(recipe.output(), 0),
                    chainDemand.getOrDefault(recipe.output(), 0),
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
            boolean waterAvailable
    ) {
        double bonus = 0.0d;
        for (DiscoveredRecipe followup : planningRecipes) {
            if (followup.id().equals(rootRecipe.id())) continue;
            boolean toolAvailable = !followup.requiresTool()
                    || IngredientResolver.recipeToolAvailable(level, villager, followup, kitchenBounds);
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

}
