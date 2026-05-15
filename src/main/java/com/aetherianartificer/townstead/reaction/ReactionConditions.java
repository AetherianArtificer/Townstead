package com.aetherianartificer.townstead.reaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Reaction-level gates evaluated before any binding selection. All listed
 * required tags must be present in the resolved {@code ContextResolver}
 * tag set. {@code timePhase} and {@code weather} are optional single-value
 * predicates matched against the same tag set.
 */
public record ReactionConditions(List<String> requiredTags, Optional<String> timePhase,
                                 Optional<String> weather) {
    public static final ReactionConditions EMPTY = new ReactionConditions(List.of(), Optional.empty(), Optional.empty());

    public static ReactionConditions fromJson(JsonObject json) {
        if (json == null) return EMPTY;
        List<String> tags = parseStringArray(json, "required_tags");
        Optional<String> time = json.has("time") ? Optional.of(GsonHelper.getAsString(json, "time")) : Optional.empty();
        Optional<String> weather =
                json.has("weather") ? Optional.of(GsonHelper.getAsString(json, "weather")) : Optional.empty();
        return new ReactionConditions(tags, time, weather);
    }

    public static List<String> parseStringArray(JsonObject json, String key) {
        if (json == null || !json.has(key)) return List.of();
        JsonElement element = json.get(key);
        if (!element.isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                out.add(e.getAsString());
            }
        }
        return Collections.unmodifiableList(out);
    }
}
