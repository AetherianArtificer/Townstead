package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecursiveDemandPlannerTest {
    @Test
    void plansAThreeStageDrinkInDependencyOrder() {
        DiscoveredRecipe roast = recipe("test:roast", "test:roasted_beans", 2,
                ingredient("test:green_beans", 2));
        DiscoveredRecipe brew = recipe("test:brew", "test:coffee_pot", 4,
                ingredient("test:roasted_beans", 2));
        DiscoveredRecipe plate = recipe("test:cup", "test:coffee_cup", 1,
                ingredient("test:coffee_pot", 1), ingredient("test:empty_cup", 1));

        RecursiveDemandPlanner.Plan plan = RecursiveDemandPlanner.plan(
                List.of(plate, brew, roast),
                Map.of(id("test:green_beans"), 2, id("test:empty_cup"), 1),
                id("test:coffee_cup"), 1);

        assertTrue(plan.succeeded(), () -> String.valueOf(plan.failure()));
        assertEquals(List.of(id("test:roast"), id("test:brew"), id("test:cup")),
                plan.steps().stream().map(step -> step.recipe().id()).toList());
        assertEquals(1, plan.stock().get(id("test:coffee_cup")));
        assertEquals(3, plan.stock().get(id("test:coffee_pot")),
                "unused multi-serving output remains visible to later demand");
    }

    @Test
    void choosesAReachableAlternativeAndExplainsMissingInputs() {
        DiscoveredRecipe tea = recipe("test:tea", "test:tea", 1,
                new RecipeIngredient(List.of(id("test:mint"), id("test:nettle")), 1));

        RecursiveDemandPlanner.Plan reachable = RecursiveDemandPlanner.plan(
                List.of(tea), Map.of(id("test:nettle"), 1), id("test:tea"), 1);
        assertTrue(reachable.succeeded());

        RecursiveDemandPlanner.Plan missing = RecursiveDemandPlanner.plan(
                List.of(tea), Map.of(), id("test:tea"), 1);
        assertFalse(missing.succeeded());
        assertEquals(RecursiveDemandPlanner.FailureKind.NO_RECIPE, missing.failure().kind());
        assertTrue(missing.failure().path().contains(id("test:mint"))
                || missing.failure().path().contains(id("test:nettle")));
    }

    @Test
    void detectsCyclesAndHonorsStockWithoutProducingUnneededStages() {
        DiscoveredRecipe a = recipe("test:a", "test:a", 1, ingredient("test:b", 1));
        DiscoveredRecipe b = recipe("test:b", "test:b", 1, ingredient("test:a", 1));

        RecursiveDemandPlanner.Plan cycle = RecursiveDemandPlanner.plan(
                List.of(a, b), Map.of(), id("test:a"), 1);
        assertFalse(cycle.succeeded());
        assertEquals(RecursiveDemandPlanner.FailureKind.CYCLE, cycle.failure().kind());

        RecursiveDemandPlanner.Plan stocked = RecursiveDemandPlanner.plan(
                List.of(a, b), Map.of(id("test:a"), 1), id("test:a"), 1);
        assertTrue(stocked.succeeded());
        assertTrue(stocked.steps().isEmpty());
    }

    @Test
    void readinessAndBoundsFailClosedWithUsefulReasons() {
        DiscoveredRecipe tea = recipe("test:tea", "test:tea", 1,
                ingredient("test:leaf", 1));
        RecursiveDemandPlanner.Plan notReady = RecursiveDemandPlanner.plan(
                List.of(tea), Map.of(id("test:leaf"), 1), id("test:tea"), 1,
                recipe -> false, RecursiveDemandPlanner.Limits.defaults());
        assertEquals(RecursiveDemandPlanner.FailureKind.RECIPE_NOT_READY,
                notReady.failure().kind());

        RecursiveDemandPlanner.Plan bounded = RecursiveDemandPlanner.plan(
                List.of(tea), Map.of(), id("test:tea"), 1,
                recipe -> true, new RecursiveDemandPlanner.Limits(1, 10));
        assertEquals(RecursiveDemandPlanner.FailureKind.DEPTH_LIMIT,
                bounded.failure().kind());
    }

    @Test
    void choosesTheCheapestReachableAlternateAndRejectsExactInputs() {
        DiscoveredRecipe slow = new DiscoveredRecipe(id("test:slow"), StationType.HOT_STATION,
                4, id("test:syrup"), 1, 1200, true, null, 0,
                List.of(ingredient("test:rare_leaf", 1)), false, true, null);
        DiscoveredRecipe quick = recipe("test:quick", "test:syrup", 1,
                ingredient("test:common_leaf", 1));
        RecursiveDemandPlanner.Plan alternate = RecursiveDemandPlanner.plan(
                List.of(slow, quick), Map.of(id("test:rare_leaf"), 1,
                        id("test:common_leaf"), 1), id("test:syrup"), 1);
        assertTrue(alternate.succeeded());
        assertEquals(id("test:quick"), alternate.steps().get(0).recipe().id());

        RecipeIngredient exact = new RecipeIngredient(List.of(id("test:blank_cup")), 1,
                null, id("test:decorated_cup"));
        DiscoveredRecipe decorated = recipe("test:decorate", "test:served_tea", 1, exact);
        RecursiveDemandPlanner.Plan refused = RecursiveDemandPlanner.plan(
                List.of(decorated), Map.of(id("test:blank_cup"), 1),
                id("test:served_tea"), 1);
        assertFalse(refused.succeeded());
        assertEquals(RecursiveDemandPlanner.FailureKind.EXACT_PRODUCT_UNSUPPORTED,
                refused.failure().kind());
    }

    private static DiscoveredRecipe recipe(String recipeId, String output, int outputCount,
                                           RecipeIngredient... inputs) {
        return new DiscoveredRecipe(id(recipeId), StationType.HOT_STATION, 1, id(output),
                outputCount, 200, false, null, 0, List.of(inputs),
                false, true, null);
    }

    private static RecipeIngredient ingredient(String item, int count) {
        return new RecipeIngredient(List.of(id(item)), count);
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
