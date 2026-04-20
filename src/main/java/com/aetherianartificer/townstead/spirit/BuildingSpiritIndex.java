package com.aetherianartificer.townstead.spirit;

import java.util.Map;
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
        return m != null ? m : Map.of();
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
}
