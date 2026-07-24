package com.aetherianartificer.townstead.profession.def;

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
 * {@code workstations}, {@code entities}, {@code recipes}, and {@code deny_recipes} entries are
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
        int weight,
        Condition requirements) {

    /** An id/#tag set gating one target axis. Empty allow sets admit everything; empty deny sets deny nothing. */
    public record TargetSet(Set<ResourceLocation> ids, List<ResourceLocation> tags) {
        public static final TargetSet EMPTY = new TargetSet(Set.of(), List.of());

        public boolean isEmpty() {
            return ids.isEmpty() && tags.isEmpty();
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
        if (matchesRecipe(recipesDenied, recipeId, outputId)) return false;
        return recipes.isEmpty() || matchesRecipe(recipes, recipeId, outputId);
    }

    private static boolean matchesRecipe(TargetSet set, @Nullable ResourceLocation recipeId,
                                         @Nullable ResourceLocation outputId) {
        if (set.isEmpty()) return false;
        if (recipeId != null && set.ids().contains(recipeId)) return true;
        if (outputId == null) return false;
        if (set.ids().contains(outputId)) return true;
        if (set.tags().isEmpty()) return false;
        var stack = BuiltInRegistries.ITEM.get(outputId).getDefaultInstance();
        for (ResourceLocation tagId : set.tags()) {
            if (stack.is(TagKey.create(Registries.ITEM, tagId))) return true;
        }
        return false;
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
        if (workstations == null || entities == null || recipes == null || denied == null) return null;

        Condition requirements = Conditions.ALWAYS;
        if (obj.has("requirements")) {
            requirements = Conditions.parse(obj.get("requirements"));
            if (requirements == null) return null;
        }
        return new WorkTaskDef(type, workstations, entities, recipes, denied,
                GsonHelper.getAsInt(obj, "weight", 1), requirements);
    }

    /** Reads an id/#tag string array into a {@link TargetSet}; null on any malformed entry. */
    private static @Nullable TargetSet readIdSet(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return TargetSet.EMPTY;
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        List<ResourceLocation> tags = new ArrayList<>();
        for (JsonElement e : obj.getAsJsonArray(key)) {
            if (!e.isJsonPrimitive()) return null;
            String raw = e.getAsString();
            if (raw.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
                if (tagId == null) return null;
                tags.add(tagId);
            } else {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) return null;
                ids.add(id);
            }
        }
        return new TargetSet(Set.copyOf(ids), List.copyOf(tags));
    }
}
