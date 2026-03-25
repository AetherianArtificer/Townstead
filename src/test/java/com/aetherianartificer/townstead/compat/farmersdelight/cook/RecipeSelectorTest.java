package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSelectorTest {

    @Test
    void scoreRecipePrefersHigherQualityMealsOverSimpleFiller() {
        double fillerScore = RecipeScoring.scoreRecipeModel(
                false, 1, 100, false, 0, 1, "baked_potato".hashCode(),
                5.0d, 0.6d, 0, 0, 0.0d, 42L
        );
        double mealScore = RecipeScoring.scoreRecipeModel(
                false, 4, 200, false, 1, 1, "rabbit_stew".hashCode(),
                10.0d, 0.6d, 0, 0, 0.0d, 42L
        );

        assertTrue(mealScore > fillerScore, "richer meal should outscore simple filler");
    }

    @Test
    void scoreRecipePenalizesRepeatedStock() {
        double lowStock = RecipeScoring.scoreRecipeModel(
                false, 1, 100, false, 0, 1, "cooked_chicken".hashCode(),
                6.0d, 0.6d, 0, 0, 0.0d, 77L
        );
        double highStock = RecipeScoring.scoreRecipeModel(
                false, 1, 100, false, 0, 1, "cooked_chicken".hashCode(),
                6.0d, 0.6d, 6, 0, 0.0d, 77L
        );

        assertTrue(lowStock > highStock, "overstocked outputs should be deprioritized");
    }

    @Test
    void scoreRecipeRewardsChainOpportunity() {
        double noChain = RecipeScoring.scoreRecipeModel(
                false, 1, 80, true, 0, 1, "cut_cooked_chicken".hashCode(),
                6.0d, 0.6d, 0, 0, 0.0d, 11L
        );
        double withChain = RecipeScoring.scoreRecipeModel(
                false, 1, 80, true, 0, 1, "cut_cooked_chicken".hashCode(),
                6.0d, 0.6d, 0, 0, 6.0d, 11L
        );

        assertTrue(withChain > noChain, "recipes that unlock follow-up work should gain score");
    }

    @Test
    void scoreRecipeRewardsComplexityWhenOutputIsEqual() {
        double simpleScore = RecipeScoring.scoreRecipeModel(
                false, 2, 100, false, 1, 1, "simple_soup".hashCode(),
                6.0d, 0.6d, 0, 0, 0.0d, 101L
        );
        double richerScore = RecipeScoring.scoreRecipeModel(
                false, 4, 200, true, 1, 3, "richer_soup".hashCode(),
                6.0d, 0.6d, 0, 0, 0.0d, 101L
        );

        assertTrue(richerScore > simpleScore, "more complex recipe should beat simpler recipe for same output");
    }
}
