package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.work.recipe.WorkIngredients;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two invariants the candidate sweep relies on to avoid copying the stock snapshot once per
 * recipe and to avoid re-walking every recipe once per candidate. Both are properties of
 * {@code applyVirtual}, so they are asserted directly rather than through a live level.
 */
class RecipeChainPlanningTest {

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static DiscoveredRecipe recipe(String recipeId, String output, List<String> inputs,
                                           String containerId, int containerCount) {
        List<RecipeIngredient> ingredients = inputs.stream()
                .map(in -> new RecipeIngredient(List.of(id(in)), 1))
                .toList();
        return new DiscoveredRecipe(
                id(recipeId), StationType.HOT_STATION, 0, id(output), 1, 200, false,
                containerId == null ? null : id(containerId), containerCount,
                ingredients, false, false, null);
    }

    private static DiscoveredRecipe recipe(String recipeId, String output, List<String> inputs) {
        return recipe(recipeId, output, inputs, null, 0);
    }

    @Test
    void rollbackRestoresTheStockSnapshotExactly() {
        DiscoveredRecipe stew = recipe("test:stew", "minecraft:beef_stew",
                List.of("minecraft:beef", "minecraft:carrot"), "minecraft:bowl", 1);

        Map<ResourceLocation, Integer> stock = new HashMap<>();
        stock.put(id("minecraft:beef"), 3);
        stock.put(id("minecraft:carrot"), 2);
        stock.put(id("minecraft:bowl"), 4);
        stock.put(id("minecraft:bread"), 7);
        Map<ResourceLocation, Integer> expected = Map.copyOf(stock);

        Map<ResourceLocation, Integer> prior = new HashMap<>();
        RecipeSelector.savePriorCounts(stew, stock, prior);
        WorkIngredients.applyVirtual(stew, stock);

        // The output was absent before, so a correct rollback must remove it, not zero it.
        assertTrue(stock.containsKey(id("minecraft:beef_stew")), "apply should add the output");

        RecipeSelector.restorePriorCounts(stock, prior);
        assertEquals(expected, stock, "rollback should leave the snapshot byte-for-byte as it was");
    }

    @Test
    void rollbackRestoresOutputCountWhenSomeAlreadyInStock() {
        DiscoveredRecipe bread = recipe("test:bread", "minecraft:bread", List.of("minecraft:wheat"));

        Map<ResourceLocation, Integer> stock = new HashMap<>();
        stock.put(id("minecraft:wheat"), 5);
        stock.put(id("minecraft:bread"), 2);
        Map<ResourceLocation, Integer> expected = Map.copyOf(stock);

        Map<ResourceLocation, Integer> prior = new HashMap<>();
        RecipeSelector.savePriorCounts(bread, stock, prior);
        WorkIngredients.applyVirtual(bread, stock);
        RecipeSelector.restorePriorCounts(stock, prior);

        assertEquals(expected, stock, "an output already in stock should be restored to its old count");
    }

    @Test
    void onlyConsumersOfTheOutputCanBecomeNewlyPlannable() {
        // Cooking bread consumes wheat and yields bread.
        DiscoveredRecipe bread = recipe("test:bread", "minecraft:bread", List.of("minecraft:wheat"));
        // A follow-up that reads bread: the one that may flip once bread exists.
        DiscoveredRecipe sandwich = recipe("test:sandwich", "test:sandwich_item",
                List.of("minecraft:bread", "minecraft:beef"));
        // A follow-up that reads none of bread's output: must not flip either way.
        DiscoveredRecipe stew = recipe("test:stew", "minecraft:beef_stew",
                List.of("minecraft:beef", "minecraft:carrot"));

        Map<ResourceLocation, Integer> before = new HashMap<>();
        before.put(id("minecraft:wheat"), 3);
        before.put(id("minecraft:beef"), 1);

        Map<ResourceLocation, Integer> after = new HashMap<>(before);
        WorkIngredients.applyVirtual(bread, after);

        assertFalse(WorkIngredients.canPlanWithVirtual(sandwich, before, true, true),
                "sandwich should not be plannable before bread exists");
        assertTrue(WorkIngredients.canPlanWithVirtual(sandwich, after, true, true),
                "sandwich should become plannable once bread exists");

        // The non-consumer is unplannable either way: cooking bread cannot open it up, which is
        // why the sweep may skip every recipe that does not read the root's output.
        assertEquals(
                WorkIngredients.canPlanWithVirtual(stew, before, true, true),
                WorkIngredients.canPlanWithVirtual(stew, after, true, true),
                "a recipe that never reads the output must not change plannability");
    }

    @Test
    void consumingInputsNeverMakesAnUnplannableRecipePlannable() {
        DiscoveredRecipe bread = recipe("test:bread", "minecraft:bread", List.of("minecraft:wheat"));
        // Competes for wheat, and does not read bread.
        DiscoveredRecipe cake = recipe("test:cake", "minecraft:cake", List.of("minecraft:wheat"));

        Map<ResourceLocation, Integer> before = new HashMap<>();
        before.put(id("minecraft:wheat"), 1);

        Map<ResourceLocation, Integer> after = new HashMap<>(before);
        WorkIngredients.applyVirtual(bread, after);

        assertTrue(WorkIngredients.canPlanWithVirtual(cake, before, true, true));
        assertFalse(WorkIngredients.canPlanWithVirtual(cake, after, true, true),
                "spending the shared wheat should only ever remove options, never add them");
    }

    @Test
    void repeatedRecipePositionsRequireTheFullRepeatedSupply() {
        DiscoveredRecipe coffee = recipe("test:coffee", "test:coffee_cup",
                List.of("test:bean", "test:bean", "test:bean", "test:bean"),
                "minecraft:glass_bottle", 1);

        Map<ResourceLocation, Integer> shortStock = new HashMap<>();
        shortStock.put(id("test:bean"), 1);
        shortStock.put(id("minecraft:glass_bottle"), 1);
        assertFalse(WorkIngredients.canPlanWithVirtual(coffee, shortStock, true, true),
                "one bean cannot satisfy four recipe positions");

        Map<ResourceLocation, Integer> exactStock = new HashMap<>(shortStock);
        exactStock.put(id("test:bean"), 4);
        assertTrue(WorkIngredients.canPlanWithVirtual(coffee, exactStock, true, true));
    }
}
