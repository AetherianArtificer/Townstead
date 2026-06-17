package com.aetherianartificer.townstead.origin.gene;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.types.ResourceGeneType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Peels a gene's root {@code resources} section into companion resource genes. A gene can
 * declare meters next to the behavior that uses them (a triple-jump's jump counter, a spell's
 * mana) instead of in a separate file; each becomes a real {@code pheno:resource} gene with the
 * derived id {@code <parentId>/<name>} that <em>rides along</em> its parent's expression (see
 * {@code GenePowerSource}), so it ticks, regenerates and syncs exactly like a standalone resource.
 *
 * <p>Within the same gene, a bare {@code "resource": "name"} (or {@code compared_to_resource})
 * is rewritten to the derived id, so authors reference the short local name. References that
 * already carry a namespace are left untouched (a global resource).</p>
 */
public final class GeneCompanions {

    private GeneCompanions() {}

    /**
     * Extract a gene root's companion resources, mutating {@code root} in place: the
     * {@code resources} block is removed and local references are rewritten to derived ids.
     * Returns the companion configs keyed by derived id (empty when none are declared).
     */
    public static Map<ResourceLocation, JsonObject> extract(ResourceLocation file, JsonObject root) {
        if (!root.has("resources") || !root.get("resources").isJsonObject()) return Map.of();
        JsonObject block = root.getAsJsonObject("resources");
        Map<ResourceLocation, JsonObject> companions = new LinkedHashMap<>();
        Map<String, String> rename = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : block.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            String name = entry.getKey();
            ResourceLocation derived = DataPackLang.parseId(file.getNamespace() + ":" + file.getPath() + "/" + name);
            if (derived == null) continue;
            JsonObject config = entry.getValue().getAsJsonObject().deepCopy();
            if (!config.has("type")) config.addProperty("type", ResourceGeneType.KEY);
            companions.put(derived, config);
            rename.put(name, derived.toString());
        }
        root.remove("resources");
        rewriteRefs(root, rename);
        return companions;
    }

    /** Rewrite local {@code resource}/{@code compared_to_resource} string values to their derived ids. */
    private static void rewriteRefs(JsonElement element, Map<String, String> rename) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) rewriteRefs(child, rename);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject obj = element.getAsJsonObject();
        for (String key : new ArrayList<>(obj.keySet())) {
            JsonElement value = obj.get(key);
            boolean ref = (key.equals("resource") || key.equals("compared_to_resource"))
                    && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            if (ref) {
                String renamed = rename.get(value.getAsString());
                if (renamed != null) obj.addProperty(key, renamed);
            } else {
                rewriteRefs(value, rename);
            }
        }
    }
}
