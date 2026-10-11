package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared pre-parse pass for gated, overridable, patchable data documents. A loader hands it
 * the raw files of one kind and parses what comes back:
 *
 * <ol>
 *   <li><b>Gate.</b> A document whose {@code "mods"}/{@code "requires"} gate is unmet is dropped;
 *       a malformed gate drops it with a warning.</li>
 *   <li><b>Overrides.</b> Each entry of the document's own {@code "overrides"} array whose gate is
 *       met is merged over the document, in order (see {@link #merge}).</li>
 *   <li><b>Patches.</b> Every {@code townstead:patch/v1} document targeting this kind and id is
 *       applied, lowest priority first (see {@link DataPatches}).</li>
 * </ol>
 *
 * The gate, override and patch keys are stripped from the result, so a loader's own
 * {@link ModGate#allows} check always passes on what it receives.
 */
public final class DataDocuments {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/DataDocuments");

    public static final String OVERRIDES = "overrides";

    private DataDocuments() {}

    /** Gate, override and patch every document of {@code kind}; the result keeps input order. */
    public static Map<ResourceLocation, JsonElement> resolve(String kind, Map<ResourceLocation, JsonElement> entries,
                                                             ResourceManager manager) {
        DataNamespaces.record(manager);
        DataPatches patches = DataPatches.forManager(manager);
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) {
                out.put(entry.getKey(), value);
                continue;
            }
            JsonObject resolved = resolveOne(kind, entry.getKey(), value.getAsJsonObject(), patches);
            if (resolved != null) out.put(entry.getKey(), resolved);
        }
        return out;
    }

    /** Gate and override one document, then apply patches; null when the gate drops it. */
    public static @Nullable JsonObject resolveOne(String kind, ResourceLocation id, JsonObject document,
                                                  @Nullable DataPatches patches) {
        JsonObject resolved = applyOverrides(document, id.toString());
        if (resolved == null) return null;
        if (patches != null) resolved = patches.apply(kind, id, resolved);
        return resolved;
    }

    /**
     * Gate {@code document} and merge its met {@code overrides} entries over it. Returns a copy
     * without the gate or override keys, or null when its gate is unmet or malformed.
     */
    public static @Nullable JsonObject applyOverrides(JsonObject document, String context) {
        Boolean met = ModGate.evaluateDocument(document);
        if (met == null) {
            LOGGER.warn("Skipping {}: malformed \"mods\"/\"requires\" gate", context);
            return null;
        }
        if (!met) return null;
        JsonObject result = document.deepCopy();
        stripControlKeys(result);
        JsonElement overrides = document.get(OVERRIDES);
        if (overrides == null) return result;
        if (!overrides.isJsonArray()) {
            LOGGER.warn("Ignoring {} in {}: expected an array", OVERRIDES, context);
            return result;
        }
        int index = 0;
        for (JsonElement element : overrides.getAsJsonArray()) {
            String where = context + "." + OVERRIDES + "[" + index++ + "]";
            if (!element.isJsonObject()) {
                LOGGER.warn("Ignoring {}: expected an object", where);
                continue;
            }
            JsonObject overlay = element.getAsJsonObject();
            Boolean overlayMet = ModGate.evaluateDocument(overlay);
            if (overlayMet == null) {
                LOGGER.warn("Ignoring {}: malformed \"mods\"/\"requires\" gate", where);
                continue;
            }
            if (!overlayMet) continue;
            JsonObject body = overlay.deepCopy();
            stripControlKeys(body);
            merge(result, body);
        }
        return result;
    }

    /**
     * Merge {@code overlay} into {@code base} in place: objects merge key by key, every other
     * value (arrays included) replaces, and a JSON null removes the key.
     */
    public static void merge(JsonObject base, JsonObject overlay) {
        for (Map.Entry<String, JsonElement> entry : overlay.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                base.remove(key);
            } else if (value.isJsonObject() && base.has(key) && base.get(key).isJsonObject()) {
                merge(base.getAsJsonObject(key), value.getAsJsonObject());
            } else {
                base.add(key, value.deepCopy());
            }
        }
    }

    private static void stripControlKeys(JsonObject object) {
        object.remove(ModGate.MODS);
        object.remove(ModGate.REQUIRES);
        object.remove(OVERRIDES);
    }

    /** A dotted path's parent object, created on the way when {@code create}; null when absent. */
    static @Nullable JsonObject parentOf(JsonObject root, String path, boolean create) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement next = current.get(parts[i]);
            if (next == null || !next.isJsonObject()) {
                if (!create) return null;
                JsonObject made = new JsonObject();
                current.add(parts[i], made);
                current = made;
            } else {
                current = next.getAsJsonObject();
            }
        }
        return current;
    }

    static String leafOf(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }

    /** The identity of a list element for union, removal and replacement. */
    static String identity(JsonElement element) {
        if (element == null || element.isJsonNull()) return "null";
        if (element.isJsonPrimitive()) return element.getAsString();
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : new String[]{"gene", "id", "type"}) {
                JsonElement v = object.get(key);
                if (v != null && v.isJsonPrimitive()) return v.getAsString();
            }
        }
        return element.toString();
    }

    static JsonArray arrayAt(JsonObject root, String path, boolean create) {
        JsonObject parent = parentOf(root, path, create);
        if (parent == null) return null;
        String leaf = leafOf(path);
        JsonElement existing = parent.get(leaf);
        if (existing != null && existing.isJsonArray()) return existing.getAsJsonArray();
        if (!create) return null;
        JsonArray made = new JsonArray();
        if (existing != null && !existing.isJsonNull()) made.add(existing);
        parent.add(leaf, made);
        return made;
    }
}
