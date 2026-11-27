package com.aetherianartificer.townstead.compat.jei;

import com.aetherianartificer.townstead.item.BlueprintRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
//? if >=1.21 {
import java.util.Optional;
import net.minecraft.world.item.crafting.RecipeHolder;
//?}

/**
 * Shows a blueprint recipe with its result changing color along with the ink slot. JEI cycles lists of
 * the same size together, so the ink slot and result slot hold one entry per ink, in the same order.
 */
//? if >=1.21 {
final class BlueprintJeiExtension implements ICraftingCategoryExtension<BlueprintRecipe> {
    static void register(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(BlueprintRecipe.class, new BlueprintJeiExtension());
    }

    @Override
    public void setRecipe(RecipeHolder<BlueprintRecipe> holder, IRecipeLayoutBuilder builder, ICraftingGridHelper grid, IFocusGroup focuses) {
        layout(holder.value(), builder, grid);
    }

    @Override
    public Optional<ResourceLocation> getRegistryName(RecipeHolder<BlueprintRecipe> holder) {
        return Optional.of(holder.id());
    }

    @Override
    public int getWidth(RecipeHolder<BlueprintRecipe> holder) {
        return 3;
    }

    @Override
    public int getHeight(RecipeHolder<BlueprintRecipe> holder) {
        return 3;
    }
//?} else {
/*final class BlueprintJeiExtension implements ICraftingCategoryExtension {
    private final BlueprintRecipe recipe;

    private BlueprintJeiExtension(BlueprintRecipe recipe) {
        this.recipe = recipe;
    }

    static void register(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addCategoryExtension(BlueprintRecipe.class, BlueprintJeiExtension::new);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper grid, IFocusGroup focuses) {
        layout(recipe, builder, grid);
    }

    @Override
    public ResourceLocation getRegistryName() {
        return recipe.getId();
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }
*///?}

    private static void layout(BlueprintRecipe recipe, IRecipeLayoutBuilder builder, ICraftingGridHelper grid) {
        List<ItemStack> inks = BlueprintRecipe.inks();
        List<ItemStack> results = new ArrayList<>();
        for (ItemStack ink : inks) results.add(recipe.resultFor(ink));

        List<Ingredient> ingredients = recipe.grid();
        List<List<ItemStack>> inputs = new ArrayList<>();
        int inkSlot = -1;
        for (int i = 0; i < ingredients.size(); i++) {
            boolean ink = BlueprintRecipe.isInk(ingredients.get(i));
            if (ink) inkSlot = i;
            inputs.add(ink ? inks : List.of(ingredients.get(i).getItems()));
        }

        List<IRecipeSlotBuilder> slots = grid.createAndSetInputs(builder, VanillaTypes.ITEM_STACK, inputs, 3, 3);
        IRecipeSlotBuilder output = grid.createAndSetOutputs(builder, VanillaTypes.ITEM_STACK, results);
        if (inkSlot >= 0) builder.createFocusLink(slots.get(inkSlot), output);
    }
}
