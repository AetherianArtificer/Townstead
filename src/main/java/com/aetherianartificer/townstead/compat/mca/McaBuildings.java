package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.mixin.accessor.VillageBuildingMapsAccessor;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCA's floor-system v2 splits grouped/external village sites (graveyards and Townstead's
 * synthetic landings and pens, but not ordinary kitchen Rooms) out of {@code Village.getBuildings()} into a separate
 * external-buildings map, so every direct {@code getBuildings()} consumer silently loses them.
 * This seam presents the pre-v2 unified view on every MCA version. Capability is probed from the
 * installed MCA (never the Minecraft version): when 1.20.1's MCA gains v2 later, this path
 * simply activates there too.
 */
public final class McaBuildings {

    private static final @Nullable Method GET_EXTERNAL_MAP;
    private static final @Nullable Constructor<?> EXTERNAL_NBT_CTOR;

    static {
        Method externalMap = null;
        Constructor<?> externalCtor = null;
        try {
            externalMap = Village.class.getMethod("getExternalBuildingMap");
            Class<?> externalClass = Class.forName("net.conczin.mca.server.world.data.ExternalBuilding");
            externalCtor = externalClass.getConstructor(CompoundTag.class);
            Townstead.LOGGER.debug("[McaBuildings] floor-system v2 detected");
        } catch (ReflectiveOperationException ignored) {
            // Pre-v2 MCA: one buildings map, no external split.
        }
        GET_EXTERNAL_MAP = externalMap;
        EXTERNAL_NBT_CTOR = externalCtor;
    }

    private McaBuildings() {}

    public static boolean hasExternalSplit() {
        return GET_EXTERNAL_MAP != null;
    }

    /**
     * Removes a building by id from whichever map owns it. The floor system keeps rooms and
     * external buildings in separate maps with separate removal calls; a room that is a
     * structure's main room is refused by MCA and reported as {@code false}.
     */
    public static boolean remove(Village village, int id) {
        if (village == null) return false;
        //? if >=1.21 {
        if (village.getExternalBuildingMap().containsKey(id)) return village.removeExternalBuilding(id);
        boolean removed = village.removeRoom(id);
        if (!removed && village.getBuildings().containsKey(id)) {
            Townstead.LOGGER.debug("[McaBuildings] MCA refused to remove room {} (main room)", id);
        }
        return removed;
        //?} else {
        /*boolean present = village.getBuildings().containsKey(id);
        village.removeBuilding(id);
        return present;
        *///?}
    }

    /** Footprint size in floor cells; the pre-floor generation stored the same count as {@code size}. */
    public static int size(Building building) {
        if (building == null) return 0;
        //? if >=1.21 {
        return (int) Math.min(Integer.MAX_VALUE, building.getFloorFootprintArea());
        //?} else {
        /*return building.getSize();
        *///?}
    }

    /** Every building of the village: functional rooms plus, on v2, external grouped sites. */
    public static Collection<Building> all(Village village) {
        Map<Integer, Building> rooms = village.getBuildings();
        Map<Integer, Building> external = externalMap(village);
        if (external == null || external.isEmpty()) return rooms.values();
        Collection<Building> out = new ArrayList<>(rooms.size() + external.size());
        out.addAll(rooms.values());
        out.addAll(external.values());
        return out;
    }

    /**
     * Read-only id-to-building view across both maps. Pre-v2 this is the live map; on v2 it is
     * a merged copy — never mutate through it (writers use {@link #putSynthetic}).
     */
    public static Map<Integer, Building> allById(Village village) {
        Map<Integer, Building> rooms = village.getBuildings();
        Map<Integer, Building> external = externalMap(village);
        if (external == null || external.isEmpty()) return rooms;
        Map<Integer, Building> out = new LinkedHashMap<>(rooms);
        out.putAll(external);
        return out;
    }

    /**
     * Whether this record is the open-air form of a building rather than an MCA room. On v2 that
     * is membership of the external map; before the split, Townstead's overlay marks it.
     */
    public static boolean isOpenAirRecord(ServerLevel level, Village village, Building building) {
        if (village == null || building == null) return false;
        Map<Integer, Building> external = externalMap(village);
        if (external != null) return external.containsKey(building.getId());
        // Before the external split there is one map, so the type's own policy is the only signal.
        return com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies
                .modeOf(building.getType()).allowsOpenAir();
    }

    public static @Nullable Building byId(Village village, int id) {
        Building room = village.getBuildings().get(id);
        if (room != null) return room;
        Map<Integer, Building> external = externalMap(village);
        return external == null ? null : external.get(id);
    }

    /**
     * Containment for an open-air site. MCA's own {@code containsPos} reduces an external
     * building to a sphere around its centre, which is wrong for anything long: most of a wharf
     * falls outside it. Its recorded footprint is the honest answer, grown a little vertically so
     * standing on the deck counts.
     */
    public static boolean contains(
            ServerLevel level, Village village, Building building, BlockPos pos) {
        if (village == null || building == null || pos == null) return false;
        if (!com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies
                .modeOf(building.getType()).allowsOpenAir()) {
            return building.containsPos(pos);
        }
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() - 1 && pos.getY() <= max.getY() + 2
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * Registers a Townstead-synthesized building (a landing or a pen) from its NBT. On v2 these
     * become {@code ExternalBuilding}s in the external map — they are open-air grouped sites,
     * and the rooms map now carries Structure/floor invariants they cannot satisfy. Removal
     * stays {@code Village.removeBuilding}, which clears both maps on v2.
     */
    public static @Nullable Building putSynthetic(Village village, int id, CompoundTag nbt) {
        try {
            if (EXTERNAL_NBT_CTOR != null) {
                Building external = (Building) EXTERNAL_NBT_CTOR.newInstance(nbt);
                Map<Integer, Building> map = externalMap(village);
                if (map != null) {
                    // Old worlds saved synthetics in the rooms list and v2's migrator could turn
                    // them into Rooms. Remove their migrated Structure as well as the room; merely
                    // deleting the map entry leaves an orphan Structure/logical-building behind.
                    VillageBuildingMapsAccessor maps = (VillageBuildingMapsAccessor) (Object) village;
                    Building staleRoom = village.getBuildings().get(id);
                    //? if >=1.21 {
                    if (staleRoom != null && village.getStructure(staleRoom.getStructureId()).isPresent()) {
                        village.removeStructure(staleRoom.getStructureId());
                    } else {
                        maps.townstead$getBuildingMap().remove(id);
                    }
                    //?} else {
                    /*maps.townstead$getBuildingMap().remove(id);
                    *///?}
                    //? if >=1.21 {
                    maps.townstead$getExternalBuildingMap().put(id, external);
                    //?} else {
                    /*map.put(id, external);
                    *///?}
                    return external;
                }
            }
            Building building = new Building(nbt);
            ((VillageBuildingMapsAccessor) (Object) village).townstead$getBuildingMap().put(id, building);
            return building;
        } catch (ReflectiveOperationException e) {
            Townstead.LOGGER.warn("[McaBuildings] failed to register synthetic building {}: {}", id, e.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<Integer, Building> externalMap(Village village) {
        if (GET_EXTERNAL_MAP == null) return null;
        try {
            return (Map<Integer, Building>) GET_EXTERNAL_MAP.invoke(village);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
