package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModifiedProductsTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    @Test
    void productAndRecipeIdsRoundTrip() {
        ResourceLocation coat = id("legendarysurvivaloverhaul:heating_coat_1");
        ResourceLocation piece = id("minecraft:leather_chestplate");

        ResourceLocation product = ModifiedProducts.key(coat, piece);
        assertEquals(id("townstead_product:commission/legendarysurvivaloverhaul/heating_coat_1/minecraft/leather_chestplate"), product);
        ModifiedProducts.Parts parts = ModifiedProducts.decode(product);
        assertNotNull(parts);
        assertEquals(coat, parts.modifier());
        assertEquals(piece, parts.item());

        ResourceLocation recipe = ModifiedProducts.recipeId(coat, piece);
        assertEquals(id("townstead:commission/legendarysurvivaloverhaul/heating_coat_1/minecraft/leather_chestplate"), recipe);
        assertEquals(product, ModifiedProducts.keyForRecipe(recipe));
        assertEquals(parts, ModifiedProducts.decodeRecipe(recipe));
    }

    @Test
    void foreignIdsDecodeToNothing() {
        assertNull(ModifiedProducts.decode(id("minecraft:leather_chestplate")));
        assertNull(ModifiedProducts.decode(id("townstead_product:potion/minecraft/potion/minecraft/healing")));
        assertNull(ModifiedProducts.keyForRecipe(id("townstead:protocol/townstead/pizza_station/0")));
        assertNull(ModifiedProducts.decode(id("townstead_product:commission/too/short")));
        assertNull(ModifiedProducts.keyForRecipe(null));
    }
}
