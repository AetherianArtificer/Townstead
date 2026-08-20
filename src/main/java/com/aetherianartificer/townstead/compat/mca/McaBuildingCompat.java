package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
//? if >=1.21 {
import net.conczin.mca.server.world.data.RoomTypeResolver;
//?}
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single compatibility seam for MCA room geometry and derived type state.
 *
 * <p>Floor-system MCA owns exact room lookup, footprints, inherited POIs, and effective types.
 * Older MCA lines have no equivalent APIs, so their previous rectangular/direct-type behaviour is
 * retained here as an explicit fallback instead of leaking version checks through Townstead.</p>
 */
public final class McaBuildingCompat {
    private McaBuildingCompat() {}

    /** MCA's exact functional room at a position; external buildings are intentionally excluded. */
    public static @Nullable Building functionalRoomAt(
            ServerLevel level, Village village, BlockPos pos) {
        if (level == null || village == null || pos == null) return null;
        //? if >=1.21 {
        return village.getFunctionalRoomAt(level, pos).orElse(null);
        //?} else {
        /*return village.getBuildingAt(pos).orElse(null);
        *///?}
    }

    /** Exact functional room across villages, preserving MCA manager iteration order. */
    public static @Nullable Building functionalRoomAt(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        for (Village village : VillageManager.get(level)) {
            Building room = functionalRoomAt(level, village, pos);
            if (room != null) return room;
        }
        return null;
    }

    /** Exact room first, then MCA/Townstead external-site containment. */
    public static @Nullable Building buildingAt(
            ServerLevel level, Village village, BlockPos pos) {
        Building room = functionalRoomAt(level, village, pos);
        if (room != null) return room;
        for (Building building : McaBuildings.all(village)) {
            //? if >=1.21 {
            if (building.isFunctionalRoom()) continue;
            //?}
            if (McaBuildings.contains(level, village, building, pos)) return building;
        }
        return null;
    }

    /**
     * Whether a position belongs to this building according to MCA. Floor rooms use exact room
     * ownership; legacy rooms and MCA external buildings retain MCA's own containment method.
     */
    public static boolean contains(
            ServerLevel level, Village village, Building building, BlockPos pos) {
        if (building == null || pos == null) return false;
        //? if >=1.21 {
        if (building.isFunctionalRoom()) {
            Building exact = functionalRoomAt(level, village, pos);
            return exact != null && exact.getId() == building.getId();
        }
        //?}
        return McaBuildings.contains(level, village, building, pos);
    }

    /** Effective presentation/classification type, including MCA main-room inheritance. */
    public static @Nullable String effectiveType(Village village, Building building) {
        if (building == null) return null;
        //? if >=1.21 {
        BuildingType type = RoomTypeResolver.create(village).resolve(building).effectiveType();
        return type == null ? building.getType() : type.name();
        //?} else {
        /*return building.getType();
        *///?}
    }

    /** Resolve effective types once for callers iterating an entire village. */
    public static Map<Integer, String> effectiveTypes(Village village) {
        Map<Integer, String> result = new HashMap<>();
        if (village == null) return result;
        //? if >=1.21 {
        RoomTypeResolver resolver = RoomTypeResolver.create(village);
        for (Building building : McaBuildings.all(village)) {
            BuildingType type = resolver.resolve(building).effectiveType();
            String name = type == null ? building.getType() : type.name();
            if (name != null) result.put(building.getId(), name);
        }
        //?} else {
        /*for (Building building : McaBuildings.all(village)) {
            if (building.getType() != null) result.put(building.getId(), building.getType());
        }
        *///?}
        return result;
    }

    /** MCA-derived visible matches, normalized only for Townstead tier-family ambiguity. */
    public static List<String> matchingTypeNames(Village village, Building building) {
        if (building == null) return List.of();
        //? if >=1.21 {
        List<String> names = RoomTypeResolver.create(village).resolve(building)
                .visibleMatchingTypes().stream().map(BuildingType::name).toList();
        //?} else {
        /*List<String> names = building.getVisibleMatchingTypes().stream()
                .map(BuildingType::name).toList();
        *///?}
        return BuildingCandidatePolicy.normalizeNamesForRecognition(names);
    }

    /**
     * Exact MCA floor footprint plus MCA-recorded POIs. Empty means this MCA generation (or this
     * external building) has no exact floor model and callers should use their legacy fallback.
     */
    public static Set<Long> exactWorkArea(Building building) {
        if (building == null) return Set.of();
        //? if >=1.21 {
        if (!building.isFunctionalRoom() || building.getFloorRegions().isEmpty()) return Set.of();
        Set<Long> cells = new HashSet<>();
        building.getFloorRegions().forEach(region ->
                region.cells().forEach(pos -> cells.add(pos.asLong())));
        building.getBlockPosStream().forEach(pos -> cells.add(pos.asLong()));
        return Set.copyOf(cells);
        //?} else {
        /*return Set.of();
        *///?}
    }

    public static BlockPos reference(Building building) {
        if (building == null) return BlockPos.ZERO;
        BlockPos source = building.getSourceBlock();
        return source == null ? building.getCenter() : source;
    }
}
