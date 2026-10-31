package com.aetherianartificer.townstead.compat.jei;

import com.aetherianartificer.townstead.client.RecipeViewerGates;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Keeps hidden items out of JEI (see {@link RecipeViewerGates}), and brings them back when they open up. */
final class JeiContentGates {
    private JeiContentGates() {}

    @Nullable private static IJeiRuntime runtime;
    private static final Set<Item> hidden = new LinkedHashSet<>();
    private static boolean listening;

    static void attach(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        hidden.clear();
        if (!listening) {
            listening = true;
            RecipeViewerGates.onChange(JeiContentGates::refresh);
        }
        refresh();
    }

    static void detach() {
        runtime = null;
        hidden.clear();
    }

    // Only the difference goes to JEI: each runtime add or remove rebuilds its ingredient list.
    private static void refresh() {
        IJeiRuntime current = runtime;
        if (current == null) return;
        Set<Item> next = RecipeViewerGates.hidden();
        if (next.equals(hidden)) return;
        IIngredientManager ingredients = current.getIngredientManager();
        List<ItemStack> back = new ArrayList<>();
        for (Item item : hidden) if (!next.contains(item)) back.add(new ItemStack(item));
        List<ItemStack> off = new ArrayList<>();
        for (Item item : next) if (!hidden.contains(item)) off.add(new ItemStack(item));
        if (!back.isEmpty()) ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, back);
        if (!off.isEmpty()) ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, off);
        hidden.clear();
        hidden.addAll(next);
    }
}
