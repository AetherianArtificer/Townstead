package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.work.station.Workstations;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One villager AI work behavior a profession declares in data. Townstead code owns the task
 * engines (the state machines that path, claim stations, gather, and produce); the profession
 * file owns which behaviors its workers run, at which workstation blocks, against which entity
 * targets, producing which recipes, in what preference order, and behind what gate.
 * {@code workstations}, {@code entities}, {@code recipes}, {@code deny_recipes},
 * {@code recipe_inputs}, and {@code deny_recipe_inputs} entries are
 * ids or {@code #tag} references; an empty allow set means the task type's full default set.
 * Declared sets can only narrow an engine's targets, never widen past its own safety rules
 * (e.g. the slaughter never-kill list is absolute).
 */
public record WorkTaskDef(
        ResourceLocation type,
        TargetSet workstations,
        TargetSet entities,
        TargetSet recipes,
        TargetSet recipesDenied,
        TargetSet recipeInputs,
        TargetSet recipeInputsDenied,
        int weight,
        Scope scope,
        Condition requirements,
        @Nullable OrderOption order) {

    /** Presentation for a task that players may place on a worksite order sheet. */
    public record OrderOption(String name, ResourceLocation icon) {}

    public WorkTaskDef(ResourceLocation type, TargetSet workstations, TargetSet entities,
                       TargetSet recipes, TargetSet recipesDenied, int weight, Scope scope,
                       Condition requirements) {
        this(type, workstations, entities, recipes, recipesDenied,
                TargetSet.EMPTY, TargetSet.EMPTY, weight, scope, requirements, null);
    }

    public WorkTaskDef(ResourceLocation type, TargetSet workstations, TargetSet entities,
                       TargetSet recipes, TargetSet recipesDenied, TargetSet recipeInputs,
                       TargetSet recipeInputsDenied, int weight, Scope scope,
                       Condition requirements) {
        this(type, workstations, entities, recipes, recipesDenied, recipeInputs,
                recipeInputsDenied, weight, scope, requirements, null);
    }

    /**
     * How far from the assigned work site a task may look for its stations. {@code workstations}
     * says which blocks; this says where.
     *
     * <p>Defaults to {@link #WORKSITE}, which is how every task behaved before this existed: the
     * villager works only what stands inside the building it was assigned. The wider values are
     * for stations a village keeps in common rather than in a room — a furnace on the square, a
     * shared oven — and they cost a wider walkable-interior scan, so they are opt-in per task.</p>
     */
    public enum Scope {
        /** Only the assigned work site. The default, and the cheapest. */
        WORKSITE,
        /** The work site plus its immediate surroundings, for a station just outside the wall. */
        NEARBY,
        /** Any recognized building in the village. */
        VILLAGE;

        public static @Nullable Scope parse(String raw) {
            return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
                case "worksite" -> WORKSITE;
                case "nearby" -> NEARBY;
                case "village" -> VILLAGE;
                default -> null;
            };
        }

        /** The wider of two scopes, for merging a bucket of tasks into one search. */
        public Scope widest(Scope other) {
            return ordinal() >= other.ordinal() ? this : other;
        }
    }

    /**
     * An id/#tag set gating one target axis. Empty allow sets admit everything; empty deny sets
     * deny nothing.
     *
     * <p>The bare token {@code "edible"} additionally admits anything whose output is food. It
     * exists because the useful line for a cook at a furnace is "food, and these few exceptions",
     * and the food half of that cannot be written as a tag without every mod maintaining one.</p>
     */
    public record TargetSet(Set<ResourceLocation> ids, List<ResourceLocation> tags, boolean edible,
                            Set<String> kinds) {
        public static final TargetSet EMPTY = new TargetSet(Set.of(), List.of(), false, Set.of());

        /** The literal accepted in place of an id to mean "any food output". */
        public static final String EDIBLE_TOKEN = "edible";

        /**
         * Literals that classify by what the output item IS, so a modded sword counts as a
         * weapon without anyone tagging it: mods extend the vanilla item classes because that
         * is where attack damage and durability come from. Tags remain the override seam for
         * the rare item that extends plain Item.
         */
        public static final Set<String> KIND_TOKENS =
                Set.of("weapon", "armor", "tool", "block", "ranged", "navigation", "book");

        public boolean isEmpty() {
            return ids.isEmpty() && tags.isEmpty() && !edible && kinds.isEmpty();
        }
    }

    // ── Workstations ──

    public boolean anyWorkstation() {
        return workstations.isEmpty();
    }

    /** Whether the declared workstation set admits the given station block. */
    public boolean allowsBlock(@Nullable ResourceLocation blockId) {
        if (workstations.isEmpty()) return true;
        if (blockId == null) return false;
        if (workstations.ids().contains(blockId)) return true;
        if (workstations.tags().isEmpty()) return false;
        // Tag keys are built here, not at parse time, so loading defs never touches the
        // registry bootstrap (which unit tests don't have).
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        for (ResourceLocation tagId : workstations.tags()) {
            if (block.defaultBlockState().is(TagKey.create(Registries.BLOCK, tagId))) return true;
        }
        return false;
    }

    // ── Entities ──

    public boolean anyEntity() {
        return entities.isEmpty();
    }

    /** Whether the declared entity set admits the given target type. */
    public boolean allowsEntity(net.minecraft.world.entity.EntityType<?> type) {
        if (entities.isEmpty()) return true;
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entities.ids().contains(key)) return true;
        for (ResourceLocation tagId : entities.tags()) {
            if (type.is(TagKey.create(Registries.ENTITY_TYPE, tagId))) return true;
        }
        return false;
    }

    /** Id-only form of {@link #allowsEntity} (tags need a live registry; tests use this). */
    public boolean allowsEntityId(@Nullable ResourceLocation key) {
        return entities.isEmpty() || (key != null && entities.ids().contains(key));
    }

    // ── Recipes ──

    /**
     * Whether this task may produce the given recipe. Ids match the recipe id or the output
     * item id; {@code #tags} match the output item's tags. Deny entries win over allows; an
     * empty allow set admits every recipe the engine offers.
     */
    public boolean allowsRecipe(@Nullable ResourceLocation recipeId, @Nullable ResourceLocation outputId) {
        return allowsRecipe(recipeId, outputId, List.of());
    }

    /**
     * Full discovered-recipe gate. Output filters classify what a task makes; input filters
     * classify what raw material the task owns. The latter is important for shared recipe
     * engines such as furnaces and smokers, where station capability alone must not make every
     * smeltable item part of every profession.
     */
    public boolean allowsRecipe(@Nullable ResourceLocation recipeId,
                                @Nullable ResourceLocation outputId,
                                List<RecipeIngredient> inputs) {
        if (matchesRecipe(recipesDenied, recipeId, outputId)) return false;
        if (!recipes.isEmpty() && !matchesRecipe(recipes, recipeId, outputId)) return false;
        if (matchesAnyInput(recipeInputsDenied, inputs)) return false;
        return recipeInputs.isEmpty() || matchesAnyInput(recipeInputs, inputs);
    }

    private static boolean matchesAnyInput(TargetSet set, List<RecipeIngredient> inputs) {
        if (set.isEmpty() || inputs == null || inputs.isEmpty()) return false;
        for (RecipeIngredient ingredient : inputs) {
            for (ResourceLocation itemId : ingredient.itemIds()) {
                if (matchesItem(set, itemId)) return true;
            }
        }
        return false;
    }

    private static boolean matchesRecipe(TargetSet set, @Nullable ResourceLocation recipeId,
                                         @Nullable ResourceLocation outputId) {
        if (set.isEmpty()) return false;
        if (recipeId != null && set.ids().contains(recipeId)) return true;
        if (outputId == null) return false;
        if (set.ids().contains(outputId)) return true;
        return matchesItem(set, outputId);
    }

    private static boolean matchesItem(TargetSet set, @Nullable ResourceLocation outputId) {
        if (outputId == null) return false;
        if (set.ids().contains(outputId)) return true;
        if (set.tags().isEmpty() && !set.edible() && set.kinds().isEmpty()) return false;
        var stack = BuiltInRegistries.ITEM.get(outputId).getDefaultInstance();
        if (set.edible() && isEdible(stack)) return true;
        for (String kind : set.kinds()) {
            if (matchesKind(kind, stack.getItem())) return true;
        }
        for (ResourceLocation tagId : set.tags()) {
            if (stack.is(TagKey.create(Registries.ITEM, tagId))) return true;
        }
        return false;
    }

    /** Class-based classification: what the item is, not what somebody remembered to tag. */
    private static boolean matchesKind(String kind, net.minecraft.world.item.Item item) {
        return switch (kind) {
            case "weapon" -> item instanceof net.minecraft.world.item.SwordItem
                    || item instanceof net.minecraft.world.item.AxeItem
                    || item instanceof net.minecraft.world.item.TridentItem
                    //? if >=1.21 {
                    || item instanceof net.minecraft.world.item.MaceItem
                    //?}
                    ;
            case "armor" -> item instanceof net.minecraft.world.item.ArmorItem
                    || item instanceof net.minecraft.world.item.ShieldItem;
            case "tool" -> item instanceof net.minecraft.world.item.DiggerItem
                    || item instanceof net.minecraft.world.item.ShearsItem
                    || item instanceof net.minecraft.world.item.FlintAndSteelItem
                    || item instanceof net.minecraft.world.item.BrushItem;
            // Placeable output: the mason's classification. Stone, glass, terracotta and every
            // modded brick are BlockItems; ingots, food and gear never are.
            case "block" -> item instanceof net.minecraft.world.item.BlockItem;
            // The fletcher's classification: everything that launches or is launched. Modded
            // bows extend ProjectileWeaponItem for draw/charge handling, arrows extend
            // ArrowItem for flight.
            case "ranged" -> item instanceof net.minecraft.world.item.ProjectileWeaponItem
                    || item instanceof net.minecraft.world.item.ArrowItem;
            // The cartographer's classification: things that show the way. Frames, patterns
            // and paper have no class of their own and ride the trade's tag instead.
            case "navigation" -> item instanceof net.minecraft.world.item.EmptyMapItem
                    || item instanceof net.minecraft.world.item.MapItem
                    || item instanceof net.minecraft.world.item.CompassItem
                    || item instanceof net.minecraft.world.item.SpyglassItem;
            // The librarian's classification. Shelves are BlockItems and ride the trade's tag
            // instead — "block" here would hand the librarian the mason's whole catalogue.
            case "book" -> item instanceof net.minecraft.world.item.BookItem
                    || item instanceof net.minecraft.world.item.WritableBookItem
                    || item instanceof net.minecraft.world.item.WrittenBookItem
                    || item instanceof net.minecraft.world.item.EnchantedBookItem;
            default -> false;
        };
    }

    private static boolean isEdible(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        //? if >=1.21 {
        return stack.get(net.minecraft.core.component.DataComponents.FOOD) != null;
        //?} else {
        /*return stack.isEdible();
        *///?}
    }

    // ── Gate ──

    public boolean available(LivingEntity entity) {
        return requirements.test(new ConditionContext(entity));
    }

    // ── Parsing ──

    /**
     * Null on any malformed field; the loader turns that into a diagnostic. A broken
     * {@code requirements} drops the whole entry: a broken gate must never read as always-on.
     */
    static @Nullable WorkTaskDef parse(JsonObject obj) {
        String rawType = GsonHelper.getAsString(obj, "type", "");
        if (rawType.isBlank()) return null;
        ResourceLocation type = rawType.contains(":")
                ? ResourceLocation.tryParse(rawType)
                : ResourceLocation.tryParse(WorkTaskTypes.NAMESPACE + ":" + rawType);
        if (type == null) return null;

        TargetSet workstations = readIdSet(obj, "workstations");
        TargetSet entities = readIdSet(obj, "entities");
        TargetSet recipes = readIdSet(obj, "recipes");
        TargetSet denied = readIdSet(obj, "deny_recipes");
        TargetSet recipeInputs = readIdSet(obj, "recipe_inputs");
        TargetSet deniedInputs = readIdSet(obj, "deny_recipe_inputs");
        if (workstations == null || entities == null || recipes == null || denied == null
                || recipeInputs == null || deniedInputs == null) return null;

        Scope scope = Scope.WORKSITE;
        if (obj.has("scope")) {
            scope = Scope.parse(GsonHelper.getAsString(obj, "scope", ""));
            // An unreadable scope must not silently widen or narrow where a villager works.
            if (scope == null) return null;
        }

        Condition requirements = Conditions.ALWAYS;
        if (obj.has("requirements")) {
            requirements = Conditions.parse(obj.get("requirements"));
            if (requirements == null) return null;
        }
        // Completed-work history belongs to the executable Job or task engine. Keeping an
        // override here would let a profession rename the same activity depending on who did it.
        if (obj.has("history_counter")) return null;
        OrderOption order = null;
        if (obj.has("order")) {
            order = parseOrder(obj.get("order"));
            if (order == null) return null;
        }
        return new WorkTaskDef(type, workstations, entities, recipes, denied,
                recipeInputs, deniedInputs,
                GsonHelper.getAsInt(obj, "weight", 1), scope, requirements, order);
    }

    private static @Nullable OrderOption parseOrder(JsonElement element) {
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        String name = GsonHelper.getAsString(json, "name", "").trim();
        String rawIcon = GsonHelper.getAsString(json, "icon", "").trim();
        ResourceLocation icon = rawIcon.isEmpty() ? null : ResourceLocation.tryParse(rawIcon);
        return name.isEmpty() || icon == null ? null : new OrderOption(name, icon);
    }

    /** Reads an id/#tag string array into a {@link TargetSet}; null on any malformed entry. */
    private static @Nullable TargetSet readIdSet(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return TargetSet.EMPTY;
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        List<ResourceLocation> tags = new ArrayList<>();
        boolean edible = false;
        Set<String> kinds = new LinkedHashSet<>();
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonPrimitive()) return null;
            String raw = e.getAsString();
            if (TargetSet.EDIBLE_TOKEN.equals(raw)) {
                edible = true;
            } else if (TargetSet.KIND_TOKENS.contains(raw)) {
                kinds.add(raw);
            } else if (raw.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
                if (tagId == null) return null;
                tags.add(tagId);
            } else {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) return null;
                ids.add(id);
            }
        }
        return new TargetSet(Set.copyOf(ids), List.copyOf(tags), edible, Set.copyOf(kinds));
    }
}
