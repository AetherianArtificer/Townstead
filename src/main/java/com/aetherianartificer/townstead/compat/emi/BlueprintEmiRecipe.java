package com.aetherianartificer.townstead.compat.emi;

import com.aetherianartificer.townstead.item.BlueprintRecipe;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPatternCraftingRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.GeneratedSlotWidget;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shows a blueprint recipe with its result changing color along with the ink slot. */
public final class BlueprintEmiRecipe extends EmiPatternCraftingRecipe {
    private final int inkSlot;
    private final List<EmiStack> inks = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    private BlueprintEmiRecipe(BlueprintRecipe recipe, ResourceLocation id) {
        super(inputs(recipe), EmiStack.of(recipe.getResultItem(null)), id, false);
        int found = -1;
        List<Ingredient> grid = recipe.grid();
        for (int i = 0; i < grid.size(); i++) {
            if (BlueprintRecipe.isInk(grid.get(i))) found = i;
        }
        inkSlot = found;
        for (ItemStack ink : BlueprintRecipe.inks()) {
            inks.add(EmiStack.of(ink));
            outputs.add(EmiStack.of(recipe.resultFor(ink)));
        }
    }

    /** Replaces the crafting recipe EMI made from each blueprint recipe. */
    static void register(EmiRegistry registry) {
        if (BlueprintRecipe.inks().isEmpty()) return;
        Set<ResourceLocation> ids = new HashSet<>();
        //? if >=1.21 {
        for (var holder : registry.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!(holder.value() instanceof BlueprintRecipe recipe)) continue;
            ids.add(holder.id());
            registry.addRecipe(new BlueprintEmiRecipe(recipe, holder.id()));
        }
        //?} else {
        /*for (var crafting : registry.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!(crafting instanceof BlueprintRecipe recipe)) continue;
            ids.add(recipe.getId());
            registry.addRecipe(new BlueprintEmiRecipe(recipe, recipe.getId()));
        }
        *///?}
        registry.removeRecipes(recipe -> ids.contains(recipe.getId()) && !(recipe instanceof BlueprintEmiRecipe));
    }

    private static List<EmiIngredient> inputs(BlueprintRecipe recipe) {
        List<EmiIngredient> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.grid()) inputs.add(EmiIngredient.of(ingredient));
        return inputs;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public SlotWidget getInputWidget(int slot, int x, int y) {
        if (slot == inkSlot) return new GeneratedSlotWidget(random -> inks.get(random.nextInt(inks.size())), unique, x, y);
        return new SlotWidget(slot < input.size() ? input.get(slot) : EmiStack.EMPTY, x, y);
    }

    @Override
    public SlotWidget getOutputWidget(int x, int y) {
        return new GeneratedSlotWidget(random -> outputs.get(random.nextInt(outputs.size())), unique, x, y);
    }
}
