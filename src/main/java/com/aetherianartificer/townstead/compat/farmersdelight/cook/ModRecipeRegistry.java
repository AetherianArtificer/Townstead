package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
//? if >=1.21 {
import net.minecraft.world.item.crafting.RecipeHolder;
//?}
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
            @Nullable ResourceLocation containerItemId,
            int containerCount,
            List<RecipeIngredient> inputs,
            boolean purification,
            boolean beverage,
            //? if >=1.21 {
            @Nullable RecipeHolder<?> source
            //?} else {
            /*@Nullable Recipe<?> source
            *///?}
    ) {}

    //? if >=1.21 {
    private static final ResourceLocation FD_COOKING_POT = ResourceLocation.parse("farmersdelight:cooking_pot");
    private static final ResourceLocation FD_COOKING_TYPE_ID = ResourceLocation.parse("farmersdelight:cooking");
    private static final ResourceLocation FD_CUTTING_TYPE_ID = ResourceLocation.parse("farmersdelight:cutting");
    private static final ResourceLocation MINECRAFT_BOWL = ResourceLocation.parse("minecraft:bowl");
    private static final ResourceLocation MINECRAFT_POTION = ResourceLocation.parse("minecraft:potion");
    private static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "impure_water_container");
    private static final ResourceLocation RUSTIC_COFFEE_BEANS =
            ResourceLocation.parse("rusticdelight:coffee_beans");
    private static final ResourceLocation RUSTIC_ROASTED_COFFEE_BEANS =
            ResourceLocation.parse("rusticdelight:roasted_coffee_beans");
    //?} else {
    /*private static final ResourceLocation FD_COOKING_POT = new ResourceLocation("farmersdelight", "cooking_pot");
    private static final ResourceLocation FD_COOKING_TYPE_ID = new ResourceLocation("farmersdelight", "cooking");
    private static final ResourceLocation FD_CUTTING_TYPE_ID = new ResourceLocation("farmersdelight", "cutting");
    private static final ResourceLocation MINECRAFT_BOWL = new ResourceLocation("minecraft", "bowl");
    private static final ResourceLocation MINECRAFT_POTION = new ResourceLocation("minecraft", "potion");
    private static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
            new ResourceLocation(Townstead.MOD_ID, "impure_water_container");
    private static final ResourceLocation RUSTIC_COFFEE_BEANS =
            new ResourceLocation("rusticdelight", "coffee_beans");
    private static final ResourceLocation RUSTIC_ROASTED_COFFEE_BEANS =
            new ResourceLocation("rusticdelight", "roasted_coffee_beans");
    *///?}

    private static final TagKey<Item>[] TIER_TAGS;
    static {
        @SuppressWarnings("unchecked")
        TagKey<Item>[] tags = new TagKey[5];
        for (int i = 0; i < 5; i++) {
            //? if >=1.21 {
            tags[i] = TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "recipe_tier_" + (i + 1)));
            //?} else {
            /*tags[i] = TagKey.create(Registries.ITEM,
                    new ResourceLocation(Townstead.MOD_ID, "recipe_tier_" + (i + 1)));
            *///?}
        }
        TIER_TAGS = tags;
    }

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

    public static List<DiscoveredRecipe> getRecipesForStation(ServerLevel level, StationType stationType) {
        return getRecipes(level).stream()
                .filter(r -> r.stationType() == stationType)
                .toList();
    }

    public static List<DiscoveredRecipe> getRecipesForStation(ServerLevel level, StationType stationType, int maxTier) {
        return getRecipes(level).stream()
                .filter(r -> r.stationType() == stationType && r.tier() <= maxTier)
                .toList();
    }

    public static List<DiscoveredRecipe> getFoodRecipesForStation(ServerLevel level, StationType stationType) {
        return getRecipes(level).stream()
                .filter(r -> r.stationType() == stationType && !r.beverage())
                .toList();
    }

    public static List<DiscoveredRecipe> getFoodRecipesForStation(ServerLevel level, StationType stationType, int maxTier) {
        return getRecipes(level).stream()
                .filter(r -> r.stationType() == stationType && r.tier() <= maxTier && !r.beverage())
                .toList();
    }

    public static List<DiscoveredRecipe> getBeverageRecipesForStation(ServerLevel level, StationType stationType) {
        return getRecipes(level).stream()
                .filter(r -> r.stationType() == stationType && r.beverage())
                .toList();
    }

    public static List<DiscoveredRecipe> getBeverageRecipesForStation(ServerLevel level, StationType stationType, int maxTier) {
        return getRecipes(level).stream()
                .filter(r -> r.stationType() == stationType && r.tier() <= maxTier && r.beverage())
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
            if (r.containerItemId() != null && r.containerCount() > 0) ids.add(r.containerItemId());
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
        ThirstCompatBridge thirstBridge = ThirstBridgeResolver.get();
        if (thirstBridge != null && TownsteadConfig.isCookWaterPurificationEnabled() && thirstBridge.supportsPurification()) {
            recipes.add(syntheticPurificationRecipe());
        }

        // 5. Synthetic coffee roasting recipe (if Rustic Delight is present and no campfire recipe already covers it)
        if (BuiltInRegistries.ITEM.containsKey(RUSTIC_COFFEE_BEANS)
                && BuiltInRegistries.ITEM.containsKey(RUSTIC_ROASTED_COFFEE_BEANS)) {
            boolean alreadyHasRoasting = recipes.stream().anyMatch(r ->
                    r.stationType() == StationType.FIRE_STATION
                            && r.output().equals(RUSTIC_ROASTED_COFFEE_BEANS));
            if (!alreadyHasRoasting) {
                recipes.add(syntheticCoffeeRoastingRecipe());
            }
        }

        // 6. Apply tag-based tier overrides
        applyTierTagOverrides(level, recipes);

        return recipes;
    }

    // ── Campfire recipes ──

    private static void discoverCampfireRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        //? if >=1.21 {
        for (RecipeHolder<?> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation recipeId = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
            ResourceLocation recipeId = recipe.getId();
        *///?}
            ItemStack result = safeGetResult(level, recipe);
            if (result.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (outputId == null) continue;

            List<RecipeIngredient> inputs = extractIngredients(recipe);
            if (inputs.isEmpty()) continue;

            int cookTime = safeCookTime(recipe, 100);
            boolean beverage = outputId.getPath().contains("coffee");
            int tier = autoTier(StationType.FIRE_STATION, inputs.size(), cookTime);

            out.add(new DiscoveredRecipe(
                    recipeId,
                    StationType.FIRE_STATION,
                    tier,
                    outputId,
                    Math.max(1, result.getCount()),
                    cookTime,
                    false,
                    null,
                    0,
                    inputs,
                    false,
                    beverage,
                    //? if >=1.21 {
                    holder
                    //?} else {
                    /*recipe
                    *///?}
            ));
        }
    }

    // ── Cooking pot recipes ──

    @SuppressWarnings("unchecked")
    private static void discoverCookingPotRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        //? if >=1.21 {
        Collection<RecipeHolder<?>> holders = getRecipesForType(level, FD_COOKING_TYPE_ID);
        //?} else {
        /*Collection<Recipe<?>> holders = getRecipesForType(level, FD_COOKING_TYPE_ID);
        *///?}

        Set<String> signatures = new HashSet<>();
        //? if >=1.21 {
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();
            ResourceLocation recipeId = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : holders) {
            ResourceLocation recipeId = recipe.getId();
        *///?}

            ItemStack result = safeGetResult(level, recipe);
            if (result.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (outputId == null) continue;

            List<RecipeIngredient> inputs = extractIngredients(recipe);
            if (inputs.isEmpty()) continue;

            ContainerInfo container = reflectContainer(recipe);
            int cookTime = safeCookTime(recipe, 120);
            int tier = autoTier(StationType.HOT_STATION, inputs.size(), cookTime);

            String sig = outputId + "|" + result.getCount() + "|" + inputs;
            if (!signatures.add(sig)) continue;

            out.add(new DiscoveredRecipe(
                    recipeId,
                    StationType.HOT_STATION,
                    tier,
                    outputId,
                    Math.max(1, result.getCount()),
                    cookTime,
                    false,
                    container.itemId(),
                    container.count(),
                    inputs,
                    false,
                    container.beverage(),
                    //? if >=1.21 {
                    holder
                    //?} else {
                    /*recipe
                    *///?}
            ));
        }
    }

    // ── Cutting board recipes ──

    @SuppressWarnings("unchecked")
    private static void discoverCuttingBoardRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        //? if >=1.21 {
        Collection<RecipeHolder<?>> holders = getRecipesForType(level, FD_CUTTING_TYPE_ID);
        //?} else {
        /*Collection<Recipe<?>> holders = getRecipesForType(level, FD_CUTTING_TYPE_ID);
        *///?}

        Set<String> signatures = new HashSet<>();
        //? if >=1.21 {
        for (RecipeHolder<?> holder : holders) {
            Recipe<?> recipe = holder.value();
            ResourceLocation recipeId = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : holders) {
            ResourceLocation recipeId = recipe.getId();
        *///?}

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
                    recipeId,
                    StationType.CUTTING_BOARD,
                    tier,
                    outputId,
                    Math.max(1, result.getCount()),
                    cookTime,
                    requiresTool,
                    null,
                    0,
                    inputs,
                    false,
                    false,
                    //? if >=1.21 {
                    holder
                    //?} else {
                    /*recipe
                    *///?}
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

    // ── Tag-based tier overrides ──

    private static void applyTierTagOverrides(ServerLevel level, List<DiscoveredRecipe> recipes) {
        for (int i = 0; i < recipes.size(); i++) {
            DiscoveredRecipe r = recipes.get(i);
            Item outputItem = BuiltInRegistries.ITEM.get(r.output());
            if (outputItem == Items.AIR) continue;
            ItemStack probe = new ItemStack(outputItem);
            for (int t = 0; t < TIER_TAGS.length; t++) {
                if (probe.is(TIER_TAGS[t])) {
                    int newTier = t + 1;
                    if (newTier != r.tier()) {
                        recipes.set(i, new DiscoveredRecipe(
                                r.id(), r.stationType(), newTier, r.output(), r.outputCount(),
                                r.cookTimeTicks(), r.requiresTool(), r.containerItemId(), r.containerCount(),
                                r.inputs(), r.purification(), r.beverage(), r.source()
                        ));
                    }
                    break;
                }
            }
        }
    }

    // ── Synthetic purification recipe ──

    private static DiscoveredRecipe syntheticPurificationRecipe() {
        return new DiscoveredRecipe(
                //? if >=1.21 {
                ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "purification"),
                //?} else {
                /*new ResourceLocation(Townstead.MOD_ID, "purification"),
                *///?}
                StationType.FIRE_STATION,
                1,
                MINECRAFT_POTION,
                1,
                100,
                false,
                null,
                0,
                List.of(new RecipeIngredient(List.of(TOWNSTEAD_IMPURE_WATER_INPUT), 1)),
                true,
                ModCompat.isLoaded("rusticdelight"),
                null
        );
    }

    // ── Synthetic coffee roasting recipe ──

    private static DiscoveredRecipe syntheticCoffeeRoastingRecipe() {
        return new DiscoveredRecipe(
                //? if >=1.21 {
                ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "coffee_roasting"),
                //?} else {
                /*new ResourceLocation(Townstead.MOD_ID, "coffee_roasting"),
                *///?}
                StationType.FIRE_STATION,
                1,
                RUSTIC_ROASTED_COFFEE_BEANS,
                1,
                100,
                false,
                null,
                0,
                List.of(new RecipeIngredient(List.of(RUSTIC_COFFEE_BEANS), 1)),
                false,
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
    //? if >=1.21 {
    @SuppressWarnings("unchecked")
    private static List<RecipeHolder<?>> getRecipesForType(ServerLevel level, ResourceLocation typeId) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(typeId);
        if (type == null) return List.of();
        ResourceLocation resolved = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (!typeId.equals(resolved)) return List.of();
        try {
            return (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                    .getAllRecipesFor((RecipeType) type);
        } catch (Throwable e) {
            return List.of();
        }
    }
    //?} else {
    /*@SuppressWarnings("unchecked")
    private static List<Recipe<?>> getRecipesForType(ServerLevel level, ResourceLocation typeId) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(typeId);
        if (type == null) return List.of();
        ResourceLocation resolved = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        if (!typeId.equals(resolved)) return List.of();
        try {
            return (List<Recipe<?>>) (List<?>) level.getRecipeManager()
                    .getAllRecipesFor((RecipeType) type);
        } catch (Throwable e) {
            return List.of();
        }
    }
    *///?}

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

    private record ContainerInfo(@Nullable ResourceLocation itemId, int count, boolean beverage) {
        static final ContainerInfo NONE = new ContainerInfo(null, 0, false);
    }

    private static ContainerInfo reflectContainer(Recipe<?> recipe) {
        String[] methods = {"getContainer", "getOutputContainer"};
        for (String name : methods) {
            try {
                Method m = recipe.getClass().getMethod(name);
                Object value = m.invoke(recipe);
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    if (stack.is(Items.BOWL)) {
                        return new ContainerInfo(MINECRAFT_BOWL, Math.max(1, stack.getCount()), false);
                    }
                    if (stack.is(Items.GLASS_BOTTLE)) {
                        ResourceLocation bottleId = BuiltInRegistries.ITEM.getKey(Items.GLASS_BOTTLE);
                        return new ContainerInfo(bottleId, Math.max(1, stack.getCount()), true);
                    }
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (itemId != null && stack.getItem() != Items.AIR) {
                        return new ContainerInfo(itemId, Math.max(1, stack.getCount()), false);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return ContainerInfo.NONE;
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

    public static boolean recipeToolMatches(DiscoveredRecipe recipe, ItemStack stack) {
        if (recipe == null || !recipe.requiresTool() || stack == null || stack.isEmpty()) return false;
        Object source = recipe.source();
        //? if >=1.21 {
        if (source instanceof RecipeHolder<?> holder) source = holder.value();
        //?}
        if (!(source instanceof Recipe<?> mcRecipe)) return false;
        try {
            Method m = mcRecipe.getClass().getMethod("getTool");
            Object value = m.invoke(mcRecipe);
            if (value instanceof net.minecraft.world.item.crafting.Ingredient toolIng) {
                return !toolIng.isEmpty() && toolIng.test(stack);
            }
        } catch (Throwable ignored) {}
        return StationHandler.isKnifeStack(stack);
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
