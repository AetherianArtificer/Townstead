package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Map;
import java.util.Set;

/** One data-defined dimension of a directional relationship. */
public record RelationshipQuality(ResourceLocation id, String displayLangKey, String displayLiteral,
                                  float minimum, float maximum, float neutral,
                                  int defaultHalfLifeDays, float pruneBelow) {
    public static final String SCHEMA = "townstead:relationship_quality/v1";

    public RelationshipQuality {
        if (!Float.isFinite(minimum) || !Float.isFinite(maximum) || minimum >= maximum)
            throw new IllegalArgumentException("range must contain finite min < max");
        if (!Float.isFinite(neutral) || neutral < minimum || neutral > maximum)
            throw new IllegalArgumentException("neutral must be inside range");
        if (defaultHalfLifeDays < 0) throw new IllegalArgumentException("default_half_life_days must be >= 0");
        if (!Float.isFinite(pruneBelow) || pruneBelow < 0)
            throw new IllegalArgumentException("prune_below must be finite and >= 0");
    }

    public float clamp(double value) { return (float) Math.max(minimum, Math.min(maximum, value)); }

    public static RelationshipQuality fallback(ResourceLocation id) {
        return new RelationshipQuality(id, "", id.getPath(), -100, 100, 0, 0, 0.01f);
    }

    public static RelationshipQuality parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        only(json, "schema", "display", "range", "decay");
        TownsteadSchema.validateRequired(json, SCHEMA);
        String langKey = "relationship_quality." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        String literal = id.getPath().replace('_', ' ');
        if (json.has("display")) {
            JsonObject display = GsonHelper.getAsJsonObject(json, "display");
            only(display, "translate", "text");
            if (display.has("translate")) langKey = GsonHelper.getAsString(display, "translate");
            else literal = GsonHelper.getAsString(display, "text");
        }
        literal = lang.getOrDefault(langKey, DataPackLang.resolveFallback(langKey, "en_us", literal));
        JsonObject range = GsonHelper.getAsJsonObject(json, "range");
        only(range, "min", "max", "neutral");
        JsonObject decay = json.has("decay") ? GsonHelper.getAsJsonObject(json, "decay") : new JsonObject();
        only(decay, "default_half_life_days", "prune_below");
        return new RelationshipQuality(id, langKey, literal,
                GsonHelper.getAsFloat(range, "min", -100), GsonHelper.getAsFloat(range, "max", 100),
                GsonHelper.getAsFloat(range, "neutral", 0),
                GsonHelper.getAsInt(decay, "default_half_life_days", 0),
                GsonHelper.getAsFloat(decay, "prune_below", 0.01f));
    }

    private static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field))
            throw new IllegalArgumentException(field + ": unknown field");
    }
}
