package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Map;
import java.util.Set;

/** Authoring policy and presentation for one kind of remembered experience. */
public record SocialMemoryDefinition(ResourceLocation id, String displayLangKey, String displayLiteral,
                                     float defaultStrength, float defaultValence,
                                     int halfLifeDays, float forgetBelow, int retentionPriority) {
    public static final String SCHEMA = "townstead:social_memory/v1";

    public SocialMemoryDefinition {
        if (!Float.isFinite(defaultStrength) || defaultStrength <= 0 || defaultStrength > 100)
            throw new IllegalArgumentException("default_strength must be > 0 and <= 100");
        if (!Float.isFinite(defaultValence) || defaultValence < -1 || defaultValence > 1)
            throw new IllegalArgumentException("default_valence must be between -1 and 1");
        if (halfLifeDays < 0) throw new IllegalArgumentException("half_life_days must be >= 0");
        if (!Float.isFinite(forgetBelow) || forgetBelow < 0)
            throw new IllegalArgumentException("forget_below must be finite and >= 0");
        if (retentionPriority < 0 || retentionPriority > 100)
            throw new IllegalArgumentException("retention_priority must be 0..100");
    }

    public static SocialMemoryDefinition fallback(ResourceLocation id) {
        return new SocialMemoryDefinition(id, "", id.getPath().replace('_', ' '), 1, 0, 60, 0.05f, 10);
    }

    public static SocialMemoryDefinition parse(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        only(json, "schema", "display", "experience", "forgetting");
        TownsteadSchema.validateRequired(json, SCHEMA);
        String langKey = "social_memory." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        String literal = id.getPath().replace('_', ' ');
        if (json.has("display")) {
            JsonObject display = GsonHelper.getAsJsonObject(json, "display");
            only(display, "translate", "text");
            if (display.has("translate")) langKey = GsonHelper.getAsString(display, "translate");
            else literal = GsonHelper.getAsString(display, "text");
        }
        literal = lang.getOrDefault(langKey, DataPackLang.resolveFallback(langKey, "en_us", literal));
        JsonObject experience = json.has("experience") ? GsonHelper.getAsJsonObject(json, "experience") : new JsonObject();
        only(experience, "default_strength", "default_valence");
        JsonObject forgetting = json.has("forgetting") ? GsonHelper.getAsJsonObject(json, "forgetting") : new JsonObject();
        only(forgetting, "half_life_days", "forget_below", "retention_priority");
        return new SocialMemoryDefinition(id, langKey, literal,
                GsonHelper.getAsFloat(experience, "default_strength", 1),
                GsonHelper.getAsFloat(experience, "default_valence", 0),
                GsonHelper.getAsInt(forgetting, "half_life_days", 60),
                GsonHelper.getAsFloat(forgetting, "forget_below", 0.05f),
                GsonHelper.getAsInt(forgetting, "retention_priority", 10));
    }

    private static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field))
            throw new IllegalArgumentException(field + ": unknown field");
    }
}
