package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
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
 * One animation candidate inside a {@link Reaction}. The {@code ref} field
 * in JSON parses into a backend prefix (e.g. {@code emotecraft}) plus one
 * or more case-preserved backend-specific identifiers. When more than one
 * identifier is listed, the backend picks uniformly at fire time after the
 * picker has already chosen this binding.
 */
public record ReactionBinding(
        String backendKey,
        List<String> refIds,
        Optional<JsonObject> args,
        float weight,
        float chance,
        int shots,
        int cooldownTicks,
        Map<String, Float> personalityWeights,
        List<String> requiredTags,
        Optional<SoundSpec> sound,
        Optional<ParticleSpec> particles,
        Optional<String> speechPool) {

    /**
     * Parse a binding entry. Returns {@code null} when the entry is
     * structurally invalid (no usable {@code ref}); the loader logs and
     * skips. Bindings whose {@code ref} entries don't all share the same
     * backend prefix are likewise rejected.
     */
    public static ReactionBinding parse(JsonElement entry) {
        if (!entry.isJsonObject()) return null;
        JsonObject json = entry.getAsJsonObject();
        if (!json.has("ref")) {
            Townstead.LOGGER.warn("Reaction binding missing 'ref' field; skipped");
            return null;
        }
        List<String> rawRefs = parseRefField(json.get("ref"));
        if (rawRefs.isEmpty()) {
            Townstead.LOGGER.warn("Reaction binding has empty 'ref' list; skipped");
            return null;
        }
        String backendKey = null;
        List<String> ids = new java.util.ArrayList<>(rawRefs.size());
        for (String raw : rawRefs) {
            int colon = raw.indexOf(':');
            if (colon <= 0 || colon >= raw.length() - 1) {
                Townstead.LOGGER.warn("Reaction ref '{}' lacks '<backend>:<id>' shape; skipped", raw);
                return null;
            }
            String backend = raw.substring(0, colon).toLowerCase(Locale.ROOT);
            String id = raw.substring(colon + 1);
            if (backendKey == null) {
                backendKey = backend;
            } else if (!backendKey.equals(backend)) {
                Townstead.LOGGER.warn("Reaction binding mixes backends ('{}' vs '{}'); skipped", backendKey, backend);
                return null;
            }
            ids.add(id);
        }
        Optional<JsonObject> args = json.has("args") && json.get("args").isJsonObject()
                ? Optional.of(json.getAsJsonObject("args"))
                : Optional.empty();
        float weight = Math.max(0.0F, GsonHelper.getAsFloat(json, "weight", 1.0F));
        float chance = clamp01(GsonHelper.getAsFloat(json, "chance", 1.0F));
        int shots = Math.max(1, GsonHelper.getAsInt(json, "shots", 1));
        int cooldownTicks = Math.max(0, GsonHelper.getAsInt(json, "cooldown_ticks", 0));
        Map<String, Float> personality = parsePersonalityWeights(json.has("personality_weights")
                ? json.getAsJsonObject("personality_weights")
                : null);
        List<String> requiredTags = ReactionConditions.parseStringArray(json, "required_tags");
        Optional<SoundSpec> sound = json.has("sound") && json.get("sound").isJsonObject()
                ? SoundSpec.fromJson(json.getAsJsonObject("sound"))
                : Optional.empty();
        Optional<ParticleSpec> particles = json.has("particles") && json.get("particles").isJsonObject()
                ? ParticleSpec.fromJson(json.getAsJsonObject("particles"))
                : Optional.empty();
        Optional<String> speechPool =
                json.has("speech_pool") ? Optional.of(GsonHelper.getAsString(json, "speech_pool")) : Optional.empty();
        return new ReactionBinding(backendKey, Collections.unmodifiableList(ids), args, weight, chance, shots,
                cooldownTicks, personality, requiredTags, sound, particles, speechPool);
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

    /**
     * Effective personality multiplier for the given (already lowercased)
     * personality key. Falls back to the {@code default} entry, then to
     * {@code 1.0}.
     */
    public float personalityMultiplier(String personalityKeyLower) {
        Float exact = personalityWeights.get(personalityKeyLower);
        if (exact != null) return exact;
        Float def = personalityWeights.get("default");
        return def != null ? def : 1.0F;
    }
}
