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
        if (recipe == null) return false;
        if (com.aetherianartificer.townstead.work.order.variant.ProductVariants.isPattern(product)) return com.aetherianartificer.townstead.work.order.variant.ProductVariants.matches(product, OrderProducts.key(recipe));
        return recipe.output().equals(item) && OrderProducts.key(recipe).equals(product);
    }
}
