package com.aetherianartificer.townstead.compat.caupona;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reflective reads against Caupona's recipe classes.
 *
 * <p>Caupona models soup as fluid and decides which soup a pot makes with a predicate system, so
 * none of what a villager needs to know — which items, which base, which bowl — comes through the
 * vanilla recipe API. The classes do expose it, though: conditions can list the item stacks they
 * accept, and a base condition is a plain {@code Predicate<Fluid>} that can be probed. This class
 * is only the reading; {@link CauponaFluidRecipes} does the deciding.</p>
 *
 * <p>Every read fails soft. A field or method that moved between versions yields nothing rather
 * than an exception, which costs a pot nobody works instead of a world nobody loads.</p>
 */
final class CauponaRecipeAccess {

    private CauponaRecipeAccess() {}

    /** A public getter if there is one, else the field of that name, else null. */
    @Nullable
    static Object read(Object owner, String getter, String field) {
        if (owner == null) return null;
        try {
            Method m = owner.getClass().getMethod(getter);
            m.setAccessible(true);
            return m.invoke(owner);
        } catch (Throwable ignored) {
            // No such getter; fall through to the field.
        }
        for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(field);
                f.setAccessible(true);
                return f.get(owner);
            } catch (Throwable ignored) {
                // Not declared here; keep walking up.
            }
        }
        return null;
    }

    static int readInt(Object owner, String getter, String field, int fallback) {
        return read(owner, getter, field) instanceof Integer i ? i : fallback;
    }

    static float readFloat(Object owner, String getter, String field, float fallback) {
        Object value = read(owner, getter, field);
        return value instanceof Number n ? n.floatValue() : fallback;
    }

    @Nullable
    static ResourceLocation readFluidId(Object owner, String getter, String field) {
        return read(owner, getter, field) instanceof Fluid fluid ? BuiltInRegistries.FLUID.getKey(fluid) : null;
    }

    @Nullable
    static ResourceLocation readItemId(Object owner, String getter, String field) {
        Object value = read(owner, getter, field);
        if (value instanceof Item item) return BuiltInRegistries.ITEM.getKey(item);
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem());
        }
        return null;
    }

    static List<?> readList(Object owner, String getter, String field) {
        return read(owner, getter, field) instanceof List<?> list ? list : List.of();
    }

    /**
     * The item ids a Caupona condition accepts. A condition owns one or more "numbers" (a tag, an
     * ingredient, a single item), and each can list its stacks — so the accepted set is readable
     * rather than something that has to be solved for.
     */
    static Set<ResourceLocation> acceptedItems(Object condition) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Object number : streamOf(read(condition, "getAllNumbers", "number"))) {
            for (Object stack : streamOf(read(number, "getStacks", "stacks"))) {
                if (stack instanceof ItemStack is && !is.isEmpty()) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(is.getItem());
                    if (id != null) ids.add(id);
                }
            }
        }
        return ids;
    }

    /** Whether any of a recipe's base conditions accepts this fluid. */
    static boolean baseAccepts(List<?> baseConditions, Fluid fluid) {
        for (Object condition : baseConditions) {
            try {
                Method test = condition.getClass().getMethod("test", Fluid.class);
                test.setAccessible(true);
                if (test.invoke(condition, fluid) instanceof Boolean b && b) return true;
            } catch (Throwable ignored) {
                // Unreadable condition: treated as not accepting, never as accepting.
            }
        }
        return false;
    }

    /**
     * The fluids a NeoForge {@code FluidIngredient} matches. Its expansion method is named
     * differently across versions, so both known names are tried before giving up.
     */
    static Set<ResourceLocation> fluidIds(@Nullable Object fluidIngredient) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        if (fluidIngredient == null) return ids;
        if (fluidIngredient instanceof Fluid fluid) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null) ids.add(id);
            return ids;
        }
        for (String name : new String[]{"getStacks", "generateStacks", "getFluids"}) {
            try {
                Method m = fluidIngredient.getClass().getMethod(name);
                m.setAccessible(true);
                for (Object stack : streamOf(m.invoke(fluidIngredient))) {
                    ResourceLocation id = fluidOf(stack);
                    if (id != null) ids.add(id);
                }
                if (!ids.isEmpty()) return ids;
            } catch (Throwable ignored) {
                // Try the next spelling.
            }
        }
        return ids;
    }

    /** The item ids a vanilla {@code Ingredient} accepts, read without importing it. */
    static Set<ResourceLocation> ingredientItems(@Nullable Object ingredient) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        if (ingredient == null) return ids;
        try {
            Method m = ingredient.getClass().getMethod("getItems");
            m.setAccessible(true);
            for (Object stack : streamOf(m.invoke(ingredient))) {
                if (stack instanceof ItemStack is && !is.isEmpty()) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(is.getItem());
                    if (id != null) ids.add(id);
                }
            }
        } catch (Throwable ignored) {
            // Unreadable ingredient: no items, so the caller drops the recipe.
        }
        return ids;
    }

    @Nullable
    private static ResourceLocation fluidOf(@Nullable Object fluidStack) {
        if (fluidStack == null) return null;
        if (fluidStack instanceof Fluid fluid) return BuiltInRegistries.FLUID.getKey(fluid);
        try {
            Object fluid = fluidStack.getClass().getMethod("getFluid").invoke(fluidStack);
            return fluid instanceof Fluid f ? BuiltInRegistries.FLUID.getKey(f) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Streams, arrays and collections all turn up in these APIs; treat them alike. */
    private static List<Object> streamOf(@Nullable Object value) {
        List<Object> out = new ArrayList<>();
        if (value == null) return out;
        if (value instanceof Stream<?> stream) {
            stream.forEach(out::add);
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(out::add);
        } else if (value instanceof Object[] array) {
            for (Object o : array) out.add(o);
        } else {
            out.add(value);
        }
        return out;
    }
}
