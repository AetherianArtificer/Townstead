package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lowers one optional-mod compatibility contribution into semantic profession mappings.
 *
 * <p>An {@code alias} names another registry profession that means the owning Career. A
 * {@code path_alias} is deliberately stronger: it means the owning Career with one of its Paths.
 * Neither form registers the foreign profession or supplies an acquisition route.</p>
 */
public final class ProfessionCompatibilityDocument {

    public static final String SCHEMA = "townstead:profession_compatibility/v1";

    private ProfessionCompatibilityDocument() {
    }

    public static Map<ResourceLocation, ProfessionDefs.Resolution> parse(
            ResourceLocation profession, JsonObject document, Set<String> declaredPaths) {
        TownsteadSchema.validateRequired(document, SCHEMA);
        Map<ResourceLocation, ProfessionDefs.Resolution> mappings = new LinkedHashMap<>();

        if (document.has("aliases")) {
            if (!document.get("aliases").isJsonArray()) {
                throw new IllegalArgumentException("'aliases' must be an array of profession ids");
            }
            for (JsonElement element : document.getAsJsonArray("aliases")) {
                ResourceLocation alias = id(element, "alias");
                put(mappings, alias, new ProfessionDefs.Resolution(profession, null));
            }
        }

        if (document.has("path_aliases")) {
            if (!document.get("path_aliases").isJsonObject()) {
                throw new IllegalArgumentException(
                        "'path_aliases' must map profession ids to Path ids");
            }
            for (Map.Entry<String, JsonElement> entry
                    : document.getAsJsonObject("path_aliases").entrySet()) {
                ResourceLocation alias = ResourceLocation.tryParse(entry.getKey());
                if (alias == null) {
                    throw new IllegalArgumentException(
                            "Invalid path-alias profession id '" + entry.getKey() + "'");
                }
                if (!entry.getValue().isJsonPrimitive()
                        || !entry.getValue().getAsJsonPrimitive().isString()
                        || entry.getValue().getAsString().isBlank()) {
                    throw new IllegalArgumentException(
                            "Path alias '" + alias + "' must name a Path id");
                }
                String path = entry.getValue().getAsString();
                if (!declaredPaths.contains(path)) {
                    throw new IllegalArgumentException(
                            "Path alias '" + alias + "' names unknown Path '" + path + "'");
                }
                put(mappings, alias, new ProfessionDefs.Resolution(profession, path));
            }
        }
        return Map.copyOf(mappings);
    }

    private static ResourceLocation id(JsonElement element, String kind) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Every " + kind + " must be a profession id");
        }
        ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
        if (id == null) throw new IllegalArgumentException(
                "Invalid " + kind + " profession id '" + element.getAsString() + "'");
        return id;
    }

    private static void put(Map<ResourceLocation, ProfessionDefs.Resolution> mappings,
                            ResourceLocation alias, ProfessionDefs.Resolution resolution) {
        ProfessionDefs.Resolution previous = mappings.putIfAbsent(alias, resolution);
        if (previous != null && !previous.equals(resolution)) {
            throw new IllegalArgumentException(
                    "Profession '" + alias + "' has more than one compatibility meaning");
        }
    }
}
