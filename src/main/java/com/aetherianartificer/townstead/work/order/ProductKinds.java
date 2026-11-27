package com.aetherianartificer.townstead.work.order;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Products that a mod tells apart within one item id, because the stack carries what it is: a
 * mixed remedy, a dyed piece, a filled canteen. A compat package registers a {@link Kind}, and
 * every order line, stock count, catalogue and store task then treats each of its products as a
 * line of its own, as it already does for potions.
 *
 * <p>A kind owns the keys it hands out. Each key must be readable on its own, on both sides, with
 * no lookup: the order sheet draws a line from its key alone.</p>
 */
public final class ProductKinds {

    public interface Kind {

        /** The product this stack is, or null when this kind does not know the stack. */
        @Nullable ResourceLocation keyOf(ItemStack stack);

        /** The product a recipe of this kind makes, or null when the recipe is not this kind's. */
        default @Nullable ResourceLocation keyForRecipe(@Nullable ResourceLocation recipeId) {
            return null;
        }

        /** Whether this kind handed out {@code product}. */
        boolean owns(ResourceLocation product);

        /** The physical item a product of this kind is. */
        ResourceLocation item(ResourceLocation product);

        /** A stack to draw the line with. Empty for the plain item. */
        default ItemStack displayStack(ResourceLocation product) {
            return ItemStack.EMPTY;
        }

        /** The line's name, or null for the display stack's own name. */
        default @Nullable String label(ResourceLocation product) {
            return null;
        }
    }

    private static final List<Kind> KINDS = new CopyOnWriteArrayList<>();

    private ProductKinds() {}

    public static void register(Kind kind) {
        if (kind != null && !KINDS.contains(kind)) KINDS.add(kind);
    }

    static void clear() {
        KINDS.clear();
    }

    static @Nullable ResourceLocation keyOf(ItemStack stack) {
        for (Kind kind : KINDS) {
            ResourceLocation key = safe(() -> kind.keyOf(stack));
            if (key != null) return key;
        }
        return null;
    }

    static @Nullable ResourceLocation keyForRecipe(@Nullable ResourceLocation recipeId) {
        if (recipeId == null) return null;
        for (Kind kind : KINDS) {
            ResourceLocation key = safe(() -> kind.keyForRecipe(recipeId));
            if (key != null) return key;
        }
        return null;
    }

    static @Nullable Kind owner(@Nullable ResourceLocation product) {
        if (product == null) return null;
        for (Kind kind : KINDS) {
            Boolean owns = safe(() -> kind.owns(product));
            if (Boolean.TRUE.equals(owns)) return kind;
        }
        return null;
    }

    private interface Call<T> {
        T get();
    }

    /** One kind failing must not break counting for every other line. */
    private static <T> @Nullable T safe(Call<T> call) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
