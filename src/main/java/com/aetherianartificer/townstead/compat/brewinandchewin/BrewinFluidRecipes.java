package com.aetherianartificer.townstead.compat.brewinandchewin;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.FluidAmount;
import com.aetherianartificer.townstead.work.recipe.FluidRecipes;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Brewin' and Chewin's keg recipes into the engine's two-stage form.
 *
 * <p>Its {@code fermenting} recipes end as fluid and its {@code keg_pouring} recipes begin as
 * fluid, so neither is workable alone; {@link FluidRecipes#join} pairs them. Neither exposes its
 * fluids through the vanilla recipe API, so the fields are read reflectively — the same approach
 * the Farmer's Delight stove already needs, and for the same reason: these are a mod's internals,
 * not a contract.</p>
 *
 * <p>Everything here fails soft. A field that moved between versions yields no recipes rather than
 * a crash, which costs a keg nobody works instead of a world nobody loads.</p>
 */
public final class BrewinFluidRecipes {

    //? if >=1.21 {
    private static final ResourceLocation FERMENTING = ResourceLocation.parse("brewinandchewin:fermenting");
    private static final ResourceLocation KEG_POURING = ResourceLocation.parse("brewinandchewin:keg_pouring");
    //?} else {
    /*private static final ResourceLocation FERMENTING = new ResourceLocation("brewinandchewin", "fermenting");
    private static final ResourceLocation KEG_POURING = new ResourceLocation("brewinandchewin", "keg_pouring");
    *///?}

    public static final String SOURCE = "townstead:brewinandchewin";

    private BrewinFluidRecipes() {}

    public static void bootstrap() {
        if (!com.aetherianartificer.townstead.compat.ModCompat.isLoaded("brewinandchewin")) return;
        com.aetherianartificer.townstead.work.recipe.FluidRecipeSources
                .register(SOURCE, BrewinFluidRecipes::discover);
    }

    /** Every keg recipe, already joined into ordinary item-in, item-out form. */
    public static List<DiscoveredRecipe> discover(ServerLevel level, StationType stationType, int tier) {
        List<FluidRecipes.Brew> brews = brews(level);
        if (brews.isEmpty()) return List.of();
        return FluidRecipes.join(brews, pours(level), stationType, tier);
    }

    // ── Fermenting ──

    private static List<FluidRecipes.Brew> brews(ServerLevel level) {
        List<FluidRecipes.Brew> out = new ArrayList<>();
        //? if >=1.21 {
        for (var holder : ModRecipeRegistry.getRecipesForType(level, FERMENTING)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : ModRecipeRegistry.getRecipesForType(level, FERMENTING)) {
            ResourceLocation id = recipe.getId();
        *///?}
            List<RecipeIngredient> inputs = ModRecipeRegistry.extractIngredients(recipe);
            if (inputs.isEmpty()) continue;
            FluidAmount result = fluidField(recipe, "result", "output");
            if (result == null) continue;
            FluidAmount base = fluidField(recipe, "base");
            int time = intField(recipe, "time");
            out.add(new FluidRecipes.Brew(id, inputs, base, result, time > 0 ? time : 1200));
        }
        return out;
    }

    // ── Pouring ──

    private static List<FluidRecipes.Pour> pours(ServerLevel level) {
        List<FluidRecipes.Pour> out = new ArrayList<>();
        //? if >=1.21 {
        for (var holder : ModRecipeRegistry.getRecipesForType(level, KEG_POURING)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : ModRecipeRegistry.getRecipesForType(level, KEG_POURING)) {
            ResourceLocation id = recipe.getId();
        *///?}
            ResourceLocation fluid = plainFluidField(recipe);
            int amount = intField(recipe, "amount");
            ResourceLocation output = itemField(recipe, "output", "result");
            if (fluid == null || amount <= 0 || output == null) continue;
            out.add(new FluidRecipes.Pour(id, fluid, amount, output, itemField(recipe, "container")));
        }
        return out;
    }

    // ── Reflection ──

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    fields.add(f);
                } catch (Throwable ignored) {
                    // Sealed by the module system; that field is simply unreadable.
                }
            }
        }
        return fields;
    }

    private static boolean named(Field f, String... hints) {
        String name = f.getName().toLowerCase(java.util.Locale.ROOT);
        for (String hint : hints) {
            if (name.contains(hint)) return true;
        }
        return false;
    }

    /** A FluidStack-typed field, read through the loader-neutral getFluid/getAmount pair. */
    private static @Nullable FluidAmount fluidField(Recipe<?> recipe, String... nameHints) {
        for (Field f : allFields(recipe.getClass())) {
            if (!"FluidStack".equals(f.getType().getSimpleName())) continue;
            if (nameHints.length > 0 && !named(f, nameHints)) continue;
            try {
                FluidAmount read = readFluidStack(f.get(recipe));
                if (read != null) return read;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static @Nullable FluidAmount readFluidStack(@Nullable Object stack) {
        if (stack == null) return null;
        try {
            Object fluid = stack.getClass().getMethod("getFluid").invoke(stack);
            Object amount = stack.getClass().getMethod("getAmount").invoke(stack);
            if (!(fluid instanceof Fluid f) || !(amount instanceof Integer mb)) return null;
            return FluidAmount.of(BuiltInRegistries.FLUID.getKey(f), mb);
        } catch (Throwable t) {
            return null;
        }
    }

    /** A pouring recipe names its fluid directly rather than as a stack. */
    private static @Nullable ResourceLocation plainFluidField(Recipe<?> recipe) {
        for (Field f : allFields(recipe.getClass())) {
            try {
                Object value = f.get(recipe);
                if (value instanceof Fluid fluid) return BuiltInRegistries.FLUID.getKey(fluid);
                FluidAmount stack = readFluidStack(
                        "FluidStack".equals(f.getType().getSimpleName()) ? value : null);
                if (stack != null) return stack.fluid();
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static int intField(Recipe<?> recipe, String... nameHints) {
        for (Field f : allFields(recipe.getClass())) {
            if (f.getType() != int.class && f.getType() != Integer.class) continue;
            if (!named(f, nameHints)) continue;
            try {
                Object value = f.get(recipe);
                if (value instanceof Integer i && i > 0) return i;
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private static @Nullable ResourceLocation itemField(Recipe<?> recipe, String... nameHints) {
        for (Field f : allFields(recipe.getClass())) {
            if (f.getType() != ItemStack.class) continue;
            if (!named(f, nameHints)) continue;
            try {
                if (f.get(recipe) instanceof ItemStack stack && !stack.isEmpty()) {
                    return BuiltInRegistries.ITEM.getKey(stack.getItem());
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
