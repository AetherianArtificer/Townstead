package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
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
        int tier = Math.max(1, FarmersDelightCookAssignment.effectiveRecipeTier(level, villager));
        return pickRecipe(level, villager, targetStationType, stationPos, kitchenBounds,
                recipeCooldownUntil, excludeBeverages, beveragesOnly, tier);
    }

    public static @Nullable DiscoveredRecipe pickRecipe(
            ServerLevel level,
            VillagerEntityMCA villager,
            StationType targetStationType,
            @Nullable BlockPos stationPos,
            Set<Long> kitchenBounds,
            Map<ResourceLocation, Long> recipeCooldownUntil,
            boolean excludeBeverages,
            boolean beveragesOnly,
            int tier
    ) {
        if (targetStationType == null) return null;
        List<DiscoveredRecipe> recipes;
        if (beveragesOnly) {
            recipes = ModRecipeRegistry.getBeverageRecipesForStation(level, targetStationType, tier);
        } else if (excludeBeverages) {
            recipes = ModRecipeRegistry.getFoodRecipesForStation(level, targetStationType, tier);
        } else {
            recipes = ModRecipeRegistry.getRecipesForStation(level, targetStationType, tier);
        }
        if (recipes.isEmpty()) return null;

        // Build output stock snapshot for scarcity scoring
        Set<ResourceLocation> trackedIds = new HashSet<>();
        Map<ResourceLocation, Integer> chainDemand = new HashMap<>();
        for (DiscoveredRecipe recipe : recipes) {
            trackedIds.add(recipe.output());
            for (RecipeIngredient ing : recipe.inputs()) {
                for (ResourceLocation id : ing.itemIds()) {
                    chainDemand.merge(id, 1, Integer::sum);
                }
            }
        }
        Map<ResourceLocation, Integer> outputStock = IngredientResolver.buildSupplySnapshot(level, villager, trackedIds, kitchenBounds);

        // Per-cook jitter seed
        long cookSeed = villager.getUUID().getLeastSignificantBits();

        // Collect all viable recipes with scores
        long now = level.getGameTime();
        List<DiscoveredRecipe> viable = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (DiscoveredRecipe recipe : recipes) {
            Long cooldownUntil = recipeCooldownUntil.get(recipe.output());
            if (cooldownUntil != null && cooldownUntil > now) continue;
            if (stationPos != null && !StationHandler.stationSupportsRecipe(level, stationPos, recipe)) continue;
            if (!IngredientResolver.canFulfill(level, villager, recipe, stationPos, kitchenBounds)) continue;
            double score = scoreRecipe(
                    recipe,
                    outputStock.getOrDefault(recipe.output(), 0),
                    chainDemand.getOrDefault(recipe.output(), 0),
                    cookSeed
            );
            viable.add(recipe);
            scores.add(score);
        }
        if (viable.isEmpty()) return null;
        if (viable.size() == 1) return viable.get(0);

        // Weighted random: convert scores to probabilities via softmax-like weighting
        // Higher scored recipes are much more likely but not guaranteed
        return weightedRandomPick(viable, scores);
    }

    public static double scoreRecipe(
            DiscoveredRecipe recipe,
            int currentStock,
            int chainDemand,
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

        // ── Primary factor: least-stocked items scored highest ──
        // Items with 0 stock get maximum scarcity bonus (~30 points).
        // Each existing item in stock reduces the bonus steeply.
        // This ensures cooks prioritize variety over repeating the same dish.
        double scarcityBonus = Math.max(0.0d, 30.0d - currentStock * 2.5d);

        // Chain demand: if this output is an ingredient for other recipes, boost it
        if (chainDemand > 0) scarcityBonus += Math.min(8.0d, chainDemand * 1.5d);

        // ── Secondary factor: recipe quality (nutrition, complexity) ──
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

        // Quality score is secondary — kept small relative to scarcity
        double qualityScore = nutrition * 0.5d + saturation * 1.0d;
        qualityScore += recipe.outputCount() * 0.15d;
        qualityScore += Math.max(0, recipe.tier() - 1) * 0.75d;

        // Small complexity cost
        qualityScore -= recipe.inputs().size() * 0.1d;
        if (recipe.requiresTool()) qualityScore -= 0.1d;

        // ── Combine: scarcity dominates, quality is tiebreaker ──
        double score = scarcityBonus + qualityScore;

        // Per-cook jitter: uses villager UUID to differentiate cooks
        score += perCookJitter(cookSeed, recipe.id()) * 3.0d;
        return score;
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
        // Shift scores so minimum is 0, then use exponential weighting
        double minScore = Double.MAX_VALUE;
        for (double s : scores) minScore = Math.min(minScore, s);
        double[] weights = new double[scores.size()];
        double totalWeight = 0;
        for (int i = 0; i < scores.size(); i++) {
            // Temperature controls randomness: lower = more deterministic
            weights[i] = Math.exp((scores.get(i) - minScore) * 0.25);
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

    private static double perCookJitter(long cookSeed, ResourceLocation recipeId) {
        long mix = cookSeed ^ recipeId.hashCode();
        long bucket = Math.floorMod(mix, 1000L);
        return bucket / 1000.0d;
    }

}
