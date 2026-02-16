package com.aetherianartificer.townstead.hunger.profile;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ButcherProfileDefinition(
        String id,
        String family,
        int level,
        int requiredTier,
        Set<ResourceLocation> inputItems,
        Set<ResourceLocation> inputTags,
        Set<ResourceLocation> fuelItems,
        Set<ResourceLocation> fuelTags,
        Set<ResourceLocation> outputItems,
        double throughputModifier,
        double stockCadenceModifier,
        double requestIntervalModifier
) {
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (id == null || id.isBlank()) errors.add("id is required");
        if (family == null || family.isBlank()) errors.add("family is required");
        if (level < 1 || level > 5) errors.add("level must be in range 1..5");
        if (requiredTier < 1 || requiredTier > 5) errors.add("requiredTier must be in range 1..5");
        if (throughputModifier <= 0d) errors.add("throughputModifier must be > 0");
        if (stockCadenceModifier <= 0d) errors.add("stockCadenceModifier must be > 0");
        if (requestIntervalModifier <= 0d) errors.add("requestIntervalModifier must be > 0");
        return errors;
    }

    public boolean hasInputFilters() {
        return !inputItems.isEmpty() || !inputTags.isEmpty();
    }

    public boolean hasFuelFilters() {
        return !fuelItems.isEmpty() || !fuelTags.isEmpty();
    }

    public boolean hasOutputFilters() {
        return !outputItems.isEmpty();
    }

    public boolean matchesInput(ItemStack stack) {
        return matchesAny(stack, inputItems, inputTags);
    }

    public boolean matchesFuel(ItemStack stack) {
        return matchesAny(stack, fuelItems, fuelTags);
    }

    public boolean matchesOutput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = itemKey(stack);
        return key != null && outputItems.contains(key);
    }

    public static Set<ResourceLocation> normalizeSet(Set<ResourceLocation> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Set.copyOf(new LinkedHashSet<>(values));
    }

    private static boolean matchesAny(ItemStack stack, Set<ResourceLocation> items, Set<ResourceLocation> tags) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = itemKey(stack);
        if (key != null && items.contains(key)) return true;
        for (ResourceLocation tagId : tags) {
            if (tagId == null) continue;
            if (stack.is(TagKey.create(Registries.ITEM, tagId))) return true;
        }
        return false;
    }

    private static ResourceLocation itemKey(ItemStack stack) {
        Item item = stack.getItem();
        return item.builtInRegistryHolder().key().location();
    }
}
