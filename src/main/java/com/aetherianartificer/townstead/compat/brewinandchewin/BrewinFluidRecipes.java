package com.aetherianartificer.townstead.compat.brewinandchewin;

import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.FluidAmount;
import com.aetherianartificer.townstead.work.recipe.FluidRecipes;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.RecipeProjectionAccess;
import com.aetherianartificer.townstead.work.recipe.RecipeProjections;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads Brewin' and Chewin's keg recipes into the engine's two-stage form.
 *
 * <p>Its {@code fermenting} recipes end as fluid and its {@code keg_pouring} recipes begin as
 * fluid, so neither is workable alone; {@link FluidRecipes#join} pairs them. Neither exposes its
 * fluids through the vanilla recipe API, so the stable names come from
 * {@code townstead:recipe_projection/v1}. The generic projection accessor performs the minimum
 * reflection; this class retains only the Brewin-specific decision to join a ferment with a
 * compatible pour.</p>
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
        for (var holder : WorkRecipeRegistry.getRecipesForType(level, FERMENTING)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : WorkRecipeRegistry.getRecipesForType(level, FERMENTING)) {
            ResourceLocation id = recipe.getId();
        *///?}
            List<RecipeIngredient> inputs = WorkRecipeRegistry.extractIngredients(recipe);
            if (inputs.isEmpty()) continue;
            RecipeProjections.View projection = RecipeProjections.project(id, FERMENTING, recipe);
            FluidAmount result = fluid(projection.value("output_fluid"));
            if (!projection.succeeded() || result == null) continue;
            FluidAmount base = fluid(projection.value("input_fluid"));
            int time = projection.intValue("time", 1200);
            out.add(new FluidRecipes.Brew(id, inputs, base, result, time > 0 ? time : 1200));
        }
        return out;
    }

    // ── Pouring ──

    private static List<FluidRecipes.Pour> pours(ServerLevel level) {
        List<FluidRecipes.Pour> out = new ArrayList<>();
        //? if >=1.21 {
        for (var holder : WorkRecipeRegistry.getRecipesForType(level, KEG_POURING)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : WorkRecipeRegistry.getRecipesForType(level, KEG_POURING)) {
            ResourceLocation id = recipe.getId();
        *///?}
            RecipeProjections.View projection = RecipeProjections.project(id, KEG_POURING, recipe);
            ResourceLocation fluid = projection.idValue("input_fluid");
            int amount = projection.intValue("fluid_amount", 0);
            ResourceLocation output = projection.idValue("output");
            if (!projection.succeeded() || fluid == null || amount <= 0 || output == null) continue;
            out.add(new FluidRecipes.Pour(id, fluid, amount, output,
                    projection.idValue("container")));
        }
        return out;
    }

    private static FluidAmount fluid(Object value) {
        if (!(value instanceof RecipeProjectionAccess.FluidValue fluid)) return null;
        return FluidAmount.of(fluid.fluid(), fluid.amount());
    }
}
