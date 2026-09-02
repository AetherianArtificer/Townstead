package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.order.OrderProducts;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** Exact item-and-product matching for inventory left over from one production cycle. */
final class CycleOutputMatcher {
    private CycleOutputMatcher() {}

    static boolean matches(@Nullable DiscoveredRecipe recipe, ResourceLocation item,
                           ResourceLocation product) {
        return recipe != null && recipe.output().equals(item)
                && OrderProducts.key(recipe).equals(product);
    }
}
