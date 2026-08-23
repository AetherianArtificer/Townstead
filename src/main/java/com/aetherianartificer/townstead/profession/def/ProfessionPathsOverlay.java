package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Set;

/** Applies the path-definition sidecar without allowing it to rewrite the Career record. */
public final class ProfessionPathsOverlay {

    public static final String SCHEMA = "townstead:profession_paths/v1";

    private ProfessionPathsOverlay() {
    }

    /** Replaces the profession's inline path list with the list declared by {@code paths.json}. */
    public static void apply(JsonObject profession, JsonObject paths) {
        TownsteadSchema.validate(paths, SCHEMA);
        if (!paths.has("paths") || !paths.get("paths").isJsonArray()) {
            throw new IllegalArgumentException("'paths' must be an array");
        }
        JsonArray pathList = paths.getAsJsonArray("paths").deepCopy();
        profession.add("paths", pathList);
        deriveCompletionTitles(profession, pathList);
    }

    /** A path-owned title is earned by learning its gateway and every remaining member. */
    static void deriveCompletionTitles(JsonObject profession, JsonArray paths) {
        boolean hadStandaloneTitles = profession.has("titles")
                && profession.get("titles").isJsonArray();
        JsonArray titles = hadStandaloneTitles
                ? profession.getAsJsonArray("titles").deepCopy() : new JsonArray();
        boolean derivedAny = false;

        for (JsonElement element : paths) {
            if (!element.isJsonObject()) continue;
            JsonObject path = element.getAsJsonObject();
            if (!path.has("title")) continue;
            String id = string(path, "id");
            String gateway = string(path, "gateway");
            if (id.isBlank() || gateway.isBlank()) {
                throw new IllegalArgumentException("A path with 'title' needs an id and gateway");
            }
            if (path.get("title").isJsonNull()) {
                throw new IllegalArgumentException("Title for path '" + id + "' cannot be null");
            }

            Set<String> members = new LinkedHashSet<>();
            members.add(gateway);
            if (path.has("skills") && path.get("skills").isJsonArray()) {
                for (JsonElement skill : path.getAsJsonArray("skills")) {
                    if (skill.isJsonPrimitive() && skill.getAsJsonPrimitive().isString()) {
                        members.add(skill.getAsString());
                    }
                }
            }

            JsonObject title = new JsonObject();
            title.addProperty("id", id);
            title.add("name", path.get("title").deepCopy());
            JsonArray skills = new JsonArray();
            members.forEach(skills::add);
            title.add("skills", skills);
            for (int i = titles.size() - 1; i >= 0; i--) {
                JsonElement existing = titles.get(i);
                if (existing.isJsonObject() && id.equals(string(existing.getAsJsonObject(), "id"))) {
                    titles.remove(i);
                }
            }
            titles.add(title);
            derivedAny = true;
        }

        if (hadStandaloneTitles || derivedAny) profession.add("titles", titles);
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.getAsJsonPrimitive(key).isString()
                ? object.get(key).getAsString() : "";
    }
}
