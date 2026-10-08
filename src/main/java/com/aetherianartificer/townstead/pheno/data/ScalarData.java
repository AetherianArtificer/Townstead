package com.aetherianartificer.townstead.pheno.data;

import com.google.gson.JsonElement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Version-uniform access to the scalar values stored by datapack-authored procedures.
 * Block entities use NeoForge/Forge persistent data; item stacks use custom data on
 * 1.21 and the ordinary stack tag on 1.20.1.
 */
public final class ScalarData {

    private ScalarData() {}

    public static boolean matches(CompoundTag tag, String key, JsonElement expected) {
        if (tag == null || key == null || key.isBlank() || expected == null
                || !expected.isJsonPrimitive() || !tag.contains(key)) return false;
        var primitive = expected.getAsJsonPrimitive();
        if (primitive.isBoolean()) return tag.getBoolean(key) == primitive.getAsBoolean();
        if (primitive.isNumber()) return Double.compare(tag.getDouble(key), primitive.getAsDouble()) == 0;
        if (primitive.isString()) return tag.getString(key).equals(primitive.getAsString());
        return false;
    }

    public static double number(CompoundTag tag, String key, double fallback) {
        return tag != null && key != null && tag.contains(key) ? tag.getDouble(key) : fallback;
    }

    public static void put(CompoundTag tag, String key, JsonElement literal, double computed) {
        if (tag == null || key == null || key.isBlank()) return;
        if (literal == null) {
            tag.putDouble(key, computed);
            return;
        }
        var primitive = literal.getAsJsonPrimitive();
        if (primitive.isBoolean()) tag.putBoolean(key, primitive.getAsBoolean());
        else if (primitive.isNumber()) tag.putDouble(key, primitive.getAsDouble());
        else if (primitive.isString()) tag.putString(key, primitive.getAsString());
    }

    public static CompoundTag itemTag(ItemStack stack) {
        //? if >=1.21 {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        //?} else {
        /*return stack.getOrCreateTag();
        *///?}
    }

    public static void updateItem(ItemStack stack, java.util.function.Consumer<CompoundTag> update) {
        if (stack == null || stack.isEmpty() || update == null) return;
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, update);
        //?} else {
        /*update.accept(stack.getOrCreateTag());
        *///?}
    }

    public static @Nullable JsonElement scalar(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.deepCopy() : null;
    }
}
