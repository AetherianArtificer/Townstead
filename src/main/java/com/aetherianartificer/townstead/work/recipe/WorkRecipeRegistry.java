package com.aetherianartificer.townstead.work.recipe;


import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;
import com.aetherianartificer.townstead.work.station.WorkstationRecipeTypes;
import com.aetherianartificer.townstead.work.station.WorkstationV2Def;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.work.producer.ProducerRecipe;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
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
import java.util.Locale;

public final class WorkRecipeRegistry {
    private WorkRecipeRegistry() {}

    //? if >=1.21 {
    private static final ResourceLocation MINECRAFT_POTION = ResourceLocation.parse("minecraft:potion");
    //?} else {
    /*private static final ResourceLocation MINECRAFT_POTION = new ResourceLocation("minecraft", "potion");
    *///?}

    /** One definition of the id, shared with the line that fills it. */
    private static final ResourceLocation TOWNSTEAD_IMPURE_WATER_INPUT =
            com.aetherianartificer.townstead.supply.TownsteadSupplyLines.IMPURE_WATER;

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

    // Discovery reads the recipe manager and the tag sets, so its result can only change when
    // datapacks reload. The cache is therefore keyed on a reload generation rather than a tick
    // deadline: no rediscovery on a timer, and no rediscovery when cooks in two dimensions take
    // turns asking (the recipe manager is server-wide, so the old per-dimension key only ever
    // caused thrash). Derived caches key off the same counter to stay coherent with it.
    private static int cachedGeneration = -1;
    private static List<DiscoveredRecipe> cachedRecipes = List.of();
    private static List<DiscoveredRecipe> cachedFoodRecipes = List.of();
    private static List<DiscoveredRecipe> cachedBeverageRecipes = List.of();
    private static Map<StationType, List<DiscoveredRecipe>> cachedStationRecipes = Map.of();
    private static Map<StationType, List<DiscoveredRecipe>> cachedFoodStationRecipes = Map.of();
    private static Map<StationType, List<DiscoveredRecipe>> cachedBeverageStationRecipes = Map.of();

    /** The current reload generation; derived caches store it to detect a rediscovery. */
    public static int generation() {
        return com.aetherianartificer.townstead.data.ReloadGeneration.current();
    }

    /** Drops the discovery cache. Called when datapacks finish reloading. */
    public static void invalidate() {
        com.aetherianartificer.townstead.data.ReloadGeneration.bump();
    }

    public static List<DiscoveredRecipe> getRecipes(ServerLevel level) {
        int current = generation();
        if (cachedGeneration == current) {
            return cachedRecipes;
        }
        List<DiscoveredRecipe> discovered = discoverAllRecipes(level);
        cachedRecipes = List.copyOf(discovered);
        rebuildRecipeViews(cachedRecipes);
        cachedGeneration = current;
        return cachedRecipes;
    }

    public static List<DiscoveredRecipe> getRecipesForStation(ServerLevel level, StationType stationType) {
        getRecipes(level);
        return cachedStationRecipes.getOrDefault(stationType, List.of());
    }


    public static List<DiscoveredRecipe> getFoodRecipesForStation(ServerLevel level, StationType stationType) {
        getRecipes(level);
        return cachedFoodStationRecipes.getOrDefault(stationType, List.of());
    }


    public static List<DiscoveredRecipe> getBeverageRecipesForStation(ServerLevel level, StationType stationType) {
        getRecipes(level);
        return cachedBeverageStationRecipes.getOrDefault(stationType, List.of());
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

    public static List<DiscoveredRecipe> getFoodRecipes(ServerLevel level) {
        getRecipes(level);
        return cachedFoodRecipes;
    }

    public static List<DiscoveredRecipe> getBeverageRecipes(ServerLevel level) {
        getRecipes(level);
        return cachedBeverageRecipes;
    }

    private static void rebuildRecipeViews(List<DiscoveredRecipe> recipes) {
        List<DiscoveredRecipe> foodRecipes = new ArrayList<>();
        List<DiscoveredRecipe> beverageRecipes = new ArrayList<>();
        EnumMap<StationType, List<DiscoveredRecipe>> stationRecipes = new EnumMap<>(StationType.class);
        EnumMap<StationType, List<DiscoveredRecipe>> foodStationRecipes = new EnumMap<>(StationType.class);
        EnumMap<StationType, List<DiscoveredRecipe>> beverageStationRecipes = new EnumMap<>(StationType.class);

        for (StationType stationType : StationType.values()) {
            stationRecipes.put(stationType, new ArrayList<>());
            foodStationRecipes.put(stationType, new ArrayList<>());
            beverageStationRecipes.put(stationType, new ArrayList<>());
        }

        for (DiscoveredRecipe recipe : recipes) {
            stationRecipes.get(recipe.stationType()).add(recipe);
            if (recipe.beverage()) {
                beverageRecipes.add(recipe);
                beverageStationRecipes.get(recipe.stationType()).add(recipe);
            } else {
                foodRecipes.add(recipe);
                foodStationRecipes.get(recipe.stationType()).add(recipe);
            }
        }

        cachedFoodRecipes = List.copyOf(foodRecipes);
        cachedBeverageRecipes = List.copyOf(beverageRecipes);
        cachedStationRecipes = freezeRecipeViewMap(stationRecipes);
        cachedFoodStationRecipes = freezeRecipeViewMap(foodStationRecipes);
        cachedBeverageStationRecipes = freezeRecipeViewMap(beverageStationRecipes);
    }

    private static Map<StationType, List<DiscoveredRecipe>> freezeRecipeViewMap(EnumMap<StationType, List<DiscoveredRecipe>> source) {
        EnumMap<StationType, List<DiscoveredRecipe>> frozen = new EnumMap<>(StationType.class);
        for (Map.Entry<StationType, List<DiscoveredRecipe>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static List<DiscoveredRecipe> discoverAllRecipes(ServerLevel level) {
        List<DiscoveredRecipe> recipes = new ArrayList<>();

        // 1. Campfire cooking recipes → FIRE_STATION
        discoverCampfireRecipes(level, recipes);

        // 2. Block-attached and legacy workstation recipe types → their stations' roles
        discoverWorkstationRecipes(level, recipes);

        // 3. Protocol stations' declared production lines → synthetic recipes
        discoverProtocolRecipes(level, recipes);

        // 4. Two-stage fluid stations (ferment then pour), joined into item-in/item-out form
        discoverFluidRecipes(level, recipes);

        // 5. Synthetic purification recipe
        ThirstCompatBridge thirstBridge = ThirstBridgeResolver.get();
        if (thirstBridge != null && TownsteadConfig.isCookWaterPurificationEnabled() && thirstBridge.supportsPurification()) {
            DiscoveredRecipe purification = syntheticPurificationRecipe(thirstBridge);
            // The impure vessel shares the purified one's item id — the difference lives in
            // its data — so the order line's count must ask the bridge, not the id, or "keep
            // 10 purified" reads full while the shelf holds swamp water. The threshold is the
            // same line the work draws: below it is input, at it is output.
            com.aetherianartificer.townstead.work.order.OrderStackFilters.register(
                    purification.output(),
                    stack -> thirstBridge.purity(stack) >= ThirstCompatBridge.PURITY_PURIFIED);
            recipes.add(purification);
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

    // ── Workstation-declared recipe types ──

    /**
     * Generic discovery for data-declared workstations: every recipe of a def's declared
     * {@code recipe_type} maps through the vanilla recipe interface (ingredients + result) onto
     * the def's station role. Pairing back to those stations is exclusive and enforced in
     * {@link com.aetherianartificer.townstead.work.station.StationProtocols#supports}.
     */
    private static void discoverWorkstationRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        List<WorkstationDef> defs = Workstations.all();
        if (defs.isEmpty() && Workstations.v2All().isEmpty()) return;
        Set<ResourceLocation> existingIds = new HashSet<>();
        for (DiscoveredRecipe r : out) existingIds.add(r.id());
        Set<ResourceLocation> seenTypes = new HashSet<>();

        // V2: the exact block owns an additive recipe-type attachment. The workstation document
        // is intentionally absent from this join.
        for (WorkstationV2Def def : Workstations.v2All()) {
            Set<ResourceLocation> attached = new LinkedHashSet<>();
            for (ResourceLocation block : def.blocks()) attached.addAll(WorkstationRecipeTypes.forBlock(block));
            StationType role = def.schedulingRole(attached);
            for (ResourceLocation typeId : attached) {
                if (!seenTypes.add(typeId)) continue;
                discoverAttachedType(level, typeId, role, def, existingIds, out);
            }
        }

        for (WorkstationDef def : defs) {
            if (Workstations.v2ByBlockId(def.blocks().stream().findFirst().orElse(null)) != null) continue;
            // Fluid stations are NOT skipped. A mod can change its recipe model between versions
            // (Brewin' 3.x fermented into a fluid; its rewrite is plain item-in, item-out), and
            // the two discovery paths self-select: generic discovery reads an empty item result
            // for a fluid recipe and drops it, while the fluid reader finds no fluid fields on an
            // item recipe. Running both means one jar's model works without knowing which it is.
            // Craft surfaces belong to the station engine; feeding their family (all of
            // minecraft:crafting) into the cook's registry would offer the kitchen every
            // recipe in the game at a station the cook cannot operate.
            if (def.role() == StationType.CRAFT_SURFACE) continue;
            ResourceLocation typeId = def.recipeType();
            if (typeId == null || !seenTypes.add(typeId)) continue;
            //? if >=1.21 {
            for (RecipeHolder<?> holder : getRecipesForType(level, typeId)) {
                Recipe<?> recipe = holder.value();
                ResourceLocation recipeId = holder.id();
            //?} else {
            /*for (Recipe<?> recipe : getRecipesForType(level, typeId)) {
                ResourceLocation recipeId = recipe.getId();
            *///?}
                if (!existingIds.add(recipeId)) continue;
                ItemStack result = safeGetResult(level, recipe);
                if (result.isEmpty()) continue;
                ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
                if (outputId == null) continue;
                List<RecipeIngredient> inputs = extractIngredients(recipe);
                if (inputs.isEmpty()) continue;
                // A furnace burns something to run. Expressing that as an ordinary ingredient on
                // a supply-line id means planning, scoring, staging and the "can I even make
                // this?" check all account for fuel without knowing what fuel is.
                if (def.role() == StationType.FURNACE_STATION || def.needsFuel()) {
                    inputs = new ArrayList<>(inputs);
                    inputs.add(new RecipeIngredient(
                            List.of(com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL), 1));
                }
                // A custom recipe type may need a vessel (Farm & Charm's pot, Bakery's jar).
                // Without this the villager brings ingredients and never the container.
                ResourceLocation container = containerOf(recipe);
                int cookTime = safeCookTime(recipe, def.cookTimeTicks());
                int tier = def.recipeTier() > 0 ? def.recipeTier()
                        : autoTier(def.role(), inputs.size(), cookTime);
                out.add(new DiscoveredRecipe(
                        recipeId,
                        def.role(),
                        tier,
                        outputId,
                        Math.max(1, result.getCount()),
                        cookTime,
                        false,
                        container,
                        container == null ? 0 : 1,
                        inputs,
                        false,
                        def.beverage(),
                        //? if >=1.21 {
                        holder
                        //?} else {
                        /*recipe
                        *///?}
                ));
            }
        }
    }

    private static void discoverAttachedType(ServerLevel level, ResourceLocation typeId,
                                             StationType role, WorkstationV2Def def,
                                             Set<ResourceLocation> existingIds,
                                             List<DiscoveredRecipe> out) {
        //? if >=1.21 {
        for (RecipeHolder<?> holder : getRecipesForType(level, typeId)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation recipeId = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : getRecipesForType(level, typeId)) {
            ResourceLocation recipeId = recipe.getId();
        *///?}
            if (!existingIds.add(recipeId)) continue;
            ItemStack result = safeGetResult(level, recipe);
            if (result.isEmpty()) continue;
            ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (outputId == null) continue;
            outputId = def.correctedOutput(recipeId, outputId);
            if (!BuiltInRegistries.ITEM.containsKey(outputId)) continue;
            List<RecipeIngredient> inputs = extractIngredients(recipe);
            if (inputs.isEmpty()) continue;
            inputs = def.executableInputs(inputs);
            inputs = def.withSupplies(inputs);
            if (inputs.isEmpty()) continue;
            ResourceLocation container = containerOf(recipe);
            int cookTime = safeCookTime(recipe, 200);
            boolean tool = def.behaviorUses("tool");
            boolean beverage = outputId.getPath().contains("coffee") || outputId.getPath().contains("tea");
            out.add(new DiscoveredRecipe(recipeId, role,
                    autoTier(role, inputs.size(), cookTime), outputId,
                    Math.max(1, result.getCount()), cookTime, tool,
                    container, container == null ? 0 : Math.max(1, result.getCount()),
                    inputs, false, beverage,
                    //? if >=1.21 {
                    holder
                    //?} else {
                    /*recipe
                    *///?}
            ));
        }
    }

    /**
     * Protocol stations declare their production inline ({@code produces}): each line becomes
     * a synthetic recipe under the def's role, so recipe selection, ingredient staging, and
     * session bookkeeping treat "ferment cheese in the basin" exactly like any cooked dish.
     * The expansion itself is engine-owned, since the order catalogue reads the same lines.
     */
    private static void discoverProtocolRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        out.addAll(com.aetherianartificer.townstead.work.station.ProtocolRecipes.discoverAll());
    }

    /**
     * The {@code orderable} declaration of the workstation whose recipe type this recipe rides;
     * null for the built-in vanilla/FD families and synthetics, which stay plain tag-gated.
     */
    @Nullable
    public static WorkstationDef.Orderable orderableOf(DiscoveredRecipe recipe) {
        WorkstationDef def = defFor(recipe);
        return def == null ? null : def.orderable();
    }

    /**
     * Every workstation whose declared or block-attached recipe type this recipe rides.
     *
     * <p>There may be more than one: attachments are additive, so a datapack can teach another
     * block the same custom recipe family without replacing the original machine. Catalogue code
     * must retain that fact long enough to choose the owner that actually stands in the worksite.</p>
     */
    public static List<WorkstationDef> defsFor(DiscoveredRecipe recipe) {
        ResourceLocation typeId = recipeTypeId(recipe);
        if (typeId == null) return List.of();
        LinkedHashMap<ResourceLocation, WorkstationDef> found = new LinkedHashMap<>();

        // V2's recipe association belongs to the exact block, not to the workstation document.
        // Recover that owner for catalogue filtering/naming. Vanilla families remain role-owned:
        // campfire_cooking is legitimately shared by campfires, skillets and stoves, so choosing
        // one attached V2 block as its exclusive owner would hide recipes in the other two.
        if (!"minecraft".equals(typeId.getNamespace())) {
            for (WorkstationV2Def v2 : Workstations.v2All()) {
                for (ResourceLocation block : v2.blocks()) {
                    if (!WorkstationRecipeTypes.forBlock(block).contains(typeId)) continue;
                    WorkstationDef compatibility = Workstations.byId(v2.id());
                    if (compatibility != null) found.putIfAbsent(compatibility.id(), compatibility);
                }
            }
        }
        if (!found.isEmpty()) return List.copyOf(found.values());

        typeId = foreignRecipeTypeId(recipe);
        if (typeId == null) return List.of();
        for (WorkstationDef def : Workstations.all()) {
            if (typeId.equals(def.recipeType())) found.putIfAbsent(def.id(), def);
        }
        return List.copyOf(found.values());
    }

    /** First declaring workstation, retained for callers that do not have a physical worksite. */
    @Nullable
    public static WorkstationDef defFor(DiscoveredRecipe recipe) {
        List<WorkstationDef> defs = defsFor(recipe);
        return defs.isEmpty() ? null : defs.get(0);
    }

    /**
     * Recipe-type id when the recipe rides a workstation-declared type; null for the built-in
     * vanilla/FD families and synthetics. Drives the exclusive recipe/station pairing.
     */
    public static @Nullable ResourceLocation foreignRecipeTypeId(DiscoveredRecipe recipe) {
        ResourceLocation id = recipeTypeId(recipe);
        if (id == null) return null;
        if ("minecraft".equals(id.getNamespace())) return null;
        // V2 types are owned by block attachments and paired at the physical station. They are
        // not legacy "foreign" recipe families, irrespective of which mod supplied the id.
        for (WorkstationV2Def def : Workstations.v2All()) {
            for (ResourceLocation block : def.blocks()) {
                if (WorkstationRecipeTypes.forBlock(block).contains(id)) return null;
            }
        }
        return id;
    }

    /** Exact public recipe-type registry id, including vanilla and Farmer's Delight families. */
    public static @Nullable ResourceLocation recipeTypeId(DiscoveredRecipe recipe) {
        if (recipe == null || recipe.source() == null) return null;
        //? if >=1.21 {
        RecipeType<?> type = recipe.source().value().getType();
        //?} else {
        /*RecipeType<?> type = recipe.source().getType();
        *///?}
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(type);
        return id;
    }

    /**
     * Stations whose work happens as fluid inside the block. Each mod reads its own recipes, and
     * the join turns the pair into something the item-only planner can already handle.
     */
    private static void discoverFluidRecipes(ServerLevel level, List<DiscoveredRecipe> out) {
        for (WorkstationDef def : Workstations.all()) {
            if (!def.fluidStation()) continue;
            var source = com.aetherianartificer.townstead.work.recipe.FluidRecipeSources
                    .byName(def.fluidSource());
            // No reader registered means the mod is absent, so the station simply has no recipes.
            if (source == null) continue;
            int tier = def.recipeTier() > 0 ? def.recipeTier() : 3;
            out.addAll(source.discover(level, def.role(), tier));
        }
    }


    /**
     * An ItemStack field naming a vessel the recipe needs, read generically across mods.
     *
     * <p>A recipe may name a vessel it does not actually require: Farm &amp; Charm's goulash
     * declares a bowl beside {@code requireContainer: false}, and its pot only checks the
     * container slot when that flag is set. So a sibling boolean about the container is
     * believed — inventing the need sends a villager to fetch a bowl the work never consumes,
     * and reports "No bowl stored here" against a dish the kitchen can in fact make.</p>
     */
    private static @Nullable ResourceLocation containerOf(Recipe<?> recipe) {
        // Prefer an explicit public recipe contract when one exists. These are semantic names,
        // not mod checks; any recipe implementation may expose one of them.
        for (String methodName : List.of("getContainer", "getOutputContainer", "getRequiredContainer",
                "container", "carrier", "getCarrier")) {
            try {
                Method method = recipe.getClass().getMethod(methodName);
                Object value = method.invoke(recipe);
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && stack.getItem() != Items.AIR) return id;
                }
                if (value instanceof net.minecraft.world.item.crafting.Ingredient ingredient
                        && !ingredient.isEmpty()) {
                    ItemStack[] options = ingredient.getItems();
                    if (options.length > 0 && !options[0].isEmpty()) {
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(options[0].getItem());
                        if (id != null && options[0].getItem() != Items.AIR) return id;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable ignored) {
                return null;
            }
        }
        for (Class<?> c = recipe.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.getType() != ItemStack.class) continue;
                if (!f.getName().toLowerCase(Locale.ROOT).contains("container")) continue;
                if (!containerRequired(recipe, c)) return null;
                try {
                    f.setAccessible(true);
                    if (f.get(recipe) instanceof ItemStack stack && !stack.isEmpty()) {
                        return BuiltInRegistries.ITEM.getKey(stack.getItem());
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /**
     * Whether a declared vessel is actually required. A recipe that states no opinion is taken
     * at its word that the vessel it names is needed — which is how every family that has no
     * such flag already behaved.
     */
    private static boolean containerRequired(Recipe<?> recipe, Class<?> declaring) {
        for (java.lang.reflect.Field f : declaring.getDeclaredFields()) {
            if (f.getType() != boolean.class) continue;
            if (!f.getName().toLowerCase(Locale.ROOT).contains("container")) continue;
            try {
                f.setAccessible(true);
                return f.getBoolean(recipe);
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    // ── Auto-tiering ──

    static int autoTier(StationType stationType, int ingredientCount, int cookTimeTicks) {
        int base = switch (stationType) {
            // A smelted steak is the plainest cooking there is, same as one on a campfire.
            case FIRE_STATION, FURNACE_STATION -> 1;
            case CUTTING_BOARD, PASSIVE_STATION, PLACE_SURFACE, CRAFT_SURFACE -> 2;
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

    private static DiscoveredRecipe syntheticPurificationRecipe(ThirstCompatBridge bridge) {
        // The line wears the active thirst mod's own vessel — canteens under LSO, water
        // bottles under Thirst Was Taken/Reclaimed. The work still takes any impure
        // container the bridge recognises; this is what the order shows and counts.
        ResourceLocation output = bridge.purificationOutput();
        if (output == null || !BuiltInRegistries.ITEM.containsKey(output)) {
            output = MINECRAFT_POTION;
        }
        return new DiscoveredRecipe(
                //? if >=1.21 {
                ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "purification"),
                //?} else {
                /*new ResourceLocation(Townstead.MOD_ID, "purification"),
                *///?}
                StationType.FIRE_STATION,
                1,
                output,
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

    // ── Recipe type lookup ──

    /**
     * Looks up a {@link RecipeType} by its registry ID and returns all recipes
     * registered under that type. This works for any mod that registers recipes
     * under the FD recipe types (e.g. addon mods adding cooking pot recipes).
     */
    //? if >=1.21 {
    @SuppressWarnings("unchecked")
    public static List<RecipeHolder<?>> getRecipesForType(ServerLevel level, ResourceLocation typeId) {
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
    public static List<Recipe<?>> getRecipesForType(ServerLevel level, ResourceLocation typeId) {
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
        String[] methods = {"getCookingTime", "getCookTime", "getCookTimeInTicks", "time",
                "getTime", "cookTick", "getCookTick"};
        for (String name : methods) {
            try {
                Method m = recipe.getClass().getMethod(name);
                Object value = m.invoke(recipe);
                if (value instanceof Number n) return Math.max(1, n.intValue());
            } catch (Throwable ignored) {}
        }
        return fallback;
    }

    public static boolean recipeToolMatches(DiscoveredRecipe recipe, ItemStack stack) {
        if (recipe == null || !recipe.requiresTool() || stack == null || stack.isEmpty()) return false;
        boolean declared = false;
        for (WorkstationDef workstation : defsFor(recipe)) {
            WorkstationV2Def v2 = Workstations.v2ByBlockId(
                    workstation.blocks().stream().findFirst().orElse(null));
            if (v2 == null || !v2.hasDeclaredTool()) continue;
            declared = true;
            if (v2.toolMatches(stack)) return true;
        }
        if (declared) return false;
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
        return com.aetherianartificer.townstead.compat.farming.FarmerHarvestToolCompatRegistry.isCompatibleTool(stack);
    }

    public static List<RecipeIngredient> extractIngredients(Recipe<?> recipe) {
        List<RecipeIngredient> result = new ArrayList<>();
        for (net.minecraft.world.item.crafting.Ingredient mcIng : recipe.getIngredients()) {
            if (mcIng == null || mcIng.isEmpty()) continue;
            List<ResourceLocation> ids = itemIdsFromIngredient(mcIng);
            if (ids.isEmpty()) continue;
            int count = reflectIngredientCount(mcIng);
            result.add(new RecipeIngredient(ids, count));
        }
        if (!result.isEmpty()) return result;

        // Single-input recipe implementations are allowed to expose their ingredient directly
        // while leaving Recipe#getIngredients empty. That is still a public recipe contract.
        for (String methodName : List.of("ingredient", "getIngredient")) {
            try {
                Method method = recipe.getClass().getMethod(methodName);
                Object value = method.invoke(recipe);
                if (!(value instanceof net.minecraft.world.item.crafting.Ingredient ingredient)
                        || ingredient.isEmpty()) continue;
                List<ResourceLocation> ids = itemIdsFromIngredient(ingredient);
                if (ids.isEmpty()) continue;
                int count = semanticIngredientCount(recipe, reflectIngredientCount(ingredient));
                result.add(new RecipeIngredient(ids, count));
                break;
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private static int semanticIngredientCount(Recipe<?> recipe, int fallback) {
        for (String name : List.of("ingredientCount", "getIngredientCount")) {
            try {
                Method method = recipe.getClass().getMethod(name);
                Object value = method.invoke(recipe);
                if (value instanceof Number number && number.intValue() > 0) return number.intValue();
            } catch (Throwable ignored) {}
        }
        return Math.max(1, fallback);
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

    /**
     * Drops the discovery cache once a datapack reload has finished. Registered through
     * {@code AddReloadListenerEvent}, so its apply phase runs after the recipe manager's — the
     * next cook to ask rediscovers against the new recipes rather than the old ones.
     */
    public static final class ReloadHook
            extends net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void> {

        @Override
        protected Void prepare(net.minecraft.server.packs.resources.ResourceManager resourceManager,
                               net.minecraft.util.profiling.ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void prepared,
                             net.minecraft.server.packs.resources.ResourceManager resourceManager,
                             net.minecraft.util.profiling.ProfilerFiller profiler) {
            invalidate();
        }
    }
}
