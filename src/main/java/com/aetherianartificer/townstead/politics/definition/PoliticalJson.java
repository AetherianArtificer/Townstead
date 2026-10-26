package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class PoliticalJson {
    private PoliticalJson() {}

    static ResourceLocation requiredId(JsonObject json, String field) {
        ResourceLocation parsed = optionalId(json, field);
        if (parsed == null) throw new IllegalArgumentException("'" + field + "' must be a resource id");
        return parsed;
    }

    static ResourceLocation optionalId(JsonObject json, String field) {
        if (json == null || !json.has(field)) return null;
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("'" + field + "' must be a resource id");
        }
        ResourceLocation parsed = DataPackLang.parseId(value.getAsString());
        if (parsed == null) throw new IllegalArgumentException("'" + field + "' must be a resource id");
        return parsed;
    }

    static List<ResourceLocation> ids(JsonObject json, String field) {
        if (json == null || !json.has(field)) return List.of();
        JsonElement value = json.get(field);
        if (!value.isJsonArray()) throw new IllegalArgumentException("'" + field + "' must be an array");
        List<ResourceLocation> out = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("'" + field + "' contains a non-string value");
            }
            ResourceLocation id = DataPackLang.parseId(element.getAsString());
            if (id == null) throw new IllegalArgumentException("'" + field + "' contains an invalid resource id");
            out.add(id);
        }
        return List.copyOf(out);
    }

    static Set<ResourceLocation> idSet(JsonObject json, String field) {
        List<ResourceLocation> values = ids(json, field);
        LinkedHashSet<ResourceLocation> unique = new LinkedHashSet<>(values);
        if (unique.size() != values.size()) throw new IllegalArgumentException("'" + field + "' contains duplicates");
        return Set.copyOf(unique);
    }

    static JsonObject object(JsonObject json, String field, boolean required) {
        if (json != null && json.has(field)) {
            if (json.get(field).isJsonObject()) return json.getAsJsonObject(field);
            throw new IllegalArgumentException("'" + field + "' must be an object");
        }
        if (required) throw new IllegalArgumentException("'" + field + "' must be an object");
        return null;
    }

    static JsonArray array(JsonObject json, String field, boolean required) {
        if (json != null && json.has(field)) {
            if (json.get(field).isJsonArray()) return json.getAsJsonArray(field);
            throw new IllegalArgumentException("'" + field + "' must be an array");
        }
        if (required) throw new IllegalArgumentException("'" + field + "' must be an array");
        return null;
    }
}
