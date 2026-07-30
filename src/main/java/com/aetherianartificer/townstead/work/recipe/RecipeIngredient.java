package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.work.producer.ProducerRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * One input a recipe needs, as a set of interchangeable item ids. An id may also name a supply
 * line ({@code townstead:furnace_fuel}), which is how the engine plans for things that have no
 * item behind them.
 */
public record RecipeIngredient(List<ResourceLocation> itemIds, int count)
        implements ProducerRecipe.ResolvedIngredient {

    public ResourceLocation primaryId() { return itemIds.get(0); }

    @Override
    public List<ResourceLocation> acceptableIds() { return itemIds; }
}
