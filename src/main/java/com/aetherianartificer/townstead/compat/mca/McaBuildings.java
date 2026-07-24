package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCA's floor-system v2 splits grouped village sites (kitchens, cafes, graveyards, Townstead's
 * synthetic landings and pens) out of {@code Village.getBuildings()} into a separate
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

    public static @Nullable Building byId(Village village, int id) {
        Building room = village.getBuildings().get(id);
        if (room != null) return room;
        Map<Integer, Building> external = externalMap(village);
        return external == null ? null : external.get(id);
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
                    // Old worlds saved synthetics in the rooms list and v2's migrator turned
                    // them into Rooms; evict the stale copy so the id resolves to the external.
                    village.getBuildings().remove(id);
                    map.put(id, external);
                    return external;
                }
            }
            Building building = new Building(nbt);
            village.getBuildings().put(id, building);
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
