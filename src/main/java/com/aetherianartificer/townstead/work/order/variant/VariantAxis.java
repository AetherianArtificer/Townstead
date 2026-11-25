package com.aetherianartificer.townstead.work.order.variant;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One way a thing on the Order Sheet can vary: how an herb is prepared, a potion's form or strength, a colour, an
 * enchantment. Its values are chosen on a line; with {@code any}, a line may leave it open and count every value.
 *
 * @param id     the axis, e.g. {@code townstead:colour}
 * @param label  its name as a translation key (shown when a row needs a label)
 * @param any    whether a line may leave this axis open
 * @param values the choices, in the order they are offered
 */
public record VariantAxis(ResourceLocation id, String label, boolean any, List<Value> values) {

    /** The value an open axis takes in a line's pattern. */
    public static final String ANY = "_";

    public VariantAxis {
        values = List.copyOf(values);
    }

    /**
     * One choice of an axis.
     *
     * @param id    the value's id: lower case, digits, {@code .}, {@code -}, {@code _} (it is part of product keys)
     * @param label its name as a translation key
     * @param icon  an item to show for it, or null for none
     * @param spelled how it is written into a product key by a template family, when not as its id (it may be
     *                empty: a potion's plain strength is no prefix at all)
     * @param onlyWhenOffered shown as a choice only where something offers it, rather than greyed where nothing does
     */
    public record Value(String id, String label, @Nullable ResourceLocation icon, String spelled, boolean onlyWhenOffered) {
        public Value(String id, String label, @Nullable ResourceLocation icon) {
            this(id, label, icon, id, false);
        }
    }

    public @Nullable Value value(String id) {
        for (Value v : values) if (v.id().equals(id)) return v;
        return null;
    }

    /** Whether {@code id} can be a value id: it goes in a resource location's path. */
    public static boolean validValueId(String id) {
        if (id == null || id.isEmpty() || ANY.equals(id)) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            if (!ok) return false;
        }
        return true;
    }
}
