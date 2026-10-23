package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Additive patches: {@code data/<ns>/patch/<path>.json} documents that change another pack's
 * data without replacing its file.
 *
 * <pre>
 * {
 *   "schema": "townstead:patch/v1",
 *   "requires": {"namespace": "townstead_classic"},
 *   "target": {"ancestry": "townstead_classic:elf"},
 *   "priority": 0,
 *   "set":      {"spawn_bias": {"default": 0.4}},
 *   "remove":   {"genes": ["townstead_classic:elf_stature"]},
 *   "replace":  {"genes": {"townstead_classic:elf_darkvision": "my_pack:darkvision"}},
 *   "add":      {"genes": ["my_pack:moonlit_eyes"], "spawn_bias.biome_tags.#minecraft:is_forest": 4},
 *   "multiply": {"spawn_bias.default": 0.8}
 * }
 * </pre>
 *
 * <p>{@code target} maps a data kind (the directory name, e.g. {@code ancestry}, {@code lineage},
 * {@code root}, {@code gene}, {@code founding_profile}) to one id or an array of ids; it may also
 * be an array of such objects. Operations run in the order set, remove, replace, add, multiply.
 * {@code set} merges like an override. List paths are dotted; list elements are matched by their
 * id (a string, or an object's {@code gene}/{@code id}/{@code type}), and {@code add} is a stable
 * union that never duplicates. A non-array {@code add} value at a path sets it only when absent.</p>
 *
 * <p>Patches apply lowest {@code priority} first, then by patch id, so the highest priority wins
 * any {@code set}. Everything is rebuilt from the resource manager on every reload, so removing a
 * pack removes its patches. A patch whose target is missing is not an error: optional
 * compatibility is the normal case.</p>
 */
public final class DataPatches {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/DataPatches");
    private static final Gson GSON = new Gson();

    public static final String SCHEMA = "townstead:patch/v1";
    public static final String DIRECTORY = "patch";

    /** A parsed, gated patch. */
    public record Patch(ResourceLocation id, int priority, Map<String, Set<ResourceLocation>> targets,
                        JsonObject document) {
        boolean targets(String kind, ResourceLocation target) {
            Set<ResourceLocation> ids = targets.get(kind);
            return ids != null && ids.contains(target);
        }
    }

    private static WeakReference<ResourceManager> cachedFor = new WeakReference<>(null);
    private static DataPatches cached = new DataPatches(List.of());
    /** Which patches touched each kind/id in the latest reload, for diagnostics. */
    private static final Map<String, List<ResourceLocation>> PROVENANCE = new LinkedHashMap<>();

    private final List<Patch> patches;

    DataPatches(List<Patch> patches) {
        List<Patch> sorted = new ArrayList<>(patches);
        sorted.sort(Comparator.comparingInt(Patch::priority).thenComparing(p -> p.id().toString()));
        this.patches = List.copyOf(sorted);
    }

    public List<Patch> patches() {
        return patches;
    }

    /** The patches of {@code manager}, parsed once per reload. */
    public static synchronized DataPatches forManager(ResourceManager manager) {
        if (manager == null) return cached;
        if (cachedFor.get() == manager) return cached;
        DataNamespaces.record(manager);
        List<Patch> parsed = new ArrayList<>();
        Map<ResourceLocation, Resource> files = manager.listResources(DIRECTORY,
                loc -> loc.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            ResourceLocation location = file.getKey();
            String path = location.getPath();
            ResourceLocation id = DataPackLang.parseId(location.getNamespace() + ":"
                    + path.substring(DIRECTORY.length() + 1, path.length() - ".json".length()));
            try (Reader reader = file.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                Patch patch = parse(id, json);
                if (patch != null) parsed.add(patch);
            } catch (Exception ex) {
                LOGGER.warn("Failed to read patch {}: {}", location, ex.getMessage());
            }
        }
        cached = new DataPatches(parsed);
        cachedFor = new WeakReference<>(manager);
        synchronized (PROVENANCE) {
            PROVENANCE.clear();
        }
        if (!parsed.isEmpty()) LOGGER.info("Loaded {} data patches", parsed.size());
        return cached;
    }

    /** Parse one patch document; null when it is gated off or malformed. */
    public static @Nullable Patch parse(ResourceLocation id, @Nullable JsonObject json) {
        if (json == null) return null;
        String context = "patch " + id;
        try {
            TownsteadSchema.validateRequired(json, SCHEMA);
        } catch (Exception ex) {
            LOGGER.warn("Skipping {}: {}", context, ex.getMessage());
            return null;
        }
        Boolean met = ModGate.evaluateDocument(json);
        if (met == null) {
            LOGGER.warn("Skipping {}: malformed \"mods\"/\"requires\" gate", context);
            return null;
        }
        if (!met) return null;
        Map<String, Set<ResourceLocation>> targets = new LinkedHashMap<>();
        if (!collectTargets(json.get("target"), targets)) {
            LOGGER.warn("Skipping {}: \"target\" must map a data kind to an id or id list", context);
            return null;
        }
        int priority = GsonHelper.getAsInt(json, "priority", 0);
        return new Patch(id, priority, targets, json);
    }

    private static boolean collectTargets(@Nullable JsonElement value, Map<String, Set<ResourceLocation>> out) {
        if (value == null) return false;
        if (value.isJsonArray()) {
            for (JsonElement e : value.getAsJsonArray()) {
                if (!collectTargets(e, out)) return false;
            }
            return !out.isEmpty();
        }
        if (!value.isJsonObject()) return false;
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            String kind = canonicalKind(entry.getKey());
            Set<ResourceLocation> ids = out.computeIfAbsent(kind, k -> new LinkedHashSet<>());
            JsonElement idValue = entry.getValue();
            if (idValue.isJsonArray()) {
                for (JsonElement e : idValue.getAsJsonArray()) {
                    ResourceLocation id = e.isJsonPrimitive() ? DataPackLang.parseId(e.getAsString()) : null;
                    if (id == null) return false;
                    ids.add(id);
                }
            } else if (idValue.isJsonPrimitive()) {
                ResourceLocation id = DataPackLang.parseId(idValue.getAsString());
                if (id == null) return false;
                ids.add(id);
            } else {
                return false;
            }
        }
        return !out.isEmpty();
    }

    /** Legacy {@code origin} files are roots; patches name either. */
    public static String canonicalKind(String kind) {
        String k = kind.toLowerCase(Locale.ROOT);
        return "origin".equals(k) ? "root" : k;
    }

    /** Apply every patch that targets {@code kind}/{@code id} to a copy of {@code document}. */
    public JsonObject apply(String kind, ResourceLocation id, JsonObject document) {
        String canonical = canonicalKind(kind);
        JsonObject result = null;
        for (Patch patch : patches) {
            if (!patch.targets(canonical, id)) continue;
            if (result == null) result = document.deepCopy();
            applyPatch(patch.document(), result, patch.id().toString());
            synchronized (PROVENANCE) {
                PROVENANCE.computeIfAbsent(canonical + " " + id, k -> new ArrayList<>()).add(patch.id());
            }
        }
        return result != null ? result : document;
    }

    /** Apply one patch's operations to {@code target} in place. */
    public static void applyPatch(JsonObject patch, JsonObject target, String context) {
        JsonObject set = object(patch, "set");
        if (set != null) DataDocuments.merge(target, set);

        JsonObject remove = object(patch, "remove");
        if (remove != null) {
            for (Map.Entry<String, JsonElement> op : remove.entrySet()) {
                JsonArray list = DataDocuments.arrayAt(target, op.getKey(), false);
                if (list == null) continue;
                Set<String> drop = new LinkedHashSet<>();
                for (JsonElement e : asArray(op.getValue())) drop.add(DataDocuments.identity(e));
                JsonArray kept = new JsonArray();
                for (JsonElement e : list) if (!drop.contains(DataDocuments.identity(e))) kept.add(e);
                replaceArray(target, op.getKey(), kept);
            }
        }

        JsonObject replace = object(patch, "replace");
        if (replace != null) {
            for (Map.Entry<String, JsonElement> op : replace.entrySet()) {
                if (!op.getValue().isJsonObject()) {
                    LOGGER.warn("Ignoring replace.{} in {}: expected an object of old → new", op.getKey(), context);
                    continue;
                }
                JsonArray list = DataDocuments.arrayAt(target, op.getKey(), false);
                if (list == null) continue;
                JsonObject mapping = op.getValue().getAsJsonObject();
                JsonArray swapped = new JsonArray();
                for (JsonElement e : list) {
                    JsonElement with = mapping.get(DataDocuments.identity(e));
                    swapped.add(with == null ? e : substitute(e, with));
                }
                replaceArray(target, op.getKey(), dedupe(swapped));
            }
        }

        JsonObject add = object(patch, "add");
        if (add != null) {
            for (Map.Entry<String, JsonElement> op : add.entrySet()) {
                JsonElement value = op.getValue();
                if (!value.isJsonArray()) {
                    JsonObject parent = DataDocuments.parentOf(target, op.getKey(), true);
                    String leaf = DataDocuments.leafOf(op.getKey());
                    if (parent != null && !parent.has(leaf)) parent.add(leaf, value.deepCopy());
                    continue;
                }
                JsonArray list = DataDocuments.arrayAt(target, op.getKey(), true);
                Set<String> seen = new LinkedHashSet<>();
                for (JsonElement e : list) seen.add(DataDocuments.identity(e));
                for (JsonElement e : value.getAsJsonArray()) {
                    if (seen.add(DataDocuments.identity(e))) list.add(e.deepCopy());
                }
            }
        }

        JsonObject multiply = object(patch, "multiply");
        if (multiply != null) {
            for (Map.Entry<String, JsonElement> op : multiply.entrySet()) {
                JsonObject parent = DataDocuments.parentOf(target, op.getKey(), false);
                String leaf = DataDocuments.leafOf(op.getKey());
                if (parent == null || !parent.has(leaf)) continue;
                JsonElement current = parent.get(leaf);
                if (!current.isJsonPrimitive() || !current.getAsJsonPrimitive().isNumber()
                        || !op.getValue().isJsonPrimitive() || !op.getValue().getAsJsonPrimitive().isNumber()) {
                    LOGGER.warn("Ignoring multiply.{} in {}: both values must be numbers", op.getKey(), context);
                    continue;
                }
                parent.addProperty(leaf, current.getAsDouble() * op.getValue().getAsDouble());
            }
        }
    }

    /** A replacement keeps an object element's other fields (e.g. a gene's occurrence). */
    private static JsonElement substitute(JsonElement original, JsonElement with) {
        if (original.isJsonObject() && original.getAsJsonObject().has("gene") && with.isJsonPrimitive()) {
            JsonObject copy = original.getAsJsonObject().deepCopy();
            copy.add("gene", with.deepCopy());
            return copy;
        }
        return with.deepCopy();
    }

    private static JsonArray dedupe(JsonArray array) {
        Set<String> seen = new LinkedHashSet<>();
        JsonArray out = new JsonArray();
        for (JsonElement e : array) if (seen.add(DataDocuments.identity(e))) out.add(e);
        return out;
    }

    private static void replaceArray(JsonObject root, String path, JsonArray value) {
        JsonObject parent = DataDocuments.parentOf(root, path, false);
        if (parent != null) parent.add(DataDocuments.leafOf(path), value);
    }

    private static JsonArray asArray(JsonElement value) {
        if (value.isJsonArray()) return value.getAsJsonArray();
        JsonArray single = new JsonArray();
        single.add(value);
        return single;
    }

    private static @Nullable JsonObject object(JsonObject json, String key) {
        JsonElement v = json.get(key);
        return v != null && v.isJsonObject() ? v.getAsJsonObject() : null;
    }

    /** Patches applied per {@code "<kind> <id>"} in the latest reload. */
    public static Map<String, List<ResourceLocation>> provenance() {
        synchronized (PROVENANCE) {
            Map<String, List<ResourceLocation>> copy = new LinkedHashMap<>();
            PROVENANCE.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            return copy;
        }
    }

    /** Every parsed patch target, for reporting targets nothing loaded. */
    public static Map<ResourceLocation, Map<String, Set<ResourceLocation>>> targets() {
        Map<ResourceLocation, Map<String, Set<ResourceLocation>>> out = new LinkedHashMap<>();
        for (Patch patch : cached.patches) out.put(patch.id(), patch.targets());
        return out;
    }
}
