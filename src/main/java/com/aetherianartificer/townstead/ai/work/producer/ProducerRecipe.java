package com.aetherianartificer.townstead.ai.work.producer;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface ProducerRecipe {
    ResourceLocation id();

    ResourceLocation output();

    int outputCount();

    int cookTimeTicks();

    int tier();

    List<? extends ResolvedIngredient> inputs();

    interface ResolvedIngredient {
        List<ResourceLocation> acceptableIds();

        int count();
    }
}
