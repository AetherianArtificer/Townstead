package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a protocol station's inline {@code produces} lines into synthetic recipes.
 *
 * <p>Engine-owned because every trade needs the same expansion: the cook's registry discovers
 * these for kitchens, and the order catalogue reads them for any worksite whose def names a work
 * task. Tags in inputs are resolved to their member items at expansion time, so the result is a
 * recipe the engine can stage item by item.</p>
 */
public final class ProtocolRecipes {

    private ProtocolRecipes() {}

    /** Every loaded protocol def's produce lines, expanded. */
    public static List<DiscoveredRecipe> discoverAll() {
        List<DiscoveredRecipe> out = new ArrayList<>();
        for (WorkstationDef def : Workstations.all()) {
            out.addAll(discoverFor(def));
        }
        return out;
    }

    /** One def's produce lines, expanded; empty for defs of other roles or with none declared. */
    public static List<DiscoveredRecipe> discoverFor(WorkstationDef def) {
        if (def.role() != StationType.PASSIVE_STATION && def.role() != StationType.PLACE_SURFACE) {
            return List.of();
        }
        List<DiscoveredRecipe> out = new ArrayList<>();
        int index = 0;
        for (WorkstationDef.Produce produce : def.produces()) {
            ResourceLocation recipeId = ResourceLocation.tryParse(
                    "townstead:protocol/" + def.id().getNamespace() + "/" + def.id().getPath() + "/" + index++);
            if (recipeId == null) continue;
            List<RecipeIngredient> inputs = new ArrayList<>();
            boolean unresolvable = false;
            for (String raw : produce.inputs()) {
                List<ResourceLocation> ids = new ArrayList<>();
                if (raw.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
                    if (tagId != null) {
                        var tag = net.minecraft.tags.TagKey.create(Registries.ITEM, tagId);
                        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(holder.value());
                            if (itemId != null) ids.add(itemId);
                        }
                    }
                } else {
                    ResourceLocation itemId = ResourceLocation.tryParse(raw);
                    if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) ids.add(itemId);
                }
                if (ids.isEmpty()) {
                    unresolvable = true;
                    break;
                }
                inputs.add(new RecipeIngredient(List.copyOf(ids), 1));
            }
            if (unresolvable || inputs.isEmpty()) continue;
            if (!BuiltInRegistries.ITEM.containsKey(produce.output())) continue;
            out.add(new DiscoveredRecipe(
                    recipeId,
                    def.role(),
                    def.recipeTier() > 0 ? def.recipeTier() : 1,
                    produce.output(),
                    Math.max(1, produce.outputCount()),
                    Math.max(1, produce.timeTicks()),
                    false,
                    null,
                    0,
                    List.copyOf(inputs),
                    false,
                    def.beverage(),
                    null
            ));
        }
        return out;
    }

    /**
     * Every recipe of a def's declared {@code recipe_type}, resolved for the order screen.
     *
     * <p>For a station whose outputs come from a recipe family rather than inline lines (the
     * smoker's {@code minecraft:smoking}), the family <em>is</em> the honest list of what the
     * station can be asked for. Sources are deliberately not carried: these entries feed the
     * catalogue, not the cook engine's exclusive pairing.</p>
     */
    public static List<DiscoveredRecipe> discoverByType(ServerLevel level, WorkstationDef def) {
        if (def.recipeType() == null) return List.of();
        List<DiscoveredRecipe> out = new ArrayList<>();
        //? if >=1.21 {
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder
                : level.getRecipeManager().getRecipes()) {
            net.minecraft.world.item.crafting.Recipe<?> recipe = holder.value();
            ResourceLocation recipeId = holder.id();
        //?} else {
        /*for (net.minecraft.world.item.crafting.Recipe<?> recipe
                : level.getRecipeManager().getRecipes()) {
            ResourceLocation recipeId = recipe.getId();
        *///?}
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (!def.recipeType().equals(typeId)) continue;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) continue;
            List<RecipeIngredient> inputs = new ArrayList<>();
            for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getIngredients()) {
                Set<ResourceLocation> ids = new LinkedHashSet<>();
                for (ItemStack option : ingredient.getItems()) {
                    if (option.isEmpty()) continue;
                    ids.add(BuiltInRegistries.ITEM.getKey(option.getItem()));
                }
                if (!ids.isEmpty()) inputs.add(new RecipeIngredient(List.copyOf(ids), 1));
            }
            out.add(new DiscoveredRecipe(
                    recipeId,
                    def.role(),
                    def.recipeTier() > 0 ? def.recipeTier() : 1,
                    BuiltInRegistries.ITEM.getKey(result.getItem()),
                    Math.max(1, result.getCount()),
                    Math.max(1, def.cookTimeTicks()),
                    false,
                    null,
                    0,
                    List.copyOf(inputs),
                    false,
                    def.beverage(),
                    null
            ));
        }
        return out;
    }
}
