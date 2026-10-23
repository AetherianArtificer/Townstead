package com.aetherianartificer.townstead.client;

import com.aetherianartificer.townstead.item.LearnedRecipes;
import com.aetherianartificer.townstead.switchboard.ContentGates;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.LinkedHashSet;
import java.util.Set;

/** Items recipe viewers hide: those of switched-off systems, and learned recipes this player lacks. */
public final class RecipeViewerGates {
    private RecipeViewerGates() {}

    public static Set<Item> hidden() {
        Set<Item> out = new LinkedHashSet<>(ContentGates.gatedItems());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            out.addAll(LearnedRecipes.unlearned(mc.player.getRecipeBook(),
                    mc.getConnection().getRecipeManager(), mc.player.level().registryAccess()));
        }
        return out;
    }

    /** Runs {@code refresh} on the client thread whenever {@link #hidden()} may have changed. */
    public static void onChange(Runnable refresh) {
        Runnable onClient = () -> Minecraft.getInstance().execute(refresh);
        Switchboard.onChange(onClient);
        LearnedRecipes.onChange(onClient);
    }
}
