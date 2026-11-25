package com.aetherianartificer.townstead.pheno.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * A thrown shot that looks like a data-pack particle rather than its item. The particle's id rides on the shot's
 * item, which every client already receives with the entity, and the thrown-item renderer draws that sprite instead.
 */
public final class ProjectileLooks {

    private static final String KEY = "townstead_look";

    private ProjectileLooks() {}

    public static void mark(ItemStack stack, String particleId) {
        //? if neoforge {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.putString(KEY, particleId);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(tag));
        //?} else {
        /*stack.getOrCreateTag().putString(KEY, particleId);
        *///?}
    }

    /** The particle this shot is drawn as, or empty. */
    public static String lookOf(ItemStack stack) {
        //? if neoforge {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag tag = data.copyTag();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        if (tag == null) return "";
        *///?}
        return tag.contains(KEY) ? tag.getString(KEY) : "";
    }
}
