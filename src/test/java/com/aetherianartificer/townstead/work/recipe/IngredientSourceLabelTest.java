package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngredientSourceLabelTest {
    @Test
    void sourceTagSurvivesMergingAndProducesANeutralRequirementName() {
        ResourceLocation dough = ResourceLocation.tryParse("forge:dough");
        ResourceLocation wheatDough = ResourceLocation.tryParse("example:wheat_dough");
        RecipeIngredient ingredient = new RecipeIngredient(List.of(wheatDough), 1, dough);

        RecipeIngredient merged = RecipeIngredient.merge(List.of(ingredient, ingredient)).get(0);
        assertEquals(dough, merged.sourceTag());
        assertEquals(2, merged.count());
        assertEquals("Dough", RequirementLabels.tagName(merged.sourceTag()));
        assertEquals("Kitchen Knife", RequirementLabels.tagName(
                ResourceLocation.tryParse("kaleidoscope_cookery:kitchen_knives")));
    }

    @Test
    void exactTagRecoveryChoosesStableCommonSemanticAlias() {
        assertEquals(ResourceLocation.tryParse("forge:dough"),
                RequirementLabels.preferredTagAlias(
                        ResourceLocation.tryParse("forge:doughs"),
                        ResourceLocation.tryParse("forge:dough")));
        assertEquals(ResourceLocation.tryParse("c:foods/dough"),
                RequirementLabels.preferredTagAlias(
                        ResourceLocation.tryParse("examplemod:dough"),
                        ResourceLocation.tryParse("c:foods/dough")));
    }
}
