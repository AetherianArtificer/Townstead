package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One weighted outcome inside a {@link Reaction}. In v3, an outcome may be a
 * Pheno action bundle with no legacy animation at all. When {@code ref} is
 * present it parses into a backend prefix (for example {@code emotecraft}) and
 * one or more backend-specific identifiers.
 */
public record ReactionBinding(
        String backendKey,
        List<String> refIds,
        Optional<JsonObject> args,
        float weight,
        float chance,
        int shots,
        int cooldownTicks,
        boolean allowMovement,
        List<String> partsSkip,
        Map<String, Float> personalityWeights,
        List<String> requiredTags,
        Optional<Condition> phenoCondition,
        Optional<Action> phenoAction,
        Optional<SoundSpec> sound,
        Optional<ParticleSpec> particles,
        Optional<String> speechPool,
        boolean animationRequired) {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
            "townstead/ReactionBinding");

    /**
     * Parse a binding entry. Returns {@code null} when the entry is
     * structurally invalid. V1/v2 require a usable {@code ref}; v3 requires at
     * least one animation or auxiliary output. Mixed backend prefixes are rejected.
     */
    public static ReactionBinding parse(JsonElement entry, int schemaVersion) {
        if (!entry.isJsonObject()) return null;
        boolean pheno = schemaVersion >= 2;
        boolean outcomeV3 = schemaVersion >= 3;
        JsonObject json = entry.getAsJsonObject().deepCopy();
        if (pheno && json.has("animation") && json.get("animation").isJsonObject()) {
            lowerAnimation(json, json.getAsJsonObject("animation"));
        }
        List<String> rawRefs = json.has("ref") ? parseRefField(json.get("ref")) : List.of();
        String backendKey = null;
        List<String> ids = new java.util.ArrayList<>(rawRefs.size());
        for (String raw : rawRefs) {
            int colon = raw.indexOf(':');
            if (colon <= 0 || colon >= raw.length() - 1) {
                LOGGER.warn("Reaction ref '{}' lacks '<backend>:<id>' shape; skipped", raw);
                return null;
            }
            String backend = raw.substring(0, colon).toLowerCase(Locale.ROOT);
            String id = raw.substring(colon + 1);
            if (backendKey == null) {
                backendKey = backend;
            } else if (!backendKey.equals(backend)) {
                LOGGER.warn("Reaction binding mixes backends ('{}' vs '{}'); skipped", backendKey, backend);
                return null;
            }
            ids.add(id);
        }
        Optional<JsonObject> args = json.has("args") && json.get("args").isJsonObject()
                ? Optional.of(json.getAsJsonObject("args"))
                : Optional.empty();
        float weight = Math.max(0.0F, GsonHelper.getAsFloat(json, "weight", 1.0F));
        float chance = clamp01(percent(json, "chance", 1.0F, pheno));
        int shots = Math.max(1, GsonHelper.getAsInt(json, "shots", 1));
        int cooldownTicks = Math.max(0, duration(json, "cooldown", "cooldown_ticks", 0, pheno));
        boolean allowMovement = GsonHelper.getAsBoolean(json, "allow_movement", false);
        List<String> partsSkip = ReactionConditions.parseStringArray(json, "parts_skip");
        // When allow_movement is set without an explicit parts_skip,
        // default to skipping legs + torso so vanilla walking shows
        // through and only the arms / head animate on top.
        if (allowMovement && partsSkip.isEmpty()) {
            partsSkip = List.of("legs", "torso");
        }
        Map<String, Float> personality = parsePersonalityWeights(json.has("personality_weights")
                ? json.getAsJsonObject("personality_weights")
                : null);
        List<String> requiredTags = ReactionConditions.parseStringArray(json, "required_tags");
        Optional<Condition> phenoCondition = Optional.empty();
        Optional<Action> phenoAction = Optional.empty();
        if (pheno && json.has("when")) {
            if (!json.get("when").isJsonObject()) {
                LOGGER.warn("Reaction binding 'when' must be an object; skipped");
                return null;
            }
            Condition parsed = Conditions.parse(
                    PhenoNormalizer.normalizeCondition(json.getAsJsonObject("when")));
            if (parsed == null) {
                LOGGER.warn("Reaction binding has an invalid Pheno 'when'; skipped");
                return null;
            }
            phenoCondition = Optional.of(parsed);
        }
        if (pheno && json.has("do")) {
            Action parsed = Actions.parse(PhenoNormalizer.normalizeAction(json.get("do")));
            if (parsed == null) {
                LOGGER.warn("Reaction binding has an invalid Pheno 'do'; skipped");
                return null;
            }
            phenoAction = Optional.of(parsed);
        }
        Optional<SoundSpec> sound = json.has("sound") && json.get("sound").isJsonObject()
                ? SoundSpec.fromJson(json.getAsJsonObject("sound"))
                : Optional.empty();
        Optional<ParticleSpec> particles = json.has("particles") && json.get("particles").isJsonObject()
                ? ParticleSpec.fromJson(json.getAsJsonObject("particles"))
                : Optional.empty();
        Optional<String> speechPool =
                json.has("speech_pool") ? Optional.of(GsonHelper.getAsString(json, "speech_pool")) : Optional.empty();
        boolean auxiliaryOutput = phenoAction.isPresent() || sound.isPresent()
                || particles.isPresent() || speechPool.filter(value -> !value.isBlank()).isPresent();
        if (backendKey == null && !outcomeV3) {
            LOGGER.warn("Reaction binding missing 'ref' field; skipped");
            return null;
        }
        if (backendKey == null && !auxiliaryOutput) {
            LOGGER.warn("Reaction v3 outcome has neither animation nor action; skipped");
            return null;
        }
        // In v1/v2 the backend was the transaction gate. V3 treats a legacy animation as one
        // optional output when the outcome also has Pheno/sound/particle/speech work, while an
        // animation-only outcome still requires it to play.
        boolean animationRequired = backendKey != null && (!outcomeV3 || !auxiliaryOutput);
        return new ReactionBinding(backendKey, Collections.unmodifiableList(ids), args, weight, chance, shots,
                cooldownTicks, allowMovement, partsSkip, personality, requiredTags,
                phenoCondition, phenoAction, sound, particles, speechPool, animationRequired);
    }

    /** Implicit outcome for a v3 reaction whose only output is its top-level {@code do}. */
    public static ReactionBinding actionOnly() {
        return new ReactionBinding(null, List.of(), Optional.empty(), 1F, 1F, 1, 0,
                false, List.of(), Map.of(), List.of(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    public boolean hasAnimation() {
        return backendKey != null && !backendKey.isBlank() && !refIds.isEmpty();
    }

    public boolean hasAuxiliaryOutput() {
        return phenoAction.isPresent() || sound.isPresent() || particles.isPresent()
                || speechPool.filter(value -> !value.isBlank()).isPresent();
    }

    private static void lowerAnimation(JsonObject choice, JsonObject animation) {
        String backend = GsonHelper.getAsString(animation, "type", "");
        if (backend.isBlank()) return;
        if (!choice.has("ref")) {
            if (animation.has("id")) {
                choice.addProperty("ref", backend + ":" + GsonHelper.getAsString(animation, "id"));
            } else if (animation.has("ids") && animation.get("ids").isJsonArray()) {
                JsonArray refs = new JsonArray();
                for (JsonElement id : animation.getAsJsonArray("ids")) {
                    if (id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) {
                        refs.add(backend + ":" + id.getAsString());
                    }
                }
                choice.add("ref", refs);
            }
        }
        for (String key : List.of("allow_movement", "parts_skip", "shots")) {
            if (animation.has(key) && !choice.has(key)) choice.add(key, animation.get(key));
        }
        JsonObject args = choice.has("args") && choice.get("args").isJsonObject()
                ? choice.getAsJsonObject("args") : new JsonObject();
        for (String key : List.of("speed", "loop_override")) {
            if (animation.has(key) && !args.has(key)) args.add(key, animation.get(key));
        }
        if (args.size() > 0) choice.add("args", args);
    }

    private static List<String> parseRefField(JsonElement ref) {
        if (ref.isJsonPrimitive() && ref.getAsJsonPrimitive().isString()) {
            String value = ref.getAsString();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (ref.isJsonArray()) {
            JsonArray arr = ref.getAsJsonArray();
            List<String> out = new java.util.ArrayList<>(arr.size());
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                    String v = e.getAsString();
                    if (!v.isBlank()) out.add(v);
                }
            }
            return out;
        }
        return List.of();
    }

    private static Map<String, Float> parsePersonalityWeights(JsonObject json) {
        if (json == null) return Map.of();
        Map<String, Float> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) continue;
            try {
                out.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().getAsFloat());
            } catch (Exception ignored) {}
        }
        return Collections.unmodifiableMap(out);
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
        String raw = value.getAsString().trim().toLowerCase(Locale.ROOT);
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

    /**
     * Effective personality multiplier for the given (already lowercased)
     * personality key. Falls back to the {@code default} entry, then to
     * {@code 1.0}.
     */
    public float personalityMultiplier(String personalityKeyLower) {
        Float exact = personalityWeights.get(personalityKeyLower);
        if (exact != null) return exact;
        if (personalityKeyLower != null) {
            String alias = personalityKeyLower.startsWith("mca:")
                    ? personalityKeyLower.substring("mca:".length())
                    : "mca:" + personalityKeyLower;
            exact = personalityWeights.get(alias);
            if (exact != null) return exact;
        }
        Float def = personalityWeights.get("default");
        return def != null ? def : 1.0F;
    }
}
