package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionStationsTest {

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static DiscoveredRecipe recipe(
            StationType stationType, String recipeId, String output
    ) {
        return new DiscoveredRecipe(
                id(recipeId), stationType, 1, id(output), 1, 200, false,
                id("minecraft:glass_bottle"), 1,
                List.of(new RecipeIngredient(List.of(id("rusticdelight:roasted_coffee_beans")), 4)),
                false, true, null);
    }

    @Test
    void builtinCookingPotAcceptsCoffeeWithoutAProtocolAdapter() {
        DiscoveredRecipe coffee = recipe(
                StationType.HOT_STATION,
                "rusticdelight:coffee",
                "rusticdelight:coffee");

        assertTrue(ProductionStations.supportsUnadaptedRecipe(
                        StationType.HOT_STATION, null, null, true, coffee),
                "the normal cooking-pot lifecycle must not require a protocol adapter");
    }

    @Test
    void builtinStationStillRejectsTheWrongRecipeFamily() {
        DiscoveredRecipe coffee = recipe(
                StationType.HOT_STATION,
                "rusticdelight:coffee",
                "rusticdelight:coffee");

        assertFalse(ProductionStations.supportsUnadaptedRecipe(
                StationType.FIRE_STATION, null, null, true, coffee));
    }

    @Test
    void declaredRecipeTypesRemainExclusive() {
        DiscoveredRecipe customPotRecipe = recipe(
                StationType.HOT_STATION,
                "example:coffee",
                "example:coffee");

        assertTrue(ProductionStations.supportsUnadaptedRecipe(
                StationType.HOT_STATION, id("example:pot_cooking"),
                id("example:pot_cooking"), true, customPotRecipe));
        assertFalse(ProductionStations.supportsUnadaptedRecipe(
                StationType.HOT_STATION, id("example:pot_cooking"),
                null, true, customPotRecipe));
    }

    @Test
    void protocolRecipesCannotFallThroughToTheBuiltinLifecycle() {
        DiscoveredRecipe passive = recipe(
                StationType.PASSIVE_STATION,
                "example:ferment",
                "example:drink");

        assertFalse(ProductionStations.supportsUnadaptedRecipe(
                StationType.PASSIVE_STATION, null, null, true, passive));
    }
}
