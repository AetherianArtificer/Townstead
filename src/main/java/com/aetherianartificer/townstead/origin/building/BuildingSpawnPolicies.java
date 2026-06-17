package com.aetherianartificer.townstead.origin.building;

import java.util.Map;

/**
 * The loaded {@link BuildingSpawnPolicy} per MCA building-type id. Populated on datapack reload from
 * the {@code spawn} block of {@code extended_buildings} (and the legacy {@code building_spawn/} dir) by
 * {@code CatalogDataLoader}; read by the spawn-selection layer. {@code null} when a building type
 * declares no policy (then only the village-wide disposition filter applies).
 */
public final class BuildingSpawnPolicies {

    private static volatile Map<String, BuildingSpawnPolicy> BY_TYPE = Map.of();

    private BuildingSpawnPolicies() {}

    public static void replaceAll(Map<String, BuildingSpawnPolicy> next) {
        BY_TYPE = Map.copyOf(next);
    }

    /** The policy for a building type, or {@code null} if none is authored. */
    public static BuildingSpawnPolicy get(String buildingType) {
        return buildingType == null ? null : BY_TYPE.get(buildingType);
    }
}
