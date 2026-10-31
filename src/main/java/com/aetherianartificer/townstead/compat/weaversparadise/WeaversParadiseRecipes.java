package com.aetherianartificer.townstead.compat.weaversparadise;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.RecipeTypeSources;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads Weavers' Paradise's two station recipe types through their own getters.
 *
 * <p>The spinning jenny consumes {@code count_required} of its input and turns one empty spool
 * into the result; the ingredient list cannot say how many. The clothcrafting station returns an
 * empty result item because the player's minigame score picks a tier at craft time; the tiers
 * are kept here so the adapter can pick one from the villager's rank. Weavers' Paradise is not
 * on the compile path, so every getter is reflected.</p>
 */
public final class WeaversParadiseRecipes {

    public static final ResourceLocation SPINNING_JENNY = DataPackLang.parseId("weaversparadise:spinning_jenny");
    public static final ResourceLocation CLOTHCRAFTING = DataPackLang.parseId("weaversparadise:clothcrafting");

    /** One clothcrafting output tier, as the mod scores it. */
    public record Tier(int minScore, int maxScore, ItemStack result, int count) {}

    /** What the adapter needs to finish a clothcrafting recipe beyond its discovered shape. */
    public record ClothcraftingInfo(List<Tier> tiers, ItemStack spoolReturn, int spoolReturnCount) {
        public Tier best() {
            Tier best = null;
            for (Tier tier : tiers) {
                if (best == null || tier.maxScore() > best.maxScore()) best = tier;
            }
            return best;
        }

        /** The tier a score lands in, or the highest tier the score clears, or the lowest. */
        public @Nullable Tier forScore(int score) {
            Tier below = null;
            Tier lowest = null;
            for (Tier tier : tiers) {
                if (score >= tier.minScore() && score <= tier.maxScore()) return tier;
                if (tier.maxScore() <= score && (below == null || tier.maxScore() > below.maxScore())) below = tier;
                if (lowest == null || tier.minScore() < lowest.minScore()) lowest = tier;
            }
            return below != null ? below : lowest;
        }

        public int maxScore() {
            int max = 0;
            for (Tier tier : tiers) max = Math.max(max, tier.maxScore());
            return max;
        }
    }

    private static final Map<ResourceLocation, ClothcraftingInfo> CLOTHCRAFTING_INFO = new ConcurrentHashMap<>();

    private WeaversParadiseRecipes() {}

    public static void bootstrap() {
        RecipeTypeSources.register(SPINNING_JENNY, WeaversParadiseRecipes::discoverJenny);
        RecipeTypeSources.register(CLOTHCRAFTING, WeaversParadiseRecipes::discoverClothcrafting);
    }

    public static @Nullable ClothcraftingInfo clothcrafting(@Nullable ResourceLocation recipeId) {
        return recipeId == null ? null : CLOTHCRAFTING_INFO.get(recipeId);
    }

    static List<DiscoveredRecipe> discoverJenny(ServerLevel level, WorkstationDef def) {
        List<DiscoveredRecipe> out = new ArrayList<>();
        forEachRecipe(level, SPINNING_JENNY, (recipeId, recipe) -> {
            Ingredient input = ingredient(invoke(recipe, "getInput"));
            Ingredient catalyst = ingredient(invoke(recipe, "getCatalyst"));
            int count = integer(invoke(recipe, "getCountRequired"), 1);
            int time = integer(invoke(recipe, "getCraftTime"), def.cookTimeTicks());
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (input == null || result == null || result.isEmpty()) return;
            List<RecipeIngredient> inputs = new ArrayList<>();
            RecipeIngredient main = ingredientOf(input, count);
            if (main == null) return;
            inputs.add(main);
            RecipeIngredient spool = catalyst == null ? null : ingredientOf(catalyst, 1);
            if (spool != null) inputs.add(spool);
            out.add(discovered(def, recipeId, BuiltInRegistries.ITEM.getKey(result.getItem()), 1, time, inputs));
        });
        return out;
    }

    static List<DiscoveredRecipe> discoverClothcrafting(ServerLevel level, WorkstationDef def) {
        List<DiscoveredRecipe> out = new ArrayList<>();
        forEachRecipe(level, CLOTHCRAFTING, (recipeId, recipe) -> {
            Ingredient ingredient = ingredient(invoke(recipe, "getIngredient"));
            int cost = integer(invoke(recipe, "getSpoolCost"), 6);
            int time = integer(invoke(recipe, "getGameDuration"), def.cookTimeTicks());
            Object returnStack = invoke(recipe, "getSpoolReturn");
            int returnCount = integer(invoke(recipe, "getSpoolReturnCount"), 0);
            List<Tier> tiers = tiers(invoke(recipe, "getTiers"));
            if (ingredient == null || tiers.isEmpty()) return;
            RecipeIngredient spools = ingredientOf(ingredient, cost);
            if (spools == null) return;
            ClothcraftingInfo info = new ClothcraftingInfo(tiers,
                    returnStack instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY, returnCount);
            Tier best = info.best();
            if (best == null || best.result().isEmpty()) return;
            CLOTHCRAFTING_INFO.put(recipeId, info);
            out.add(discovered(def, recipeId, BuiltInRegistries.ITEM.getKey(best.result().getItem()),
                    Math.max(1, best.count()), time, List.of(spools)));
        });
        return out;
    }

    private static DiscoveredRecipe discovered(WorkstationDef def, ResourceLocation recipeId, ResourceLocation output,
                                               int count, int time, List<RecipeIngredient> inputs) {
        return new DiscoveredRecipe(recipeId, def.role(), def.recipeTier() > 0 ? def.recipeTier() : 1,
                output, count, Math.max(1, time), false, null, 0, List.copyOf(inputs), false, def.beverage(), null);
    }

    private interface RecipeVisitor {
        void visit(ResourceLocation id, Recipe<?> recipe);
    }

    private static void forEachRecipe(ServerLevel level, ResourceLocation typeId, RecipeVisitor visitor) {
        //? if >=1.21 {
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : WorkRecipeRegistry.getRecipesForType(level, typeId)) {
            visitor.visit(holder.id(), holder.value());
        }
        //?} else {
        /*for (Recipe<?> recipe : WorkRecipeRegistry.getRecipesForType(level, typeId)) {
            visitor.visit(recipe.getId(), recipe);
        }
        *///?}
    }

    private static List<Tier> tiers(@Nullable Object value) {
        List<Tier> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        for (Object tier : list) {
            Object result = invoke(tier, "result");
            if (!(result instanceof ItemStack stack)) continue;
            out.add(new Tier(integer(invoke(tier, "minScore"), 0), integer(invoke(tier, "maxScore"), 0),
                    stack.copy(), integer(invoke(tier, "count"), 1)));
        }
        return out;
    }

    private static @Nullable RecipeIngredient ingredientOf(Ingredient ingredient, int count) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (ItemStack option : ingredient.getItems()) {
            if (!option.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(option.getItem()));
        }
        return ids.isEmpty() ? null : new RecipeIngredient(List.copyOf(ids), Math.max(1, count));
    }

    private static @Nullable Ingredient ingredient(@Nullable Object value) {
        return value instanceof Ingredient ingredient ? ingredient : null;
    }

    private static int integer(@Nullable Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static @Nullable Object invoke(@Nullable Object owner, String method) {
        if (owner == null) return null;
        try {
            Method found = owner.getClass().getMethod(method);
            return found.getParameterCount() == 0 ? found.invoke(owner) : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
