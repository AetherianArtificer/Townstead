package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectedStationPlanTest {
    @Test
    void projectionAddsOnlyMaterialDeficitsAlreadyMissingFromPublicRecipe() {
        RecipeIngredient grain = ingredient("test:grain", 2);
        RecipeIngredient yeast = ingredient("test:yeast", 1);

        List<RecipeIngredient> deficits = ProjectedStationPlan.deficits(
                List.of(ingredient("test:grain", 1)), List.of(grain, yeast));

        assertEquals(2, deficits.size());
        assertEquals(id("test:grain"), deficits.get(0).primaryId());
        assertEquals(1, deficits.get(0).count());
        assertEquals(id("test:yeast"), deficits.get(1).primaryId());
        assertEquals(1, deficits.get(1).count());
    }

    private static RecipeIngredient ingredient(String id, int count) {
        return new RecipeIngredient(List.of(id(id)), count);
    }

    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }
}
