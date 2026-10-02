package com.aetherianartificer.townstead.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/** Parses old resource ids retained by a renamed data-pack definition. */
public final class LegacyIds {
    private LegacyIds() {}

    public static Set<ResourceLocation> parse(JsonObject json, ResourceLocation owner) {
        if (json == null || !json.has("legacy_ids")) return Set.of();
        JsonElement element = json.get("legacy_ids");
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("'legacy_ids' must be an array of resource ids");
        }
        JsonArray array = element.getAsJsonArray();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Every legacy id must be a resource id string");
            }
            ResourceLocation id = DataPackLang.parseId(value.getAsString());
            if (id == null) throw new IllegalArgumentException("Invalid legacy id " + value);
            if (id.equals(owner)) throw new IllegalArgumentException("A definition cannot list its own id as legacy");
            if (!ids.add(id)) throw new IllegalArgumentException("Legacy id " + id + " is repeated");
        }
        return Set.copyOf(ids);
    }
}
