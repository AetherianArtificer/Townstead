package com.aetherianartificer.townstead.compat.butchery;

import net.minecraft.core.registries.BuiltInRegistries;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Helpers for Butchery's {@code sponge} / {@code rag} items and their
 * wetness NBT. Both items carry a per-stack integer (stored as a double in
 * Butchery's code) tracking how many cleaning or carcass-soaking uses
 * remain before the cloth needs to be re-wet.
 *
 * <p>Wetness cap is 10 (see {@code WetspongeProcedure} / {@code WetragProcedure}
 * which set the tag back to 10 on re-wet). Each cleaning decrement is 1,
 * each skin-rack soak decrement is 1.
 *
 * <p>1.21+ stores the tag under {@code DataComponents.CUSTOM_DATA} while
 * 1.20.1 uses the raw {@code ItemStack#getOrCreateTag()} Forge map; the
 * getters/setters below wrap both.
 */
public final class SpongeRagHelper {
    public static final String SPONGE_WETNESS_KEY = "spongeWetness";
    public static final String RAG_WETNESS_KEY = "ragWetness";
    public static final int FULL_WETNESS = 10;

    public static final ResourceLocation SPONGE_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:sponge");
            //?} else {
            /*new ResourceLocation("butchery", "sponge");
            *///?}
    public static final ResourceLocation RAG_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:rag");
            //?} else {
            /*new ResourceLocation("butchery", "rag");
            *///?}
    public static final ResourceLocation BLOOD_PUDDLE_ID =
            //? if >=1.21 {
            ResourceLocation.parse("butchery:blood_puddle");
            //?} else {
            /*new ResourceLocation("butchery", "blood_puddle");
            *///?}

    private SpongeRagHelper() {}

    public static boolean isSponge(ItemStack stack) {
        return !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(SPONGE_ID);
    }

    public static boolean isRag(ItemStack stack) {
        return !stack.isEmpty()
                && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(RAG_ID);
    }

    public static boolean isCloth(ItemStack stack) {
        return isSponge(stack) || isRag(stack);
    }

    /** Read the cloth's wetness counter (0-10). Returns 0 for non-cloth items. */
    public static int readWetness(ItemStack stack) {
        if (isSponge(stack)) return readTagDouble(stack, SPONGE_WETNESS_KEY);
        if (isRag(stack)) return readTagDouble(stack, RAG_WETNESS_KEY);
        return 0;
    }

    /** True if the stack is a sponge or rag with wetness >= 1 (usable for cleaning or soaking). */
    public static boolean isWet(ItemStack stack) {
        return isCloth(stack) && readWetness(stack) >= 1;
    }

    /** Set wetness on a sponge or rag. Clamped to [0, FULL_WETNESS]. No-op on non-cloth. */
    public static void setWetness(ItemStack stack, int value) {
        if (!isCloth(stack)) return;
        int clamped = Math.max(0, Math.min(FULL_WETNESS, value));
        String key = isSponge(stack) ? SPONGE_WETNESS_KEY : RAG_WETNESS_KEY;
        writeTagDouble(stack, key, clamped);
    }

    /** Decrement wetness by one, mirroring {@code CleanbloodProcedure}. */
    public static void decrementWetness(ItemStack stack) {
        if (!isCloth(stack)) return;
        setWetness(stack, readWetness(stack) - 1);
    }

    // ── Cross-version NBT access ──

    private static int readTagDouble(ItemStack stack, String key) {
        //? if >=1.21 {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return (int) tag.getDouble(key);
        //?} else {
        /*CompoundTag tag = stack.getOrCreateTag();
        return (int) tag.getDouble(key);
        *///?}
    }

    private static void writeTagDouble(ItemStack stack, String key, int value) {
        //? if >=1.21 {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble(key, value));
        //?} else {
        /*stack.getOrCreateTag().putDouble(key, (double) value);
        *///?}
    }
}
