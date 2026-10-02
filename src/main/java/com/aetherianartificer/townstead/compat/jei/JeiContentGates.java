package com.aetherianartificer.townstead.compat.jei;

import com.aetherianartificer.townstead.switchboard.ContentGates;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Keeps items of switched-off systems out of JEI, and brings them back when their system returns. */
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
            Switchboard.onChange(() -> Minecraft.getInstance().execute(JeiContentGates::refresh));
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
        for (Item item : ContentGates.gatedItems()) off.add(new ItemStack(item));
        if (!off.isEmpty()) {
            ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, off);
            hidden.addAll(off);
        }
    }
}
