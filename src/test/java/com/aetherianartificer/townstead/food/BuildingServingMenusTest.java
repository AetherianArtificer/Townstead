package com.aetherianartificer.townstead.food;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingServingMenusTest {
    private static final String PIZZERIA = "compat/pizzadelight/pizzeria_l1";

    @AfterEach
    void clear() {
        BuildingServingMenus.replaceAll(Map.of());
    }

    @Test
    void pizzeriaServesItsAuthoredMenuInsteadOfAnyFood() {
        BuildingServingMenus.replaceAll(Map.of(PIZZERIA, Set.of(id("pizzadelight:pizza"))));

        assertTrue(BuildingServingMenus.allows(PIZZERIA,
                id("pizzadelight:pizza"), id("pizzadelight:pizza")));
        assertFalse(BuildingServingMenus.allows(PIZZERIA,
                id("minecraft:brown_mushroom"), id("minecraft:brown_mushroom")));
        assertTrue(BuildingServingMenus.allows("compat/farmersdelight/kitchen_l1",
                id("minecraft:brown_mushroom"), id("minecraft:brown_mushroom")),
                "buildings without an authored menu keep the existing food behavior");
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
