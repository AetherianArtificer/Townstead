package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Pure recipe-type ownership rule shared by station selection and regression tests. */
public final class StationRecipeOwnership {

    private StationRecipeOwnership() {}

    public static boolean ownsDeclaredType(WorkstationDef def, StationType stationType,
                                           @Nullable ResourceLocation recipeType) {
        return def != null && stationType == def.role()
                && recipeType != null && recipeType.equals(def.recipeType());
    }

    public static boolean isFuelRequirement(RecipeIngredient ingredient) {
        return ingredient != null && ingredient.itemIds().contains(
                com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL);
    }
}
