package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.StationHandler;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;

import com.aetherianartificer.townstead.storage.WorksiteStorageIndex;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.TownsteadConfig;
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

        WorksiteStorageIndex.Snapshot kitchenSnapshot = WorksiteStorageIndex.snapshot(level, villager, kitchenBounds);
        Map<ResourceLocation, Integer> outputStock = WorkIngredients.buildSupplySnapshot(
                level, villager, planning.trackedIds(), kitchenBounds, kitchenSnapshot);
        boolean waterAvailable = WorkIngredients.waterAvailable(level, villager, null, kitchenBounds);
        long cookSeed = villager.getUUID().getLeastSignificantBits();
        long now = level.getGameTime();
        Map<ResourceLocation, Boolean> toolAvailableByRecipe = new HashMap<>();
        List<ScoredRecipe> candidates = new ArrayList<>();
        // One working copy of the stock for the whole sweep. Each candidate applies its own
        // effect, reads the result, then rolls back, so the copy is paid once instead of once
        // per recipe — the stock map is sized by every tracked id in the pack.
        Map<ResourceLocation, Integer> scratchSupply = new HashMap<>(outputStock);
        Map<ResourceLocation, Integer> priorCounts = new HashMap<>();
        for (DiscoveredRecipe recipe : recipes) {
            // Declared recipe scoping: every selection path funnels through here, so a task's
            // recipes/deny_recipes lists bind initial picks and per-station re-picks alike.
            if (!com.aetherianartificer.townstead.work.producer.ProducerTaskDeclarations
                    .allowsRecipe(villager, targetStationType, beveragesOnly, recipe)) continue;
            Long cooldownUntil = recipeCooldownUntil.get(recipe.output());
            if (cooldownUntil != null && cooldownUntil > now) continue;
            boolean candidatePlanable = WorkIngredients.canPlanWithVirtual(
                    recipe,
                    outputStock,
                    toolAvailable(level, villager, recipe, kitchenBounds, toolAvailableByRecipe),
                    waterAvailable
            );
            double chainOpportunity = 0.0d;
            if (candidatePlanable) {
                savePriorCounts(recipe, scratchSupply, priorCounts);
                WorkIngredients.applyVirtual(recipe, scratchSupply);
                chainOpportunity = computeChainOpportunity(
                        planning,
                        recipe,
                        outputStock,
                        scratchSupply,
                        level,
                        villager,
                        kitchenBounds,
                        toolAvailableByRecipe,
                        waterAvailable
                );
                restorePriorCounts(scratchSupply, priorCounts);
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
        WorksiteStorageIndex.Snapshot kitchenSnapshot = WorksiteStorageIndex.snapshot(level, villager, kitchenBounds);
        List<ScoredRecipe> viable = new ArrayList<>();
        for (ScoredRecipe candidate : candidates) {
            DiscoveredRecipe recipe = candidate.recipe();
            if (stationPos != null && !StationHandler.stationSupportsRecipe(level, stationPos, recipe)) continue;
            if (!WorkIngredients.canFulfill(level, villager, recipe, stationPos, kitchenBounds, kitchenSnapshot)) continue;
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

    /**
     * How much cooking {@code rootRecipe} would open up: the value of every follow-up that only
     * becomes plannable once its output exists.
     *
     * <p>Only follow-ups that read the root's output can qualify. {@code applyVirtual} raises
     * exactly one key (the output) and lowers the rest, and plannability rises monotonically with
     * supply, so a recipe that never reads the output cannot go from unplannable to plannable.
     * Walking that item's consumers is therefore the same answer as walking every recipe, which
     * is what this used to do once per candidate.</p>
     */
    private static double computeChainOpportunity(
            PlanningData planning,
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
        for (DiscoveredRecipe followup : planning.consumersByItem()
                .getOrDefault(rootRecipe.output(), List.of())) {
            if (followup.id().equals(rootRecipe.id())) continue;
            boolean toolAvailable = toolAvailable(level, villager, followup, kitchenBounds, toolAvailableByRecipe);
            boolean before = WorkIngredients.canPlanWithVirtual(followup, baseSupply, toolAvailable, waterAvailable);
            boolean after = WorkIngredients.canPlanWithVirtual(followup, afterSupply, toolAvailable, waterAvailable);
            if (!before && after) {
                bonus += chainRecipeValue(followup);
            }
        }
        return bonus;
    }

    /** Records the counts {@link WorkIngredients#applyVirtual} is about to move: container, inputs, output. */
    static void savePriorCounts(DiscoveredRecipe recipe,
                                Map<ResourceLocation, Integer> supply,
                                Map<ResourceLocation, Integer> out) {
        out.clear();
        if (recipe.containerItemId() != null && recipe.containerCount() > 0) {
            out.put(recipe.containerItemId(), supply.get(recipe.containerItemId()));
        }
        for (RecipeIngredient ingredient : recipe.inputs()) {
            for (ResourceLocation id : ingredient.itemIds()) {
                out.put(id, supply.get(id));
            }
        }
        out.put(recipe.output(), supply.get(recipe.output()));
    }

    /** Puts back what {@link #savePriorCounts} recorded; a null count means the id was absent. */
    static void restorePriorCounts(Map<ResourceLocation, Integer> supply,
                                   Map<ResourceLocation, Integer> prior) {
        for (Map.Entry<ResourceLocation, Integer> entry : prior.entrySet()) {
            if (entry.getValue() == null) {
                supply.remove(entry.getKey());
            } else {
                supply.put(entry.getKey(), entry.getValue());
            }
        }
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
            return WorkRecipeRegistry.getBeverageRecipesForStation(level, targetStationType);
        }
        if (excludeBeverages) {
            return WorkRecipeRegistry.getFoodRecipesForStation(level, targetStationType);
        }
        return WorkRecipeRegistry.getRecipesForStation(level, targetStationType);
    }

    private static List<DiscoveredRecipe> planningRecipes(ServerLevel level, boolean excludeBeverages, boolean beveragesOnly) {
        if (beveragesOnly) {
            return WorkRecipeRegistry.getBeverageRecipes(level);
        }
        if (excludeBeverages) {
            return WorkRecipeRegistry.getFoodRecipes(level);
        }
        return WorkRecipeRegistry.getRecipes(level);
    }

    private static PlanningData planningData(ServerLevel level, boolean excludeBeverages, boolean beveragesOnly) {
        PlanningKey key = new PlanningKey(level.dimension().location(), excludeBeverages, beveragesOnly);
        int generation = WorkRecipeRegistry.generation();
        PlanningData current = PLANNING_CACHE.get(key);
        if (current != null && current.generation() == generation) {
            return current;
        }
        List<DiscoveredRecipe> planningRecipes = planningRecipes(level, excludeBeverages, beveragesOnly);
        Set<ResourceLocation> trackedIds = new HashSet<>();
        Map<ResourceLocation, Integer> chainDemand = new HashMap<>();
        // Which recipes read a given item, so a chain lookup starts from the item rather than
        // from a sweep of the whole recipe set. Container items count as reads too.
        Map<ResourceLocation, List<DiscoveredRecipe>> consumersByItem = new HashMap<>();
        for (DiscoveredRecipe recipe : planningRecipes) {
            trackedIds.add(recipe.output());
            if (recipe.containerItemId() != null) {
                trackedIds.add(recipe.containerItemId());
                consumersByItem.computeIfAbsent(recipe.containerItemId(), unused -> new ArrayList<>()).add(recipe);
            }
            for (RecipeIngredient ingredient : recipe.inputs()) {
                for (ResourceLocation id : ingredient.itemIds()) {
                    trackedIds.add(id);
                    chainDemand.merge(id, 1, Integer::sum);
                    consumersByItem.computeIfAbsent(id, unused -> new ArrayList<>()).add(recipe);
                }
            }
        }
        PlanningData rebuilt = new PlanningData(
                List.copyOf(planningRecipes),
                Set.copyOf(trackedIds),
                Map.copyOf(chainDemand),
                Map.copyOf(consumersByItem),
                generation
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
                unused -> WorkIngredients.recipeToolAvailable(level, villager, recipe, kitchenBounds)
        );
    }

    private record PlanningKey(ResourceLocation dimension, boolean excludeBeverages, boolean beveragesOnly) {}

    private record PlanningData(
            List<DiscoveredRecipe> recipes,
            Set<ResourceLocation> trackedIds,
            Map<ResourceLocation, Integer> chainDemand,
            Map<ResourceLocation, List<DiscoveredRecipe>> consumersByItem,
            int generation
    ) {}

}
