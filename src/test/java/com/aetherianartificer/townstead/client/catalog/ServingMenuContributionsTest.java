package com.aetherianartificer.townstead.client.catalog;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServingMenuContributionsTest {
    @Test
    void independentDatapacksUnionProductsForTheSameBuilding() {
        Map<String, Set<ResourceLocation>> menus = new HashMap<>();

        CatalogDataLoader.mergeServingMenu(menus, Set.of("bakery"), Set.of(id("minecraft:bread")));
        CatalogDataLoader.mergeServingMenu(menus, Set.of("bakery"), Set.of(id("example:fruit_tart")));

        assertEquals(Set.of(id("minecraft:bread"), id("example:fruit_tart")), menus.get("bakery"));
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
