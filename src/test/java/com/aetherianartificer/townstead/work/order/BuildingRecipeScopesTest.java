package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingRecipeScopesTest {

    @AfterEach
    void clear() {
        BuildingRecipeScopes.replaceAll(Map.of());
    }

    @Test
    void specialistBuildingNarrowsOnlyItsOwnRecipeCatalogue() {
        BuildingRecipeScopes.replaceAll(Map.of(
                "compat/pizzadelight/pizzeria_l1", Set.of("pizzadelight")));

        assertTrue(BuildingRecipeScopes.allows("compat/pizzadelight/pizzeria_l1",
                id("pizzadelight:raw_pizza")));
        assertFalse(BuildingRecipeScopes.allows("compat/pizzadelight/pizzeria_l1",
                id("rusticdelight:cooked_calamari")));
        assertTrue(BuildingRecipeScopes.allows("compat/pizzadelight/pizzeria_l1",
                id("townstead:protocol/townstead/pizza_station/0")),
                "the building's own physical station protocols remain visible");
        assertTrue(BuildingRecipeScopes.allows("compat/farmersdelight/kitchen_l1",
                id("rusticdelight:cooked_calamari")),
                "buildings without an authored scope remain open");
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
