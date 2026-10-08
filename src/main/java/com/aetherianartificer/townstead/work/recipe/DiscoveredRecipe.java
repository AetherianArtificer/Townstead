package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.work.producer.ProducerRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
//? if >=1.21 {
import net.minecraft.world.item.crafting.RecipeHolder;
//?}

import javax.annotation.Nullable;
import java.util.List;

/**
 * A recipe the engine can actually work: resolved to concrete item ids, tied to the station role
 * that performs it, and independent of whichever mod's recipe type it was discovered from.
 */
public record DiscoveredRecipe(
        ResourceLocation id,
        StationType stationType,
        int tier,
        ResourceLocation output,
        int outputCount,
        int cookTimeTicks,
        boolean requiresTool,
        @Nullable ResourceLocation containerItemId,
        int containerCount,
        List<RecipeIngredient> inputs,
        boolean purification,
        boolean beverage,
        //? if >=1.21 {
        @Nullable RecipeHolder<?> source
        //?} else {
        /*@Nullable Recipe<?> source
        *///?}
) implements ProducerRecipe {}
