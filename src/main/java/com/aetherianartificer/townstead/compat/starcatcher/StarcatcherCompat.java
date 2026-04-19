package com.aetherianartificer.townstead.compat.starcatcher;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Lightweight Starcatcher integration, loaded conditionally at runtime.
 * Starcatcher rods extend a plain Item (not vanilla FishingRodItem) and
 * are tagged {@code starcatcher:rods}, so our fisherman villager misses
 * them by default. This helper teaches the fisherman to recognize a
 * Starcatcher rod as a valid fishing tool — for inventory pick, hand
 * display, and damage — while keeping the actual hook spawn using the
 * vanilla FishingHook mechanic (no Starcatcher minigame / custom bob
 * integration needed).
 */
public final class StarcatcherCompat {
    private static final String MOD_ID = "starcatcher";

    //? if >=1.21 {
    private static final TagKey<Item> RODS_TAG = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "rods"));
    //?} else {
    /*private static final TagKey<Item> RODS_TAG = ItemTags.create(
            new ResourceLocation(MOD_ID, "rods"));
    *///?}

    private StarcatcherCompat() {}

    public static boolean isLoaded() {
        return ModCompat.isLoaded(MOD_ID);
    }

    /**
     * True if the stack is a Starcatcher rod (tag-based lookup, so it
     * automatically covers every rod variant the mod adds or future-proofs).
     * Returns false if Starcatcher isn't loaded.
     */
    public static boolean isStarcatcherRod(ItemStack stack) {
        if (!isLoaded()) return false;
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(RODS_TAG);
    }
}
