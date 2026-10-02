package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.*;

/** Strict JSON reading shared by the generative conversation documents. */
final class Json {
    private Json() {}

    static void only(JsonObject json, String... fields) {
        Set<String> allowed = Set.of(fields);
        for (String field : json.keySet()) if (!allowed.contains(field)) throw bad(field, "unknown field");
    }

    static IllegalArgumentException bad(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }

    static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive()) return List.of(value.getAsString());
        List<String> out = new ArrayList<>();
        for (JsonElement e : value.getAsJsonArray()) out.add(GsonHelper.convertToString(e, key));
        return List.copyOf(out);
    }

    static Set<String> stringSet(JsonObject json, String key) {
        return Set.copyOf(strings(json, key));
    }

    /** An id without a namespace is a {@code townstead:} id. */
    static ResourceLocation parseId(String raw) {
        return ResourceLocation.tryParse(raw.indexOf(':') < 0 ? "townstead:" + raw : raw);
    }

    /** A subject filter with the default namespace applied: an id or a {@code #tag}. */
    static String subjectFilter(String raw) {
        boolean tag = raw.startsWith("#");
        String body = tag ? raw.substring(1) : raw;
        return (tag ? "#" : "") + (body.indexOf(':') < 0 ? "townstead:" + body : body);
    }

    static Set<String> subjectSet(JsonObject json, String key) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : strings(json, key)) out.add(subjectFilter(raw));
        return Set.copyOf(out);
    }

    static ResourceLocation id(JsonObject json, String key) {
        if (!json.has(key)) return null;
        ResourceLocation id = parseId(json.get(key).getAsString());
        if (id == null) throw bad(key, "expected resource id");
        return id;
    }

    static ResourceLocation requiredId(JsonObject json, String key) {
        ResourceLocation id = id(json, key);
        if (id == null) throw bad(key, "is required");
        return id;
    }

    static List<ResourceLocation> ids(JsonObject json, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        for (String raw : strings(json, key)) {
            ResourceLocation id = parseId(raw);
            if (id == null) throw bad(key, "expected resource id: " + raw);
            out.add(id);
        }
        return List.copyOf(out);
    }

    static double number(JsonObject json, String key, double fallback, double min, double max) {
        double value = GsonHelper.getAsDouble(json, key, fallback);
        if (!Double.isFinite(value) || value < min || value > max) throw bad(key, "must be " + min + ".." + max);
        return value;
    }

    static Map<String, Double> numbers(JsonObject json, String key, double min, double max) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (json.has(key)) for (var e : GsonHelper.getAsJsonObject(json, key).entrySet()) {
            double value = e.getValue().getAsDouble();
            if (!Double.isFinite(value) || value < min || value > max) throw bad(key + "." + e.getKey(), "must be " + min + ".." + max);
            out.put(e.getKey(), value);
        }
        return Map.copyOf(out);
    }

    /** A state value as the composer compares it: strings, booleans and numbers all become strings. */
    static String scalar(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive()) throw bad(field, "must be a string, boolean or number");
        return value.getAsString();
    }
}
