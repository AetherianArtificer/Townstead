package com.aetherianartificer.townstead.spirit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static map: building-type id → spirit-id → point contribution.
 *
 * Populated by {@code CatalogDataLoader} as it scans both the inline
 * {@code townsteadSpirit} field on Townstead-authored building_type JSONs
 * and the companion {@code data/townstead/spirit/<type>.json} files that
 * annotate vanilla MCA building types without clobbering them.
 *
 * Readers ({@link VillageSpiritAggregator}, and the client Spirit page)
 * treat the map as immutable. Writers go through {@link #put} / {@link #clear}
 * during reload.
 */
public final class BuildingSpiritIndex {
    private static final Map<String, Map<String, Integer>> CONTRIBUTIONS = new ConcurrentHashMap<>();

    private BuildingSpiritIndex() {}

    /** Returns an immutable map of spirit id → points for the given building type, or an empty map. */
    public static Map<String, Integer> contributionsFor(String buildingType) {
        if (buildingType == null) return Map.of();
        Map<String, Integer> m = CONTRIBUTIONS.get(buildingType);
        if (m != null) return m;
        Map<String, Integer> scanned = scanClasspathCompanion(buildingType);
        if (scanned.isEmpty()) {
            scanned = scanInlineBuildingType(buildingType);
        }
        if (!scanned.isEmpty()) {
            put(buildingType, scanned);
            return CONTRIBUTIONS.getOrDefault(buildingType, Map.of());
        }
        return Map.of();
    }

    /**
     * Store a contribution map for a building type. Later calls for the same
     * building type overwrite — the loader is responsible for merge semantics
     * (last writer wins, which matches how other `CatalogDataLoader` fields
     * behave).
     */
    public static void put(String buildingType, Map<String, Integer> contributions) {
        if (buildingType == null || contributions == null || contributions.isEmpty()) return;
        CONTRIBUTIONS.put(buildingType, Map.copyOf(contributions));
    }

    public static void clear() {
        CONTRIBUTIONS.clear();
    }

    public static int size() {
        return CONTRIBUTIONS.size();
    }

    private static Map<String, Integer> scanClasspathCompanion(String buildingType) {
        try {
            ClassLoader cl = BuildingSpiritIndex.class.getClassLoader();
            if (cl == null) return Map.of();
            String relPath = "data/townstead/spirit/" + buildingType + ".json";
            Enumeration<URL> urls = cl.getResources(relPath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream();
                        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj == null || !obj.has("townsteadSpirit")) continue;
                    return parseSpiritMap(obj.getAsJsonObject("townsteadSpirit"));
                }
            }
        } catch (Exception ignored) {}
        return Map.of();
    }

    private static Map<String, Integer> scanInlineBuildingType(String buildingType) {
        String relPath = buildingType.startsWith("compat/")
                ? "townstead_compat/building_types/" + buildingType + ".json"
                : "data/mca/building_types/" + buildingType + ".json";
        return scanInlineSpiritJson(relPath);
    }

    private static Map<String, Integer> scanInlineSpiritJson(String relPath) {
        try {
            ClassLoader cl = BuildingSpiritIndex.class.getClassLoader();
            if (cl == null) return Map.of();
            Enumeration<URL> urls = cl.getResources(relPath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream();
                        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj == null || !obj.has("townsteadSpirit")) continue;
                    return parseSpiritMap(obj.getAsJsonObject("townsteadSpirit"));
                }
            }
        } catch (Exception ignored) {}
        return Map.of();
    }

    private static Map<String, Integer> parseSpiritMap(JsonObject spirit) {
        if (spirit == null || spirit.size() == 0) return Map.of();
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : spirit.entrySet()) {
            if (!SpiritRegistry.contains(e.getKey())) continue;
            try {
                int pts = e.getValue().getAsInt();
                if (pts > 0) out.put(e.getKey(), pts);
            } catch (Exception ignored) {}
        }
        return out;
    }
}
