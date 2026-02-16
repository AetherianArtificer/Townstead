package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class FarmersDelightKnifeCompat implements FarmerHarvestToolCompat {
    private static final TagKey<Item> KNIFE_TAG_FORGE =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "tools/knives"));
    private static final TagKey<Item> KNIFE_TAG_C =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/knives"));
    private static final TagKey<Item> KNIFE_TAG_FD =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("farmersdelight", "tools/knives"));

    @Override
    public String modId() {
        return "farmersdelight";
    }

    @Override
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(KNIFE_TAG_FORGE) || stack.is(KNIFE_TAG_C) || stack.is(KNIFE_TAG_FD)) return true;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        String path = key.getPath();
        return path.endsWith("_knife") || path.contains("/knife");
    }

    @Override
    public boolean shouldUseForBlock(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        String namespace = key.getNamespace();
        String path = key.getPath();

        // Farmers' Delight straw modifiers:
        // - minecraft:short_grass
        // - minecraft:tall_grass
        // - minecraft:wheat (age max)
        // - farmersdelight:rice_panicles (age max)
        // - farmersdelight:sandy_shrub
        if ("minecraft".equals(namespace) && ("short_grass".equals(path) || "tall_grass".equals(path))) return true;
        if (ModCompat.matchesLoadedModPath(key, "farmersdelight", "sandy_shrub")) return true;
        if ("minecraft".equals(namespace) && "wheat".equals(path)) return isMatureAgeState(state);
        if (ModCompat.matchesLoadedModPath(key, "farmersdelight", "rice_panicles")) return isMatureAgeState(state);
        return false;
    }

    private static boolean isMatureAgeState(BlockState state) {
        StateDefinition<?, ?> definition = state.getBlock().getStateDefinition();
        Property<?> property = definition.getProperty("age");
        if (!(property instanceof IntegerProperty integerProperty)) return false;
        int value = state.getValue(integerProperty);
        int max = integerProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
        return value >= max;
    }
}
