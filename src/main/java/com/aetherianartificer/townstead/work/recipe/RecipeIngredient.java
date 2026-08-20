package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.work.producer.ProducerRecipe;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

import java.util.List;

/**
 * One input a recipe needs, as a set of interchangeable item ids. An id may also name a supply
 * line ({@code townstead:furnace_fuel}), which is how the engine plans for things that have no
 * item behind them.
 */
public record RecipeIngredient(List<ResourceLocation> itemIds, int count,
                               @Nullable ResourceLocation sourceTag)
        implements ProducerRecipe.ResolvedIngredient {

    public RecipeIngredient(List<ResourceLocation> itemIds, int count) {
        this(itemIds, count, null);
    }

    public ResourceLocation primaryId() { return itemIds.get(0); }

    @Override
    public List<ResourceLocation> acceptableIds() { return itemIds; }

    /**
     * Groups with the same interchangeable set, summed. A shaped recipe lists eight ingots as
     * eight groups of one; anything that checks availability group by group would let one ingot
     * satisfy all eight, so counting always starts from the merged view.
     */
    public static List<RecipeIngredient> merge(List<RecipeIngredient> inputs) {
        record Key(List<ResourceLocation> ids, @Nullable ResourceLocation tag) {}
        java.util.Map<Key, Integer> counts = new java.util.LinkedHashMap<>();
        for (RecipeIngredient input : inputs) {
            counts.merge(new Key(input.itemIds(), input.sourceTag()),
                    Math.max(1, input.count()), Integer::sum);
        }
        List<RecipeIngredient> out = new java.util.ArrayList<>(counts.size());
        counts.forEach((key, count) -> out.add(
                new RecipeIngredient(key.ids(), count, key.tag())));
        return out;
    }
}
