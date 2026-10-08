package com.aetherianartificer.townstead.culture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which cultures a Root's founders tend to come from, authored on ancestries, lineages and roots
 * and composed most-general first by {@code RootRegistry.effectiveCulturalSpawnBias}.
 *
 * <p>A tendency, never a rule. A Piglin culture is mostly Piglins and a few Overworlders who
 * settled there, so a root leans toward some cultures without being confined to them, and
 * {@link Cultures#ANY} keeps the set open so a founder can always turn up somewhere unexpected.</p>
 *
 * <p>This is the only place in the naming and culture system where a species has any say, and even
 * here it only weights a roll for a villager who spawned with no parents and no home. Anyone with
 * either inherits instead, so this never decides a culture for someone who has one available. It is
 * a sibling of {@link com.aetherianartificer.townstead.root.SpawnBias}, which weights founders by
 * biome, and composes the same way: a lineage overrides its ancestry's weight for the same culture,
 * and a root overrides both.</p>
 *
 * <pre>{@code
 * "cultural_spawn_bias": [
 *   { "culture": "townstead_classic:highhold", "rate": 6 },
 *   { "culture": "any",                        "rate": 2 }
 * ]
 * }</pre>
 */
public record CulturalSpawnBias(Map<String, Float> weights) {

    public static final CulturalSpawnBias EMPTY = new CulturalSpawnBias(Map.of());

    public CulturalSpawnBias {
        weights = weights == null ? Map.of() : Map.copyOf(weights);
    }

    public boolean isEmpty() {
        return weights.isEmpty();
    }

    /** One weighted claim on a culture. {@code any} means any loaded culture. */
    public record Entry(String culture, float rate) {}

    /** The declared weights, in authoring order, as entries. */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>(weights.size());
        for (Map.Entry<String, Float> weight : weights.entrySet()) {
            entries.add(new Entry(weight.getKey(), weight.getValue()));
        }
        return entries;
    }

    /** Layers a more specific bias over this one; a repeated culture takes the newer weight. */
    public CulturalSpawnBias mergedWith(CulturalSpawnBias over) {
        if (over == null || over.isEmpty()) return this;
        if (this.isEmpty()) return over;
        Map<String, Float> merged = new LinkedHashMap<>(this.weights);
        merged.putAll(over.weights);
        return new CulturalSpawnBias(merged);
    }

    /** Reads a {@code cultural_spawn_bias} array, ignoring entries that name nothing. */
    public static CulturalSpawnBias parse(JsonObject owner) {
        JsonElement element = owner == null ? null : owner.get("cultural_spawn_bias");
        if (element == null || !element.isJsonArray()) return EMPTY;

        Map<String, Float> weights = new LinkedHashMap<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (value == null || !value.isJsonObject()) continue;
            JsonObject entry = value.getAsJsonObject();
            String culture = GsonHelper.getAsString(entry, "culture", "").trim();
            if (culture.isEmpty()) continue;
            float rate = GsonHelper.getAsFloat(entry, "rate", 1.0F);
            if (rate <= 0.0F) continue;
            weights.put(culture, rate);
        }
        return weights.isEmpty() ? EMPTY : new CulturalSpawnBias(weights);
    }
}
