package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.thirst.ThirstWasTakenBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;

public final class ModRecipeRegistry {
    private ModRecipeRegistry() {}

    public enum StationType { CUTTING_BOARD, HOT_STATION, FIRE_STATION }

    public record RecipeIngredient(List<ResourceLocation> itemIds, int count) {
        public ResourceLocation primaryId() { return itemIds.get(0); }
    }

    public record DiscoveredRecipe(
            ResourceLocation id,
            StationType stationType,
            int tier,
            ResourceLocation output,
            int outputCount,
            int cookTimeTicks,
            boolean requiresTool,
            int bowlsRequired,
            List<RecipeIngredient> inputs,
            boolean purification,
            @Nullable RecipeHolder<?> source
    ) {}

    private static final ResourceLocation FD_COOKING_POT = ResourceLocation.parse("farmersdelight:cooking_pot");
    private static final ResourceLocation FD_COOKING_TYPE_ID = ResourceLocation.parse("farmersdelight:cooking");
    private static final ResourceLocation FD_CUTTING_TYPE_ID = ResourceLocation.parse("farmersdelight:cutting");
    private static final ResourceLocation MINECRAFT_BOWL = ResourceLocation.parse("minecraft:bowl");
    private static final ResourceLocation MINECRAFT_POTION = ResourceLocation.parse("minecraft:potion");
    private static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "impure_water_container");

    private static final long CACHE_TICKS = 200L;
    private static ResourceLocation cachedDimension = null;
    private static long cacheUntilTick = Long.MIN_VALUE;
    private static List<DiscoveredRecipe> cachedRecipes = List.of();

    public static List<DiscoveredRecipe> getRecipes(ServerLevel level) {
        ResourceLocation dimension = level.dimension().location();
        long now = level.getGameTime();
        if (dimension.equals(cachedDimension) && now < cacheUntilTick) {
            return cachedRecipes;
        }
        List<DiscoveredRecipe> discovered = discoverAllRecipes(level);
        cachedDimension = dimension;
        cacheUntilTick = now + CACHE_TICKS;
        cachedRecipes = List.copyOf(discovered);
        return cachedRecipes;
    }

    public static List<DiscoveredRecipe> getRecipesForStation(ServerLevel level, StationType stationType, int maxTier) {
        return getRecipes(level).stream()
                .filter(r -> r.stationType() == stationType && r.tier() <= maxTier)
                .toList();
    }

    public static Set<ResourceLocation> allOutputIds(ServerLevel level) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (DiscoveredRecipe r : getRecipes(level)) {
            ids.add(r.output());
        }
        return ids;
    }

    public static Set<ResourceLocation> allInputIds(ServerLevel level) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (DiscoveredRecipe r : getRecipes(level)) {
            if (r.bowlsRequired() > 0) ids.add(MINECRAFT_BOWL);
            for (RecipeIngredient ing : r.inputs()) {
                ids.addAll(ing.itemIds());
            }
        }
        return ids;
    }

    private static List<DiscoveredRecipe> discoverAllRecipes(ServerLevel level) {
        List<DiscoveredRecipe> recipes = new ArrayList<>();

        // 1. Campfire cooking recipes → FIRE_STATION
        discoverCampfireRecipes(level, recipes);

        // 2. FD cooking pot recipes → HOT_STATION
        discoverCookingPotRecipes(level, recipes);

        // 3. FD cutting board recipes → CUTTING_BOARD
        discoverCuttingBoardRecipes(level, recipes);

        // 4. Synthetic purification recipe
        if (TownsteadConfig.isCookWaterPurificationEnabled() && ThirstWasTakenBridge.INSTANCE.isActive()) {
            recipes.add(syntheticPurificationRecipe());
        }

        return recipes;
    }

    // ── Campfire recipes ──

    private static void discoverCampfireRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        for (RecipeHolder<?> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            Recipe<?> recipe = holder.value();
            ItemStack result = safeGetResult(level, recipe);
            if (result.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (outputId == null) continue;

            List<RecipeIngredient> inputs = extractIngredients(recipe);
            if (inputs.isEmpty()) continue;

            int cookTime = safeCookTime(recipe, 100);
            int tier = autoTier(StationType.FIRE_STATION, inputs.size(), cookTime);

            out.add(new DiscoveredRecipe(
                    holder.id(),
                    StationType.FIRE_STATION,
                    tier,
                    outputId,
                    Math.max(1, result.getCount()),
                    cookTime,
                    false,
                    0,
                    inputs,
                    false,
                    holder
            ));
        }
    }

    // ── Cooking pot recipes ──

    @SuppressWarnings("unchecked")
    private static void discoverCookingPotRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        Collection<RecipeHolder<?>> holders = getRecipesForType(level, FD_COOKING_TYPE_ID);

        Set<String> signatures = new HashSet<>();
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();

            ItemStack result = safeGetResult(level, recipe);
            if (result.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (outputId == null) continue;

            List<RecipeIngredient> inputs = extractIngredients(recipe);
            if (inputs.isEmpty()) continue;

            int bowls = reflectContainerBowls(recipe);
            int cookTime = safeCookTime(recipe, 120);
            int tier = autoTier(StationType.HOT_STATION, inputs.size(), cookTime);

            String sig = outputId + "|" + result.getCount() + "|" + inputs;
            if (!signatures.add(sig)) continue;

            out.add(new DiscoveredRecipe(
                    holder.id(),
                    StationType.HOT_STATION,
                    tier,
                    outputId,
                    Math.max(1, result.getCount()),
                    cookTime,
                    false,
                    bowls,
                    inputs,
                    false,
                    holder
            ));
        }
    }

    // ── Cutting board recipes ──

    @SuppressWarnings("unchecked")
    private static void discoverCuttingBoardRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        Collection<RecipeHolder<?>> holders = getRecipesForType(level, FD_CUTTING_TYPE_ID);

        Set<String> signatures = new HashSet<>();
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();

            ItemStack result = safeGetResult(level, recipe);
            if (result.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (outputId == null) continue;

            List<RecipeIngredient> inputs = extractIngredients(recipe);
            if (inputs.isEmpty()) continue;

            boolean requiresTool = reflectRequiresTool(recipe);
            int cookTime = safeCookTime(recipe, 80);
            int tier = autoTier(StationType.CUTTING_BOARD, inputs.size(), cookTime);

            String sig = outputId + "|" + result.getCount() + "|" + inputs;
            if (!signatures.add(sig)) continue;

            out.add(new DiscoveredRecipe(
                    holder.id(),
                    StationType.CUTTING_BOARD,
                    tier,
                    outputId,
                    Math.max(1, result.getCount()),
                    cookTime,
                    requiresTool,
                    0,
                    inputs,
                    false,
                    holder
            ));
        }
    }

    // ── Auto-tiering ──

    static int autoTier(StationType stationType, int ingredientCount, int cookTimeTicks) {
        int base = switch (stationType) {
            case FIRE_STATION -> 1;
            case CUTTING_BOARD -> 2;
            case HOT_STATION -> 3;
        };
        int complexity = Math.max(0, (ingredientCount - 1) / 2);
        int timePenalty = cookTimeTicks > 200 ? 1 : 0;
        return Math.min(5, base + complexity + timePenalty);
    }

    // ── Synthetic purification recipe ──

    private static DiscoveredRecipe syntheticPurificationRecipe() {
        return new DiscoveredRecipe(
                ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "purification"),
                StationType.FIRE_STATION,
                1,
                MINECRAFT_POTION,
                1,
                100,
                false,
                0,
                List.of(new RecipeIngredient(List.of(TOWNSTEAD_IMPURE_WATER_INPUT), 1)),
                true,
                null
        );
    }

    // ── Recipe type lookup ──

    /**
     * Looks up a {@link RecipeType} by its registry ID and returns all recipes
     * registered under that type. This works for any mod that registers recipes
     * under the FD recipe types (e.g. addon mods adding cooking pot recipes).
     */
    @SuppressWarnings("unchecked")
    private static List<RecipeHolder<?>> getRecipesForType(ServerLevel level, ResourceLocation typeId) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(typeId);
        if (type == null) return List.of();
        // RecipeType identity check — the registry returns the default (crafting) for unknown keys
        ResourceLocation resolved = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (!typeId.equals(resolved)) return List.of();
        try {
            return (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                    .getAllRecipesFor((RecipeType) type);
        } catch (Throwable e) {
            return List.of();
        }
    }

    // ── Reflection helpers ──

    private static ItemStack safeGetResult(ServerLevel level, Recipe<?> recipe) {
        try {
            ItemStack fromApi = recipe.getResultItem(level.registryAccess());
            if (fromApi != null && !fromApi.isEmpty()) return fromApi.copy();
        } catch (Throwable ignored) {}
        try {
            Method m = recipe.getClass().getMethod("getResultItem");
            Object value = m.invoke(recipe);
            if (value instanceof ItemStack stack && !stack.isEmpty()) return stack.copy();
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    private static int safeCookTime(Recipe<?> recipe, int fallback) {
        String[] methods = {"getCookingTime", "getCookTime", "getCookTimeInTicks"};
        for (String name : methods) {
            try {
                Method m = recipe.getClass().getMethod(name);
                Object value = m.invoke(recipe);
                if (value instanceof Number n) return Math.max(1, n.intValue());
            } catch (Throwable ignored) {}
        }
        return fallback;
    }

    private static int reflectContainerBowls(Recipe<?> recipe) {
        String[] methods = {"getContainer", "getOutputContainer"};
        for (String name : methods) {
            try {
                Method m = recipe.getClass().getMethod(name);
                Object value = m.invoke(recipe);
                if (value instanceof ItemStack stack && stack.is(Items.BOWL)) {
                    return Math.max(1, stack.getCount());
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static boolean reflectRequiresTool(Recipe<?> recipe) {
        try {
            Method m = recipe.getClass().getMethod("getTool");
            Object value = m.invoke(recipe);
            if (value instanceof net.minecraft.world.item.crafting.Ingredient toolIng) {
                return !toolIng.isEmpty();
            }
        } catch (Throwable ignored) {}
        return true; // Default: assume cutting board recipes need a knife
    }

    private static List<RecipeIngredient> extractIngredients(Recipe<?> recipe) {
        List<RecipeIngredient> result = new ArrayList<>();
        for (net.minecraft.world.item.crafting.Ingredient mcIng : recipe.getIngredients()) {
            if (mcIng == null || mcIng.isEmpty()) continue;
            List<ResourceLocation> ids = itemIdsFromIngredient(mcIng);
            if (ids.isEmpty()) continue;
            int count = reflectIngredientCount(mcIng);
            result.add(new RecipeIngredient(ids, count));
        }
        return result;
    }

    private static int reflectIngredientCount(net.minecraft.world.item.crafting.Ingredient ingredient) {
        String[] methods = {"getCount", "count", "getAmount", "amount"};
        for (String name : methods) {
            try {
                Method m = ingredient.getClass().getMethod(name);
                Object value = m.invoke(ingredient);
                if (value instanceof Number n && n.intValue() > 0) return n.intValue();
            } catch (Throwable ignored) {}
        }
        return 1;
    }

    private static List<ResourceLocation> itemIdsFromIngredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        ItemStack[] options = ingredient.getItems();
        if (options == null || options.length == 0) return List.of();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (ItemStack option : options) {
            if (option == null || option.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(option.getItem());
            if (id != null && !id.equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) {
                ids.add(id);
            }
        }
        return ids.isEmpty() ? List.of() : List.copyOf(ids);
    }
}
