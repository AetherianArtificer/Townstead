package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One reaction definition loaded from a data pack JSON. Holds metadata,
 * gating thresholds, declarative triggers (kept as raw {@link JsonObject}
 * for the trigger layer to parse), and the binding list. Bindings are the
 * weighted animation candidates the dispatcher picks from when the
 * reaction fires.
 */
public record Reaction(
        ResourceLocation id,
        Optional<String> displayName,
        List<String> tags,
        int cooldownTicks,
        float chance,
        int lockTicks,
        ReactionConditions conditions,
        Optional<Condition> phenoCondition,
        Optional<Action> phenoAction,
        int mirrorRadius,
        float mirrorChance,
        int hearts,
        List<JsonObject> rawTriggers,
        List<ReactionBinding> bindings,
        boolean replace) {

    /**
     * Merge {@code prior} with {@code higherPriority}: tags / bindings /
     * triggers concatenate (then dedup), scalars are taken from the
     * higher-priority pack, conditions concatenate required tags. When
     * {@code higherPriority.replace()} is true, {@code prior} is dropped
     * and only {@code higherPriority} survives.
     */
    public static Reaction mergeFrom(Reaction prior, Reaction higherPriority) {
        if (prior == null || higherPriority.replace()) return higherPriority;

        java.util.LinkedHashSet<String> mergedTags = new java.util.LinkedHashSet<>(prior.tags());
        mergedTags.addAll(higherPriority.tags());

        java.util.LinkedHashSet<ReactionBinding> mergedBindings = new java.util.LinkedHashSet<>(prior.bindings());
        mergedBindings.addAll(higherPriority.bindings());

        java.util.LinkedHashSet<JsonObject> mergedTriggers = new java.util.LinkedHashSet<>(prior.rawTriggers());
        mergedTriggers.addAll(higherPriority.rawTriggers());

        java.util.LinkedHashSet<String> mergedRequiredTags =
                new java.util.LinkedHashSet<>(prior.conditions().requiredTags());
        mergedRequiredTags.addAll(higherPriority.conditions().requiredTags());
        ReactionConditions mergedConditions = new ReactionConditions(
                List.copyOf(mergedRequiredTags),
                higherPriority.conditions().timePhase().or(prior.conditions()::timePhase),
                higherPriority.conditions().weather().or(prior.conditions()::weather));

        return new Reaction(
                higherPriority.id(),
                higherPriority.displayName().or(prior::displayName),
                List.copyOf(mergedTags),
                higherPriority.cooldownTicks(),
                higherPriority.chance(),
                higherPriority.lockTicks(),
                mergedConditions,
                higherPriority.phenoCondition().or(prior::phenoCondition),
                higherPriority.phenoAction().or(prior::phenoAction),
                higherPriority.mirrorRadius(),
                higherPriority.mirrorChance(),
                higherPriority.hearts() != 0 ? higherPriority.hearts() : prior.hearts(),
                List.copyOf(mergedTriggers),
                List.copyOf(mergedBindings),
                false);
    }

    public static Reaction parse(ResourceLocation id, JsonObject json) {
        String schema = GsonHelper.getAsString(json, "schema", "");
        boolean v2 = "townstead:reaction/v2".equals(schema);
        if (!schema.isEmpty() && !v2) {
            throw new IllegalArgumentException("Unknown reaction schema '" + schema + "'");
        }
        Optional<String> displayName =
                json.has("display_name") ? Optional.of(GsonHelper.getAsString(json, "display_name")) : Optional.empty();
        List<String> tags = ReactionConditions.parseStringArray(json, "tags");
        int cooldownTicks = Math.max(0, duration(json, "cooldown", "cooldown_ticks", 100, v2));
        float chance = clamp01(percent(json, "chance", 1.0F, v2));
        int lockTicks = Math.max(0, duration(json, "lock", "lock_ticks", 0, v2));
        ReactionConditions conditions = json.has("conditions") && json.get("conditions").isJsonObject()
                ? ReactionConditions.fromJson(json.getAsJsonObject("conditions"))
                : ReactionConditions.EMPTY;
        Optional<Condition> phenoCondition = Optional.empty();
        Optional<Action> phenoAction = Optional.empty();
        if (v2 && json.has("when")) {
            if (!json.get("when").isJsonObject()) {
                throw new IllegalArgumentException("'when' must be a Pheno condition object");
            }
            JsonObject normalized = PhenoNormalizer.normalizeCondition(json.getAsJsonObject("when"));
            Condition parsed = Conditions.parse(normalized);
            if (parsed == null) throw new IllegalArgumentException("Invalid Pheno condition in 'when'");
            phenoCondition = Optional.of(parsed);
        }
        if (v2 && json.has("do")) {
            JsonElement normalized = PhenoNormalizer.normalizeAction(json.get("do"));
            Action parsed = Actions.parse(normalized);
            if (parsed == null) throw new IllegalArgumentException("Invalid Pheno action in 'do'");
            phenoAction = Optional.of(parsed);
        }
        int mirrorRadius = Math.max(0, GsonHelper.getAsInt(json, "mirror_radius", 0));
        float mirrorChance = clamp01(GsonHelper.getAsFloat(json, "mirror_chance", 0.0F));
        int hearts = GsonHelper.getAsInt(json, "hearts", 0);
        boolean replace = GsonHelper.getAsBoolean(json, "replace", false);

        List<JsonObject> rawTriggers = new ArrayList<>();
        if (json.has("triggers") && json.get("triggers").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("triggers")) {
                if (e.isJsonObject()) rawTriggers.add(e.getAsJsonObject());
            }
        }

        List<ReactionBinding> bindings = new ArrayList<>();
        String bindingField = v2 && json.has("choices") ? "choices" : "bindings";
        if (json.has(bindingField) && json.get(bindingField).isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray(bindingField)) {
                ReactionBinding b = ReactionBinding.parse(e, v2);
                if (b != null) bindings.add(b);
            }
        }
        // No "empty bindings" warning here — merge contributions may
        // legitimately ship triggers-only or bindings-only fragments
        // that compose with other packs.
        return new Reaction(id, displayName, tags, cooldownTicks, chance, lockTicks,
                conditions, phenoCondition, phenoAction, mirrorRadius, mirrorChance, hearts,
                Collections.unmodifiableList(rawTriggers),
                Collections.unmodifiableList(bindings),
                replace);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) return 0.0F;
        if (value > 1.0F) return 1.0F;
        return value;
    }

    private static int duration(JsonObject json, String v2Key, String legacyKey, int fallback, boolean v2) {
        String key = v2 && json.has(v2Key) ? v2Key : legacyKey;
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsInt();
        if (!v2 || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return fallback;
        String raw = value.getAsString().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            if (raw.endsWith("ms")) return (int) Math.round(Double.parseDouble(raw.substring(0, raw.length() - 2)) / 50d);
            if (raw.endsWith("s")) return (int) Math.round(Double.parseDouble(raw.substring(0, raw.length() - 1)) * 20d);
            if (raw.endsWith("t")) raw = raw.substring(0, raw.length() - 1);
            return (int) Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Malformed duration '" + value.getAsString() + "' for '" + key + "'");
        }
    }

    private static float percent(JsonObject json, String key, float fallback, boolean v2) {
        if (!json.has(key)) return fallback;
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) return value.getAsFloat();
        if (!v2 || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return fallback;
        String raw = value.getAsString().trim();
        try {
            return raw.endsWith("%")
                    ? Float.parseFloat(raw.substring(0, raw.length() - 1)) / 100F
                    : Float.parseFloat(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Malformed percentage '" + raw + "' for '" + key + "'");
        }
    }
}
