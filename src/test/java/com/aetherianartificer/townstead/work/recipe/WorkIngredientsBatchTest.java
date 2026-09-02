package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkIngredientsBatchTest {
    @Test
    void stationSetupSupplyDoesNotScaleWithEveryPhysicalPosition() {
        RecipeIngredient dough = new RecipeIngredient(
                List.of(ResourceLocation.tryParse("example:raw_dough")), 1);
        RecipeIngredient igniter = new RecipeIngredient(List.of(
                ResourceLocation.tryParse("minecraft:flint_and_steel"),
                ResourceLocation.tryParse("minecraft:fire_charge")), 1,
                ResourceLocation.tryParse("example:igniters"));

        assertEquals(8, WorkIngredients.requiredIngredientCount(dough, 0, 1, 8));
        assertEquals(1, WorkIngredients.requiredIngredientCount(igniter, 1, 1, 8));
    }

}
