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

    /**
     * Groups with the same interchangeable set, summed. A shaped recipe lists eight ingots as
     * eight groups of one; anything that checks availability group by group would let one ingot
     * satisfy all eight, so counting always starts from the merged view.
     */
    public static List<RecipeIngredient> merge(List<RecipeIngredient> inputs) {
        java.util.Map<List<ResourceLocation>, Integer> counts = new java.util.LinkedHashMap<>();
        for (RecipeIngredient input : inputs) {
            counts.merge(input.itemIds(), Math.max(1, input.count()), Integer::sum);
        }
        List<RecipeIngredient> out = new java.util.ArrayList<>(counts.size());
        counts.forEach((ids, count) -> out.add(new RecipeIngredient(ids, count)));
        return out;
    }
}
