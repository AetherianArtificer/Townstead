package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.recipe.WorkRecipeRegistry;

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
        if (def.role() != StationType.PASSIVE_STATION && def.role() != StationType.PLACE_SURFACE
                && def.role() != StationType.CRAFT_SURFACE) {
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
                ResourceLocation sourceTag = null;
                if (raw.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
                    if (tagId != null) {
                        sourceTag = tagId;
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
                inputs.add(new RecipeIngredient(List.copyOf(ids), 1, sourceTag));
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
     * Every recipe one station definition exposes to both execution and Order Sheets.
     * Data-authored lines, ordinary recipe types, attached V2 families, and station-native
     * registries all converge here so adding a profession never needs its own catalogue.
     */
    public static List<DiscoveredRecipe> discover(ServerLevel level, WorkstationDef def) {
        List<DiscoveredRecipe> out = new ArrayList<>();
        if (BrewingStandStationAdapter.NAME.equals(def.adapter())) {
            out.addAll(com.aetherianartificer.townstead.work.recipe.PotionBrewingRecipes
                    .discover(level));
        } else {
            out.addAll(discoverFor(def));
        }
        out.addAll(discoverByType(level, def));

        Set<ResourceLocation> attached = new LinkedHashSet<>();
        for (ResourceLocation block : def.blocks()) {
            if (Workstations.v2ByBlockId(block) != null) {
                attached.addAll(WorkstationRecipeTypes.forBlock(block));
            }
        }
        if (!attached.isEmpty()) {
            for (DiscoveredRecipe recipe : WorkRecipeRegistry.getRecipes(level)) {
                ResourceLocation type = WorkRecipeRegistry.recipeTypeId(recipe);
                if (type != null && attached.contains(type)) out.add(recipe);
            }
        }
        return List.copyOf(out);
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
        long gameTime = level.getGameTime();
        ByTypeCacheEntry cached = BY_TYPE_CACHE.get(def.id());
        if (cached != null && gameTime < cached.expiresAt()) return cached.recipes();
        List<DiscoveredRecipe> out = new ArrayList<>();
        List<ItemStack> probes = null;
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
            ResourceLocation typeId = WorkRecipeRegistry.recipeTypeId(recipe.getType());
            if (!def.recipeType().equals(typeId)) continue;
            // Trim recipes compose their result from base and template at craft time; there is
            // no honest static line for "any armor, any pattern".
            if (recipe instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe) continue;
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) continue;
            List<RecipeIngredient> inputs;
            if (recipe instanceof net.minecraft.world.item.crafting.SmithingRecipe smithing) {
                // Smithing recipes do not enumerate ingredients through getIngredients(), but
                // they answer membership questions. Probing the registry through those public
                // tests rebuilds the exact template/base/addition sets without reflection —
                // which is what keeps this correct across both ports' mappings.
                if (probes == null) probes = buildProbes();
                inputs = probeSmithing(smithing, probes);
            } else {
                inputs = new ArrayList<>();
                for (net.minecraft.world.item.crafting.Ingredient ingredient : recipe.getIngredients()) {
                    Set<ResourceLocation> ids = new LinkedHashSet<>();
                    for (ItemStack option : ingredient.getItems()) {
                        if (option.isEmpty()) continue;
                        ids.add(BuiltInRegistries.ITEM.getKey(option.getItem()));
                    }
                    if (!ids.isEmpty()) inputs.add(new RecipeIngredient(List.copyOf(ids), 1));
                }
                // Single-input recipes (stonecutting) may also decline to enumerate; their own
                // matches() answers instead. Self-selecting: never runs when getIngredients works.
                if (inputs.isEmpty()
                        && recipe instanceof net.minecraft.world.item.crafting.SingleItemRecipe single) {
                    if (probes == null) probes = buildProbes();
                    inputs = probeSingleItem(level, single, probes);
                }
            }
            // A recipe whose ingredients cannot be resolved must yield nothing rather than a
            // zero-input line — a recipe that costs nothing is an item printer.
            if (inputs.isEmpty()) continue;
            // A furnace burns something to run. Expressed as an ordinary ingredient on the
            // supply-line id so planning, staging and the needs list all account for fuel
            // without knowing what fuel is. Only furnaces: a passive block with a recipe
            // family processes on its own and must not be handed an input it cannot take.
            if (def.role() == StationType.FURNACE_STATION) {
                inputs.add(new RecipeIngredient(List.of(
                        com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL), 1));
            }
            // Processors publish their own duration. Crafting has no recipe clock: it is one
            // immediate transaction, with villager animation pacing kept in the work task.
            int processingTime = def.role() == StationType.CRAFT_SURFACE
                    ? 1
                    : WorkRecipeRegistry.cookingTimeTicks(recipe, def.cookTimeTicks());
            out.add(new DiscoveredRecipe(
                    recipeId,
                    def.role(),
                    def.recipeTier() > 0 ? def.recipeTier() : 1,
                    BuiltInRegistries.ITEM.getKey(result.getItem()),
                    Math.max(1, result.getCount()),
                    processingTime,
                    false,
                    null,
                    0,
                    List.copyOf(inputs),
                    false,
                    def.beverage(),
                    null
            ));
        }
        List<DiscoveredRecipe> frozen = List.copyOf(out);
        BY_TYPE_CACHE.put(def.id(), new ByTypeCacheEntry(frozen, gameTime + BY_TYPE_CACHE_TICKS));
        return frozen;
    }

    // The crafting family alone is thousands of recipes, and both the catalogue and the station
    // engine ask for the same expansion. Recipe managers change only on datapack reload, so a
    // short TTL keeps the walk rare, and the workstation loader clears it on every reload.
    private static final long BY_TYPE_CACHE_TICKS = 600L;
    private record ByTypeCacheEntry(List<DiscoveredRecipe> recipes, long expiresAt) {}
    private static final java.util.concurrent.ConcurrentHashMap<ResourceLocation, ByTypeCacheEntry>
            BY_TYPE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Datapack reload changed the recipe manager; every cached expansion is stale. */
    public static void invalidate() {
        BY_TYPE_CACHE.clear();
    }

    /** One probe stack per registered item, built once per discovery pass and reused. */
    private static List<ItemStack> buildProbes() {
        List<ItemStack> out = new ArrayList<>();
        for (net.minecraft.world.item.Item item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            out.add(item.getDefaultInstance());
        }
        return out;
    }

    /** A single-input recipe's ingredient set, rebuilt through its own matches(). */
    private static List<RecipeIngredient> probeSingleItem(
            ServerLevel level, net.minecraft.world.item.crafting.SingleItemRecipe recipe,
            List<ItemStack> probes) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (ItemStack probe : probes) {
            //? if >=1.21 {
            boolean matches = recipe.matches(
                    new net.minecraft.world.item.crafting.SingleRecipeInput(probe), level);
            //?} else {
            /*net.minecraft.world.SimpleContainer holder = new net.minecraft.world.SimpleContainer(1);
            holder.setItem(0, probe);
            boolean matches = recipe.matches(holder, level);
            *///?}
            if (matches) ids.add(BuiltInRegistries.ITEM.getKey(probe.getItem()));
        }
        return ids.isEmpty() ? List.of() : List.of(new RecipeIngredient(List.copyOf(ids), 1));
    }

    /** The template/base/addition sets, rebuilt through the recipe's own membership tests. */
    private static List<RecipeIngredient> probeSmithing(
            net.minecraft.world.item.crafting.SmithingRecipe smithing, List<ItemStack> probes) {
        List<ResourceLocation> template = new ArrayList<>();
        List<ResourceLocation> base = new ArrayList<>();
        List<ResourceLocation> addition = new ArrayList<>();
        for (ItemStack probe : probes) {
            if (smithing.isTemplateIngredient(probe)) {
                template.add(BuiltInRegistries.ITEM.getKey(probe.getItem()));
            }
            if (smithing.isBaseIngredient(probe)) {
                base.add(BuiltInRegistries.ITEM.getKey(probe.getItem()));
            }
            if (smithing.isAdditionIngredient(probe)) {
                addition.add(BuiltInRegistries.ITEM.getKey(probe.getItem()));
            }
        }
        List<RecipeIngredient> out = new ArrayList<>(3);
        if (!template.isEmpty()) out.add(new RecipeIngredient(List.copyOf(template), 1));
        if (!base.isEmpty()) out.add(new RecipeIngredient(List.copyOf(base), 1));
        if (!addition.isEmpty()) out.add(new RecipeIngredient(List.copyOf(addition), 1));
        // A recipe that answered no to everything stays empty, and the caller's zero-input
        // guard drops it.
        return out;
    }
}
