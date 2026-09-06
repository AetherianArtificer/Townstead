package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Conservative station-facing interpretation of a recipe projection.
 *
 * <p>Projected item materials may become ordinary gather requirements only when their public
 * item identities can be resolved. Fluid requirements remain native-observed facts: Townstead
 * records the identity and quantity but never manufactures a tank, fill level, or progress flag
 * that the owning mod has not exposed.</p>
 */
public record ProjectedStationPlan(boolean eligible, List<RecipeIngredient> materials,
                                   @Nullable FluidRequirement fluid, boolean nativeObserved,
                                   List<String> diagnostics) {
    public record FluidRequirement(ResourceLocation fluid, int amount) {}

    public ProjectedStationPlan {
        materials = List.copyOf(materials);
        diagnostics = List.copyOf(diagnostics);
    }

    public static ProjectedStationPlan from(DiscoveredRecipe recipe) {
        ResourceLocation type = WorkRecipeRegistry.recipeTypeId(recipe);
        if (type == null) return new ProjectedStationPlan(true, List.of(), null, false, List.of());
        RecipeProjections.View view = RecipeProjections.project(recipe.id(), type, source(recipe));
        if (!view.succeeded()) {
            boolean absent = view.diagnostics().stream()
                    .allMatch(diagnostic -> diagnostic.kind() == RecipeProjections.FailureKind.NO_PROJECTION);
            return new ProjectedStationPlan(absent, List.of(), null, false,
                    absent ? List.of() : List.of(view.failureSummary()));
        }

        List<String> diagnostics = new ArrayList<>();
        List<RecipeIngredient> materials = new ArrayList<>();
        int projectedEntries = 0;
        for (String field : List.of("inputs", "catalysts")) {
            Object value = view.value(field);
            if (value == null) continue;
            List<Object> entries = RecipeProjectionAccess.elements(value);
            projectedEntries += entries.size();
            for (Object entry : entries) {
                RecipeIngredient ingredient = ingredient(entry);
                if (ingredient != null) materials.add(ingredient);
                else diagnostics.add("unresolved_" + field + ":" + entry.getClass().getName());
            }
        }
        materials = deficits(recipe.inputs(), RecipeIngredient.merge(materials));

        FluidRequirement fluid = fluid(view.value("input_fluid"), view.intValue("fluid_amount", 0));
        boolean unresolvedOnlySource = projectedEntries > 0 && materials.isEmpty()
                && recipe.inputs().isEmpty() && diagnostics.stream().anyMatch(s -> s.startsWith("unresolved_"));
        if (unresolvedOnlySource) diagnostics.add("projected materials are not safely stageable");
        if (fluid != null) diagnostics.add("fluid requirement is native-observed; no foreign tank state is synthesized");
        return new ProjectedStationPlan(view.ready() && !unresolvedOnlySource, materials, fluid,
                fluid != null, diagnostics);
    }

    private static @Nullable Object source(DiscoveredRecipe recipe) {
        if (recipe.source() == null) return null;
        //? if >=1.21 {
        return recipe.source().value();
        //?} else {
        /*return recipe.source();
        *///?}
    }

    private static @Nullable FluidRequirement fluid(@Nullable Object value, int explicitAmount) {
        if (value instanceof RecipeProjectionAccess.FluidValue amount) {
            return amount.amount() > 0 ? new FluidRequirement(amount.fluid(), amount.amount()) : null;
        }
        ResourceLocation id = value instanceof ResourceLocation resource ? resource : null;
        return id != null && explicitAmount > 0 ? new FluidRequirement(id, explicitAmount) : null;
    }

    private static @Nullable RecipeIngredient ingredient(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof RecipeIngredient ingredient) return ingredient;
        if (value instanceof ResourceLocation id) return new RecipeIngredient(List.of(id), 1);
        if (value instanceof Item item) return item(item, 1);
        if (value instanceof ItemStack stack && !stack.isEmpty()) return item(stack.getItem(), stack.getCount());
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        int count = positiveInt(invoke(value, "getCount"), 1);
        for (String method : List.of("getItems", "items", "getItem", "item")) {
            Object result = invoke(value, method);
            for (Object element : RecipeProjectionAccess.elements(result)) {
                RecipeIngredient converted = ingredient(element);
                if (converted != null) ids.addAll(converted.itemIds());
            }
            if (!ids.isEmpty()) return new RecipeIngredient(List.copyOf(ids), count);
        }
        return null;
    }

    private static RecipeIngredient item(Item item, int count) {
        return new RecipeIngredient(List.of(BuiltInRegistries.ITEM.getKey(item)), Math.max(1, count));
    }

    private static @Nullable Object invoke(Object owner, String method) {
        try {
            Method found = owner.getClass().getMethod(method);
            return found.getParameterCount() == 0 ? found.invoke(owner) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int positiveInt(@Nullable Object value, int fallback) {
        return value instanceof Number number && number.intValue() > 0 ? number.intValue() : fallback;
    }

    /** Only projected amounts not already represented by the public recipe become gather work. */
    static List<RecipeIngredient> deficits(List<RecipeIngredient> publicInputs,
                                           List<RecipeIngredient> projected) {
        Map<List<ResourceLocation>, Integer> represented = new LinkedHashMap<>();
        for (RecipeIngredient input : RecipeIngredient.merge(publicInputs)) {
            represented.merge(input.itemIds(), input.count(), Integer::sum);
        }
        List<RecipeIngredient> result = new ArrayList<>();
        for (RecipeIngredient input : projected) {
            int publicCount = represented.getOrDefault(input.itemIds(), 0);
            int missing = Math.max(0, input.count() - publicCount);
            if (missing > 0) result.add(new RecipeIngredient(input.itemIds(), missing,
                    input.sourceTag(), input.exactProduct()));
        }
        return List.copyOf(result);
    }
}
