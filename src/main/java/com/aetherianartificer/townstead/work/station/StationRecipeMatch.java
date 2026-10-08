package com.aetherianartificer.townstead.work.station;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Method;

/** Verifies that a station's real recipe fulfils the production line it was offered. */
public final class StationRecipeMatch {
    private StationRecipeMatch() {}

    public static boolean produces(ServerLevel level, Object holderOrRecipe, ResourceLocation expectedOutput) {
        if (level == null || holderOrRecipe == null || expectedOutput == null) return false;
        Object recipe = unwrapHolder(holderOrRecipe);
        ItemStack result = result(level, recipe);
        return !result.isEmpty() && expectedOutput.equals(BuiltInRegistries.ITEM.getKey(result.getItem()));
    }

    private static Object unwrapHolder(Object candidate) {
        try {
            Method value = candidate.getClass().getMethod("value");
            Object unwrapped = value.invoke(candidate);
            if (unwrapped != null) return unwrapped;
        } catch (ReflectiveOperationException ignored) {
        }
        return candidate;
    }

    private static ItemStack result(ServerLevel level, Object candidate) {
        if (candidate instanceof Recipe<?> recipe) {
            try {
                ItemStack stack = recipe.getResultItem(level.registryAccess());
                if (stack != null) return stack;
            } catch (Throwable ignored) {
            }
        }
        try {
            Method method = candidate.getClass().getMethod("getResultItem");
            Object value = method.invoke(candidate);
            if (value instanceof ItemStack stack) return stack;
        } catch (ReflectiveOperationException ignored) {
        }
        return ItemStack.EMPTY;
    }
}
