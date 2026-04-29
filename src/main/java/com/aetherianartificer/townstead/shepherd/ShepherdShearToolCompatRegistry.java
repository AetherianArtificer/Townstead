package com.aetherianartificer.townstead.shepherd;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Matches shears across vanilla and modded variants. Vanilla
 * {@link Items#SHEARS} always qualifies; the tag check accepts community
 * shear tags so modded shears (e.g. iron/diamond shears from other mods)
 * count too. Mirrors {@code FarmerHarvestToolCompatRegistry} but flat —
 * shears are simple enough that a per-mod provider list isn't justified.
 */
public final class ShepherdShearToolCompatRegistry {
    //? if >=1.21 {
    private static final TagKey<Item> SHEARS_TAG_C = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "tools/shear"));
    private static final TagKey<Item> SHEARS_TAG_C_PLURAL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "tools/shears"));
    private static final TagKey<Item> SHEARS_TAG_FORGE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("forge", "shears"));
    private static final TagKey<Item> SHEARS_TAG_FORGE_TOOLS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("forge", "tools/shears"));
    //?} else {
    /*private static final TagKey<Item> SHEARS_TAG_C = TagKey.create(Registries.ITEM,
            new ResourceLocation("c", "tools/shear"));
    private static final TagKey<Item> SHEARS_TAG_C_PLURAL = TagKey.create(Registries.ITEM,
            new ResourceLocation("c", "tools/shears"));
    private static final TagKey<Item> SHEARS_TAG_FORGE = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "shears"));
    private static final TagKey<Item> SHEARS_TAG_FORGE_TOOLS = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "tools/shears"));
    *///?}

    private ShepherdShearToolCompatRegistry() {}

    public static boolean isCompatibleShears(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.SHEARS)) return true;
        return stack.is(SHEARS_TAG_C)
                || stack.is(SHEARS_TAG_C_PLURAL)
                || stack.is(SHEARS_TAG_FORGE)
                || stack.is(SHEARS_TAG_FORGE_TOOLS);
    }

    public static int findShearsSlot(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isCompatibleShears(inv.getItem(i))) return i;
        }
        return -1;
    }

    public static ItemStack getShears(SimpleContainer inv) {
        int slot = findShearsSlot(inv);
        if (slot < 0) return ItemStack.EMPTY;
        return inv.getItem(slot);
    }
}
