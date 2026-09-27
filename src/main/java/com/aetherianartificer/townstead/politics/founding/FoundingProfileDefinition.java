package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.SpawnBias;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A data-authored recipe for giving one recognized settlement its first civic identity. */
public record FoundingProfileDefinition(ResourceLocation id,
                                        Component displayName,
                                        @Nullable ResourceLocation culture,
                                        float weight,
                                        SpawnBias spawnBias,
                                        Condition when,
                                        Population population,
                                        @Nullable FactionSpec faction) {
    public static final String SCHEMA = "townstead:founding_profile/v1";
    public static final ResourceLocation CULTURAL_AFFINITY = id("townstead:cultural_affinity");

    public FoundingProfileDefinition {
        if (weight < 0.0F || !Float.isFinite(weight)) {
            throw new IllegalArgumentException("'weight' must be a finite non-negative number");
        }
        spawnBias = spawnBias == null ? SpawnBias.EMPTY : spawnBias;
        when = when == null ? Conditions.ALWAYS : when;
        population = population == null ? Population.DEFAULT : population;
    }

    public static FoundingProfileDefinition parse(ResourceLocation id, JsonObject json,
                                                   Map<String, String> lang) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        Component name = json.has("name")
                ? com.aetherianartificer.townstead.data.DataPackLang.parseComponent(
                        json.get("name"), id + ".name", lang)
                : Component.literal(id.getPath());
        ResourceLocation culture = optionalId(json, "culture");
        float weight = GsonHelper.getAsFloat(json, "weight", 1.0F);
        Condition when = Conditions.ALWAYS;
        if (json.has("when")) {
            when = Conditions.parse(json.get("when"));
            if (when == null) throw new IllegalArgumentException("'when' is not a registered Pheno condition");
        }
        return new FoundingProfileDefinition(id, name, culture, weight, parseSpawnBias(json), when,
                Population.parse(object(json, "population", false)),
                json.has("faction") ? FactionSpec.parse(object(json, "faction", true), false)
                        : json.has("government") ? FactionSpec.parse(object(json, "government", true), true) : null);
    }

    public record Population(ResourceLocation strategy,
                             float outsiderBaseline,
                             Map<ResourceLocation, Float> adjustments) {
        private static final Population DEFAULT = new Population(CULTURAL_AFFINITY, 0.1F, Map.of());

        public Population {
            adjustments = Map.copyOf(new LinkedHashMap<>(adjustments));
            if (outsiderBaseline < 0.0F || !Float.isFinite(outsiderBaseline)) {
                throw new IllegalArgumentException("'population.outsider_baseline' must be finite and non-negative");
            }
            for (Map.Entry<ResourceLocation, Float> entry : adjustments.entrySet()) {
                if (entry.getValue() < 0.0F || !Float.isFinite(entry.getValue())) {
                    throw new IllegalArgumentException("Root adjustment for " + entry.getKey()
                            + " must be finite and non-negative");
                }
            }
        }

        static Population parse(@Nullable JsonObject json) {
            if (json == null) return DEFAULT;
            ResourceLocation strategy = optionalId(json, "strategy");
            if (strategy == null) strategy = CULTURAL_AFFINITY;
            float outsider = GsonHelper.getAsFloat(json, "outsider_baseline", 0.1F);
            Map<ResourceLocation, Float> adjustments = new LinkedHashMap<>();
            JsonArray array = array(json, "adjustments", false);
            if (array != null) {
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        throw new IllegalArgumentException("Every population adjustment must be an object");
                    }
                    JsonObject value = element.getAsJsonObject();
                    ResourceLocation root = requiredId(value, "root");
                    if (adjustments.put(root, GsonHelper.getAsFloat(value, "multiplier", 1.0F)) != null) {
                        throw new IllegalArgumentException("Root " + root + " is adjusted twice");
                    }
                }
            }
            return new Population(strategy, outsider, adjustments);
        }
    }

    /**
     * The faction a founded settlement starts as: its kind and who fills its offices first. The older
     * {@code government} block ({@code organization_kind}, seats of {@code roles}) still loads; a role
     * that is not an office of the kind, such as {@code member}, is dropped when the profile is applied.
     */
    public record FactionSpec(ResourceLocation kind, List<Seat> seats, boolean legacy) {
        public FactionSpec {
            seats = List.copyOf(seats);
        }

        public List<Set<ResourceLocation>> bundles() {
            List<Set<ResourceLocation>> out = new ArrayList<>();
            for (Seat seat : seats) for (int i = 0; i < seat.count(); i++) out.add(seat.offices());
            return List.copyOf(out);
        }

        static FactionSpec parse(JsonObject json, boolean legacy) {
            ResourceLocation kind = requiredId(json, legacy ? "organization_kind" : "kind");
            List<Seat> seats = new ArrayList<>();
            JsonArray array = array(json, "seats", false);
            if (array != null) {
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) throw new IllegalArgumentException("Every seat must be an object");
                    seats.add(Seat.parse(element.getAsJsonObject(), legacy ? "roles" : "offices"));
                }
            }
            return new FactionSpec(kind, seats, legacy);
        }
    }

    /** One or more residents take this exact set of offices. */
    public record Seat(Set<ResourceLocation> offices, int count) {
        public Seat {
            offices = Set.copyOf(new LinkedHashSet<>(offices));
            if (offices.isEmpty()) throw new IllegalArgumentException("A seat must hold at least one office");
            if (count < 1) throw new IllegalArgumentException("A seat count must be at least one");
        }

        static Seat parse(JsonObject json, String field) {
            JsonArray values = array(json, field, true);
            Set<ResourceLocation> offices = new LinkedHashSet<>();
            for (JsonElement element : values) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("Seat " + field + " must be resource ids");
                }
                ResourceLocation office = DataPackLang.parseId(element.getAsString());
                if (office == null) throw new IllegalArgumentException("Seat entry is not a resource id");
                if (!offices.add(office)) throw new IllegalArgumentException("Seat entry " + office + " is repeated");
            }
            return new Seat(offices, GsonHelper.getAsInt(json, "count", 1));
        }
    }

    private static SpawnBias parseSpawnBias(JsonObject owner) {
        JsonObject json = object(owner, "spawn_bias", false);
        if (json == null) return SpawnBias.EMPTY;
        Float fallback = json.has("default") ? GsonHelper.getAsFloat(json, "default") : null;
        return new SpawnBias(fallback, weights(json, "biomes", false),
                weights(json, "biome_tags", true), weights(json, "dimensions", false));
    }

    private static Map<ResourceLocation, Float> weights(JsonObject owner, String field, boolean stripHash) {
        JsonObject json = object(owner, field, false);
        if (json == null) return Map.of();
        Map<ResourceLocation, Float> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String raw = stripHash && entry.getKey().startsWith("#") ? entry.getKey().substring(1) : entry.getKey();
            ResourceLocation id = DataPackLang.parseId(raw);
            if (id == null) throw new IllegalArgumentException("'" + field + "' contains invalid id " + entry.getKey());
            float weight = entry.getValue().getAsFloat();
            if (weight < 0.0F || !Float.isFinite(weight)) {
                throw new IllegalArgumentException("Environmental weight for " + id + " must be finite and non-negative");
            }
            out.put(id, weight);
        }
        return out;
    }

    private static ResourceLocation requiredId(JsonObject json, String field) {
        ResourceLocation value = optionalId(json, field);
        if (value == null) throw new IllegalArgumentException("'" + field + "' must be a resource id");
        return value;
    }

    private static @Nullable ResourceLocation optionalId(JsonObject json, String field) {
        if (json == null || !json.has(field)) return null;
        JsonElement value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("'" + field + "' must be a resource id");
        }
        ResourceLocation parsed = DataPackLang.parseId(value.getAsString());
        if (parsed == null) throw new IllegalArgumentException("'" + field + "' must be a resource id");
        return parsed;
    }

    private static @Nullable JsonObject object(JsonObject json, String field, boolean required) {
        if (json != null && json.has(field)) {
            if (json.get(field).isJsonObject()) return json.getAsJsonObject(field);
            throw new IllegalArgumentException("'" + field + "' must be an object");
        }
        if (required) throw new IllegalArgumentException("'" + field + "' must be an object");
        return null;
    }

    private static @Nullable JsonArray array(JsonObject json, String field, boolean required) {
        if (json != null && json.has(field)) {
            if (json.get(field).isJsonArray()) return json.getAsJsonArray(field);
            throw new IllegalArgumentException("'" + field + "' must be an array");
        }
        if (required) throw new IllegalArgumentException("'" + field + "' must be an array");
        return null;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = DataPackLang.parseId(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
