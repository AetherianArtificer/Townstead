package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Applies the villager-work sidecar without allowing it to rewrite Career progression. */
public final class ProfessionWorkOverlay {

    public static final String SCHEMA = "townstead:profession_work/v1";

    private ProfessionWorkOverlay() {
    }

    /**
     * Copies the fields owned by {@code work.json} onto the in-memory profession document.
     * {@code tasks} is the sidecar spelling of the legacy inline {@code work_tasks} field.
     */
    public static void apply(JsonObject profession, JsonObject work) {
        TownsteadSchema.validateRequired(work, SCHEMA);
        if (work.has("trades")) {
            throw new IllegalArgumentException("Merchant offers belong in trade/*.json");
        }
        copy(work, profession, "register_profession", "register_profession");
        copy(work, profession, "poi", "poi");
        if (work.has("tasks")) {
            copy(work, profession, "tasks", "work_tasks");
        } else {
            // Accept the internal spelling too, so extracting an old inline block is painless.
            copy(work, profession, "work_tasks", "work_tasks");
        }
        applyPathWorksites(profession, work);
    }

    private static void applyPathWorksites(JsonObject profession, JsonObject work) {
        if (!work.has("path_worksites")) return;
        if (!work.get("path_worksites").isJsonObject()) {
            throw new IllegalArgumentException("'path_worksites' must be an object keyed by path id");
        }
        if (!profession.has("paths") || !profession.get("paths").isJsonArray()) {
            throw new IllegalArgumentException(
                    "'path_worksites' requires Path documents to be composed first");
        }

        Map<String, JsonObject> paths = new LinkedHashMap<>();
        for (JsonElement element : profession.getAsJsonArray("paths")) {
            if (!element.isJsonObject()) continue;
            JsonObject path = element.getAsJsonObject();
            if (path.has("id") && path.get("id").isJsonPrimitive()) {
                paths.put(path.get("id").getAsString(), path);
            }
        }
        for (Map.Entry<String, JsonElement> entry
                : work.getAsJsonObject("path_worksites").entrySet()) {
            JsonObject path = paths.get(entry.getKey());
            if (path == null) {
                throw new IllegalArgumentException("Unknown path '" + entry.getKey()
                        + "' in 'path_worksites'");
            }
            if (!entry.getValue().isJsonArray()) {
                throw new IllegalArgumentException("Worksites for path '" + entry.getKey()
                        + "' must be an array");
            }
            path.add("worksites", entry.getValue().deepCopy());
        }
    }

    private static void copy(JsonObject from, JsonObject to, String source, String target) {
        if (from.has(source)) to.add(target, from.get(source).deepCopy());
    }
}
