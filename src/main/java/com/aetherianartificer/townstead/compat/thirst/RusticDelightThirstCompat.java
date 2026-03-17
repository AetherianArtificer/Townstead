package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Registers Rustic Delight drink items with Thirst Was Taken hydration values.
 * Uses putIfAbsent so external configs and other mods take priority.
 */
public final class RusticDelightThirstCompat {

    private static final DrinkEntry[] DRINKS = {
            // Baseline: farmersrespite:coffee = 6/11, water potion = 6/8
            drink("rusticdelight:coffee",           6, 11),
            drink("rusticdelight:milk_coffee",      7, 12),
            drink("rusticdelight:chocolate_coffee", 6, 11),
            drink("rusticdelight:honey_coffee",     7, 12),
            drink("rusticdelight:syrup_coffee",     6, 11),
            drink("rusticdelight:dark_coffee",      5, 10),
            drink("rusticdelight:syrup",            2,  3),
    };

    private RusticDelightThirstCompat() {}

    @SuppressWarnings("unchecked")
    public static void register() {
        if (!ModCompat.isLoaded("thirst")) return;
        if (!ModCompat.isLoaded("rusticdelight")) return;
        // LSO has its own data-driven hydration system; TWP-specific item registration is unnecessary
        if (ModCompat.isLoaded("legendarysurvivaloverhaul")) return;

        try {
            Class<?> thirstHelper = Class.forName("dev.ghen.thirst.api.ThirstHelper");
            Field drinksField = thirstHelper.getField("VALID_DRINKS");
            Object raw = drinksField.get(null);
            if (!(raw instanceof Map<?, ?> map)) return;
            Map<Item, Number[]> validDrinks = (Map<Item, Number[]>) map;

            int added = 0;
            for (DrinkEntry entry : DRINKS) {
                Item item = resolveItem(entry.itemId);
                if (item == null) continue;
                if (validDrinks.containsKey(item)) continue;
                validDrinks.put(item, new Number[]{entry.hydration, entry.quenchness});
                added++;
            }
            if (added > 0) {
                Townstead.LOGGER.info("Registered {} Rustic Delight drink(s) with Thirst Was Taken.", added);
            }
        } catch (Exception e) {
            Townstead.LOGGER.warn("Failed to register Rustic Delight drinks with Thirst Was Taken.", e);
        }
    }

    private static Item resolveItem(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return null;
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }

    private static DrinkEntry drink(String itemId, int hydration, int quenchness) {
        return new DrinkEntry(itemId, hydration, quenchness);
    }

    private record DrinkEntry(String itemId, int hydration, int quenchness) {}
}
