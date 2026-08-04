package com.aetherianartificer.townstead.compat.caupona;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.FluidAmount;
import com.aetherianartificer.townstead.work.recipe.FluidCarriers;
import com.aetherianartificer.townstead.work.recipe.FluidRecipeSources;
import com.aetherianartificer.townstead.work.recipe.FluidRecipes;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads Caupona's stew pot into recipes a villager can work.
 *
 * <p>Caupona does not have recipes in the usual sense. A cooking recipe carries no ingredient
 * list: it carries predicates, and whichever recipe's predicates a potful of items satisfies
 * first — highest priority wins — decides which soup comes out. Bowls are a separate recipe type
 * again, and the soup in between is a fluid nobody can carry.</p>
 *
 * <p>What makes this tractable is that the predicates are introspectable. A condition can list the
 * item stacks it accepts, so an item set that satisfies a recipe is <em>read</em> rather than
 * solved for: take the items every one of its allow-conditions accepts, drop anything its
 * deny-conditions name, and one item repeated fills the pot. Base fluids are found the same way,
 * by probing the base conditions with the fluids a villager can actually fetch.</p>
 *
 * <p>Two deliberate omissions. A recipe with no allow-conditions is skipped: it is a catch-all
 * fallback, and claiming it would tell the planner that any item at all makes that soup. A recipe
 * whose conditions share no common item is skipped too, rather than guessed at — the pot would
 * still make <em>a</em> soup, just not the one that was promised.</p>
 */
public final class CauponaFluidRecipes {

    public static final String SOURCE = "townstead:caupona";

    /** One bowl's worth, the unit the pot fills and drains in. */
    static final int SERVING_MB = 250;
    /** What one bucket of base fills the pot with, and so what one work cycle yields. */
    static final int BATCH_MB = FluidAmount.BUCKET;
    /** The pot holds nine ingredients, one per slot. */
    static final int MAX_INGREDIENTS = 9;

    //? if >=1.21 {
    private static final ResourceLocation COOKING = ResourceLocation.parse("caupona:cooking");
    private static final ResourceLocation BOILING = ResourceLocation.parse("caupona:boiling");
    private static final ResourceLocation BOWL = ResourceLocation.parse("caupona:bowl");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    //?} else {
    /*private static final ResourceLocation COOKING = new ResourceLocation("caupona", "cooking");
    private static final ResourceLocation BOILING = new ResourceLocation("caupona", "boiling");
    private static final ResourceLocation BOWL = new ResourceLocation("caupona", "bowl");
    private static final ResourceLocation WATER = new ResourceLocation("minecraft", "water");
    *///?}

    private CauponaFluidRecipes() {}

    public static void bootstrap() {
        if (!ModCompat.isLoaded("caupona")) return;
        FluidRecipeSources.register(SOURCE, CauponaFluidRecipes::discover);
    }

    public static List<DiscoveredRecipe> discover(ServerLevel level, StationType stationType, int tier) {
        List<FluidRecipes.Pour> pours = pours(level);
        if (pours.isEmpty()) return List.of();
        List<Brew> brews = resolveByPriority(candidates(level));
        if (brews.isEmpty()) return List.of();

        List<FluidRecipes.Brew> joined = new ArrayList<>(brews.size());
        for (Brew brew : brews) {
            joined.add(new FluidRecipes.Brew(
                    brew.id(),
                    List.of(new RecipeIngredient(List.copyOf(brew.items()), brew.count())),
                    FluidAmount.of(brew.baseFluid(), BATCH_MB),
                    new FluidAmount(brew.outputFluid(), BATCH_MB),
                    brew.timeTicks()));
        }
        return FluidRecipes.join(joined, pours, stationType, tier);
    }

    // ── Cooking ──

    /**
     * A cooking recipe read into the items and base that would produce it, before priority has
     * decided which recipe actually gets each item.
     */
    record Brew(ResourceLocation id, int priority, ResourceLocation baseFluid,
                ResourceLocation outputFluid, Set<ResourceLocation> items, int count, int timeTicks) {}

    private static List<Brew> candidates(ServerLevel level) {
        Boiling boiling = Boiling.read(level);
        List<Brew> out = new ArrayList<>();
        //? if >=1.21 {
        for (var holder : WorkRecipeRegistry.getRecipesForType(level, COOKING)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : WorkRecipeRegistry.getRecipesForType(level, COOKING)) {
            ResourceLocation id = recipe.getId();
        *///?}
            Brew brew = candidate(id, recipe, boiling);
            if (brew != null) out.add(brew);
        }
        return out;
    }

    @Nullable
    private static Brew candidate(ResourceLocation id, Recipe<?> recipe, Boiling boiling) {
        ResourceLocation output = CauponaRecipeAccess.readFluidId(recipe, "getOutput", "output");
        if (output == null) return null;

        // A recipe that allows anything is a fallback, not something to plan a shift around.
        List<?> allow = CauponaRecipeAccess.readList(recipe, "getAllow", "allow");
        if (allow.isEmpty()) return null;

        Set<ResourceLocation> items = null;
        for (Object condition : allow) {
            Set<ResourceLocation> accepted = CauponaRecipeAccess.acceptedItems(condition);
            if (accepted.isEmpty()) return null;
            if (items == null) {
                items = new LinkedHashSet<>(accepted);
            } else {
                items.retainAll(accepted);
            }
            if (items.isEmpty()) return null;
        }
        if (items == null || items.isEmpty()) return null;

        for (Object condition : CauponaRecipeAccess.readList(recipe, "getDeny", "deny")) {
            items.removeAll(CauponaRecipeAccess.acceptedItems(condition));
        }
        if (items.isEmpty()) return null;

        Base base = boiling.reachableBase(CauponaRecipeAccess.readList(recipe, "getBase", "base"));
        if (base == null) return null;

        // Density is measured per serving, so a full pot needs that much in every part of it.
        float density = CauponaRecipeAccess.readFloat(recipe, "getDensity", "density", 1f);
        int parts = BATCH_MB / SERVING_MB;
        int count = Math.min(MAX_INGREDIENTS, Math.max(1, (int) Math.ceil(density * parts)));

        int cookTime = CauponaRecipeAccess.readInt(recipe, "getTime", "time", 200);
        int priority = CauponaRecipeAccess.readInt(recipe, "getPriority", "priority", 0);
        return new Brew(id, priority, base.carried(), output, items,
                count, Math.max(1, (base.boilTicks() + cookTime) * parts));
    }

    /**
     * Gives each item to the highest-priority recipe that would claim it, which is the same rule
     * the pot itself applies. Without this two soups could both promise the same potful and only
     * one of them would ever appear.
     */
    static List<Brew> resolveByPriority(List<Brew> candidates) {
        List<Brew> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt(Brew::priority).reversed()
                .thenComparing(brew -> brew.id().toString()));

        Map<String, ResourceLocation> claimed = new HashMap<>();
        Map<ResourceLocation, Set<ResourceLocation>> won = new HashMap<>();
        for (Brew brew : ordered) {
            for (ResourceLocation item : brew.items()) {
                String key = brew.baseFluid() + "|" + item;
                if (claimed.putIfAbsent(key, brew.id()) == null) {
                    won.computeIfAbsent(brew.id(), k -> new LinkedHashSet<>()).add(item);
                }
            }
        }

        List<Brew> out = new ArrayList<>();
        for (Brew brew : ordered) {
            Set<ResourceLocation> items = won.get(brew.id());
            // Everything this recipe accepts belongs to a stronger one; it can never be cooked.
            if (items == null || items.isEmpty()) continue;
            out.add(new Brew(brew.id(), brew.priority(), brew.baseFluid(), brew.outputFluid(),
                    items, brew.count(), brew.timeTicks()));
        }
        return out;
    }

    // ── Bowls ──

    private static List<FluidRecipes.Pour> pours(ServerLevel level) {
        List<FluidRecipes.Pour> out = new ArrayList<>();
        //? if >=1.21 {
        for (var holder : WorkRecipeRegistry.getRecipesForType(level, BOWL)) {
            Recipe<?> recipe = holder.value();
            ResourceLocation id = holder.id();
        //?} else {
        /*for (Recipe<?> recipe : WorkRecipeRegistry.getRecipesForType(level, BOWL)) {
            ResourceLocation id = recipe.getId();
        *///?}
            ResourceLocation filled = CauponaRecipeAccess.readItemId(recipe, "getBowl", "bowl");
            Set<ResourceLocation> empties = CauponaRecipeAccess.ingredientItems(
                    CauponaRecipeAccess.read(recipe, "getInBowl", "inBowl"));
            if (filled == null || empties.isEmpty()) continue;
            ResourceLocation empty = empties.iterator().next();

            // One bowl recipe covers every fluid its ingredient matches, so it becomes one pour
            // per fluid — the join only ever looks a fluid up by id.
            for (ResourceLocation fluid : CauponaRecipeAccess.fluidIds(
                    CauponaRecipeAccess.read(recipe, "getFluid", "fluid"))) {
                out.add(new FluidRecipes.Pour(id, fluid, SERVING_MB, filled, empty));
            }
        }
        return out;
    }

    // ── Base fluids ──

    /** A base a villager can actually deliver, and what it costs the pot to boil it into shape. */
    private record Base(ResourceLocation carried, int boilTicks) {}

    /**
     * Caupona's boiling recipes, which are what let a bucket of water become a soup base. The pot
     * boils on its own, so a base is workable as long as some carryable fluid reaches it.
     */
    private record Boiling(Map<ResourceLocation, ResourceLocation> next, Map<ResourceLocation, Integer> times) {

        static Boiling read(ServerLevel level) {
            Map<ResourceLocation, ResourceLocation> next = new HashMap<>();
            Map<ResourceLocation, Integer> times = new HashMap<>();
            //? if >=1.21 {
            for (var holder : WorkRecipeRegistry.getRecipesForType(level, BOILING)) {
                Recipe<?> recipe = holder.value();
            //?} else {
            /*for (Recipe<?> recipe : WorkRecipeRegistry.getRecipesForType(level, BOILING)) {
            *///?}
                ResourceLocation after = CauponaRecipeAccess.readFluidId(recipe, "getAfter", "after");
                if (after == null) continue;
                int time = CauponaRecipeAccess.readInt(recipe, "getTime", "time", 200);
                for (ResourceLocation before : CauponaRecipeAccess.fluidIds(
                        CauponaRecipeAccess.read(recipe, "getBefore", "before"))) {
                    next.putIfAbsent(before, after);
                    times.putIfAbsent(before, time);
                }
            }
            return new Boiling(next, times);
        }

        /**
         * The carryable fluid whose boiling chain satisfies these base conditions. Water is tried
         * first so the common case stays the obvious errand.
         */
        @Nullable
        Base reachableBase(List<?> conditions) {
            for (ResourceLocation carried : carriedFluidsWaterFirst()) {
                if (conditions.isEmpty()) return new Base(carried, 0);
                ResourceLocation current = carried;
                int ticks = 0;
                Set<ResourceLocation> seen = new LinkedHashSet<>();
                while (current != null && seen.add(current)) {
                    if (CauponaRecipeAccess.baseAccepts(conditions, BuiltInRegistries.FLUID.get(current))) {
                        return new Base(carried, ticks);
                    }
                    Integer time = times.get(current);
                    ticks += time == null ? 0 : time;
                    current = next.get(current);
                }
            }
            return null;
        }

        private static List<ResourceLocation> carriedFluidsWaterFirst() {
            List<ResourceLocation> fluids = new ArrayList<>(FluidCarriers.carriedFluids());
            fluids.sort(Comparator.comparing((ResourceLocation id) -> !WATER.equals(id))
                    .thenComparing(ResourceLocation::toString));
            return fluids;
        }
    }
}
