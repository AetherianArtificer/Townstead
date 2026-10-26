package com.aetherianartificer.townstead.compat.jei;

import com.aetherianartificer.townstead.client.RecipeViewerGates;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Keeps hidden items out of JEI (see {@link RecipeViewerGates}), and brings them back when they open up. */
final class JeiContentGates {
    private JeiContentGates() {}

    @Nullable private static IJeiRuntime runtime;
    private static final List<ItemStack> hidden = new ArrayList<>();
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

    private static void refresh() {
        IJeiRuntime current = runtime;
        if (current == null) return;
        IIngredientManager ingredients = current.getIngredientManager();
        if (!hidden.isEmpty()) {
            ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.copyOf(hidden));
            hidden.clear();
        }
        List<ItemStack> off = new ArrayList<>();
        for (Item item : RecipeViewerGates.hidden()) off.add(new ItemStack(item));
        if (!off.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, off);
            hidden.addAll(off);
        }
    }
}
