package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;

import java.util.Set;

/**
 * Buildings whose beds are for one night, never a home: an inn, an infirmary. MCA marks these
 * {@code noBeds}; a building type MCA does not mark can set {@code "lodging": true} in its
 * {@code extended_buildings} file.
 */
public final class BuildingLodging {
    private static volatile Set<String> FLAGGED = Set.of();

    private BuildingLodging() {}

    public static void replaceAll(Set<String> types) {
        FLAGGED = types == null ? Set.of() : Set.copyOf(types);
    }

    public static boolean isLodging(String typeName) {
        if (typeName == null || typeName.isBlank()) return false;
        if (FLAGGED.contains(typeName)) return true;
        BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(typeName);
        return type != null && type.noBeds();
    }

    /** Uses the building's effective type, so a guest room takes its inn's type. */
    public static boolean isLodging(Village village, Building building) {
        return building != null && isLodging(McaBuildingCompat.effectiveType(village, building));
    }
}
