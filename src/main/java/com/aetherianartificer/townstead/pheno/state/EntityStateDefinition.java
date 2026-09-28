package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Public identity and tier vocabulary for one open semantic entity state. While it is active, the
 * states it {@code excludes} cannot take hold (a thrall cannot be turned into a vampire).
 */
public record EntityStateDefinition(
        ResourceLocation id,
        double min,
        double max,
        double initial,
        List<Tier> tiers,
        MergePolicy merge,
        Persistence persistence,
        DeathPolicy deathPolicy,
        @Nullable Aspect aspect,
        java.util.Set<ResourceLocation> excludes) {

    public static final String SCHEMA = "pheno:entity_state/v1";

    public enum MergePolicy { FIRST, MAX, SUM, LATEST }
    public enum Persistence { PERSISTENT, SESSION }
    public enum DeathPolicy { KEEP, CLEAR }
    /** Which parents must carry the aspect: any, both, or only the father (or mother) and not the other. */
    public enum Parents { ANY, BOTH, FATHER_ONLY, MOTHER_ONLY }

    public record Tier(String id, double min) {}

    /**
     * Marks a lasting identity state (vampire, dhampir) as opposed to a passing one (drunk).
     * {@code level} shows its amount as a level where it is displayed, instead of its tier.
     */
    public record Aspect(boolean display, @Nullable Inheritance inheritance, boolean level) {}

    /** A child born to carriers of this aspect receives {@code aspect} with {@code chance}. */
    public record Inheritance(ResourceLocation aspect, double chance, Parents parents) {}

    public EntityStateDefinition {
        tiers = List.copyOf(tiers);
        excludes = excludes == null ? java.util.Set.of() : java.util.Set.copyOf(excludes);
    }

    public double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }

    public @Nullable Tier tier(double value) {
        Tier selected = null;
        for (Tier tier : tiers) {
            if (value >= tier.min()) selected = tier;
            else break;
        }
        return selected;
    }

    public int tierIndex(double value) {
        Tier tier = tier(value);
        return tier == null ? -1 : tiers.indexOf(tier);
    }

    public @Nullable Tier tier(String id) {
        if (id == null) return null;
        for (Tier tier : tiers) if (tier.id().equals(id)) return tier;
        return null;
    }

    static EntityStateDefinition parse(ResourceLocation fileId, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        ResourceLocation id = json.has("id")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "id")) : fileId;
        if (id == null) throw new IllegalArgumentException("'id' must be a resource id");
        double min = GsonHelper.getAsDouble(json, "min", 0);
        double max = GsonHelper.getAsDouble(json, "max", 1);
        if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max) {
            throw new IllegalArgumentException("state range requires finite min < max");
        }
        double initial = GsonHelper.getAsDouble(json, "initial", min);
        if (!Double.isFinite(initial) || initial < min || initial > max) {
            throw new IllegalArgumentException("'initial' must be inside the state range");
        }

        List<Tier> tiers = new ArrayList<>();
        Set<String> names = new HashSet<>();
        if (json.has("tiers")) {
            if (!json.get("tiers").isJsonArray()) {
                throw new IllegalArgumentException("'tiers' must be an array");
            }
            for (JsonElement element : json.getAsJsonArray("tiers")) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("each tier must be an object");
                JsonObject tierJson = element.getAsJsonObject();
                String name = GsonHelper.getAsString(tierJson, "id", "").trim();
                double threshold = GsonHelper.getAsDouble(tierJson, "min", Double.NaN);
                if (name.isEmpty() || !names.add(name)) throw new IllegalArgumentException("tier ids must be non-empty and unique");
                if (!Double.isFinite(threshold) || threshold < min || threshold > max) {
                    throw new IllegalArgumentException("tier '" + name + "' threshold is outside the state range");
                }
                tiers.add(new Tier(name, threshold));
            }
        }
        tiers.sort(Comparator.comparingDouble(Tier::min));
        return new EntityStateDefinition(id, min, max, initial, tiers,
                enumValue(json, "merge", MergePolicy.MAX, MergePolicy.class),
                enumValue(json, "persistence", Persistence.PERSISTENT, Persistence.class),
                enumValue(json, "death", DeathPolicy.CLEAR, DeathPolicy.class),
                json.has("aspect") ? aspect(id, json.get("aspect")) : null,
                excludes(json));
    }

    private static java.util.Set<ResourceLocation> excludes(JsonObject json) {
        if (!json.has("excludes")) return java.util.Set.of();
        java.util.Set<ResourceLocation> out = new java.util.LinkedHashSet<>();
        for (var element : GsonHelper.getAsJsonArray(json, "excludes")) {
            ResourceLocation state = DataPackLang.parseId(element.getAsString());
            if (state == null) throw new IllegalArgumentException("'" + element.getAsString() + "' is not a state id");
            out.add(state);
        }
        return out;
    }

    /** The amount a newly received aspect starts at: its first tier, else fully present. */
    public double receivedAmount() {
        for (Tier tier : tiers) if (tier.min() > min) return tier.min();
        return max;
    }

    private static Aspect aspect(ResourceLocation self, JsonElement element) {
        if (!element.isJsonObject()) throw new IllegalArgumentException("'aspect' must be an object");
        JsonObject json = element.getAsJsonObject();
        Inheritance inheritance = null;
        if (json.has("inheritance")) {
            if (!json.get("inheritance").isJsonObject()) throw new IllegalArgumentException("'inheritance' must be an object");
            JsonObject inherit = json.getAsJsonObject("inheritance");
            String mode = GsonHelper.getAsString(inherit, "mode", "none").toLowerCase(Locale.ROOT);
            ResourceLocation target = switch (mode) {
                case "none" -> null;
                case "same" -> self;
                case "child_aspect" -> {
                    ResourceLocation parsed = DataPackLang.parseId(GsonHelper.getAsString(inherit, "aspect", ""));
                    if (parsed == null) throw new IllegalArgumentException("child_aspect inheritance requires an 'aspect' id");
                    yield parsed;
                }
                default -> throw new IllegalArgumentException("unknown inheritance mode '" + mode + "'");
            };
            double chance = GsonHelper.getAsDouble(inherit, "chance", 1);
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) {
                throw new IllegalArgumentException("inheritance chance must be in [0,1]");
            }
            if (target != null) {
                inheritance = new Inheritance(target, chance,
                        enumValue(inherit, "parents", Parents.ANY, Parents.class));
            }
        }
        return new Aspect(GsonHelper.getAsBoolean(json, "display", true), inheritance,
                GsonHelper.getAsBoolean(json, "level", false));
    }

    private static <E extends Enum<E>> E enumValue(JsonObject json, String key, E fallback, Class<E> type) {
        String value = GsonHelper.getAsString(json, key, fallback.name()).toUpperCase(Locale.ROOT);
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown " + key + " policy '" + value.toLowerCase(Locale.ROOT) + "'");
        }
    }
}
