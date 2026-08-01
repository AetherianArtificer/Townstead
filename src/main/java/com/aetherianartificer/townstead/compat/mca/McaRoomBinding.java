package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.work.site.WorksiteBindings;
import com.aetherianartificer.townstead.work.site.WorksiteKey;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Binds a worksite to an MCA room, so a kitchen or a smithy is found by the building it occupies.
 *
 * <p>Building ids are stable across rescans — MCA reconciles rooms rather than regenerating them,
 * keeps the existing id, and only ever increments its counter — and they are unique per world, since
 * that counter lives on the village manager rather than on a village. That makes the id a sound key;
 * the village is carried as refreshable metadata instead, because a building can change hands
 * between villages without becoming a different place.</p>
 *
 * <p>Every read goes through {@link McaBuildings}, never {@code Village.getBuildings()} — the call
 * that silently lost grouped types across 56 sites when MCA split its floor model. The register must
 * never learn which generation of MCA is installed.</p>
 */
public final class McaRoomBinding implements WorksiteBindings.Binding {

    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "mca_room");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "mca_room");
    *///?}

    private McaRoomBinding() {}

    public static void bootstrap() {
        WorksiteBindings.register(new McaRoomBinding());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    /**
     * A room outranks a block. A station standing inside a recognised building belongs to that
     * building, so a kitchen with three appliances is one place with one order list rather than
     * three — and the villager and the player's board resolve to the same record.
     */
    @Override
    public int priority() {
        return 100;
    }

    @Override
    public @Nullable WorksiteKey keyAt(ServerLevel level, BlockPos pos) {
        Building building = buildingAt(level, pos);
        return building == null
                ? null
                : new WorksiteKey(ID, level.dimension().location(), building.getId());
    }

    @Override
    public boolean stillExists(ServerLevel level, WorksiteKey key) {
        int buildingId = (int) key.value();
        for (Village village : VillageManager.get(level)) {
            if (McaBuildings.byId(village, buildingId) != null) return true;
        }
        return false;
    }

    @Override
    public java.util.Set<Long> extentOf(ServerLevel level, WorksiteKey key) {
        Building building = byId(level, key);
        return building == null
                ? java.util.Set.of()
                : com.aetherianartificer.townstead.work.WorkSiteBounds.workArea(level, building);
    }

    /** The building this key names, wherever it has ended up. */
    @Nullable
    public static Building byId(ServerLevel level, WorksiteKey key) {
        if (!ID.equals(key.binding())) return null;
        int buildingId = (int) key.value();
        for (Village village : VillageManager.get(level)) {
            Building building = McaBuildings.byId(village, buildingId);
            if (building != null) return building;
        }
        return null;
    }

    @Override
    public String defaultName(ServerLevel level, WorksiteKey key) {
        int buildingId = (int) key.value();
        for (Village village : VillageManager.get(level)) {
            Building building = McaBuildings.byId(village, buildingId);
            if (building == null) continue;
            // "kitchen_l3" is a type, not a name. A player renaming "The Kitchen" is editing
            // something that already reads like a place.
            String readable = com.aetherianartificer.townstead.work.site.WorksiteNames
                    .fromBuildingType(building.getType());
            if (!readable.isEmpty()) return readable;
        }
        return "";
    }

    /** The village this key's building belongs to, or {@code NO_VILLAGE}. Metadata, not identity. */
    public static int villageOf(ServerLevel level, WorksiteKey key) {
        if (!ID.equals(key.binding())) return com.aetherianartificer.townstead.work.site.Worksite.NO_VILLAGE;
        int buildingId = (int) key.value();
        for (Village village : VillageManager.get(level)) {
            if (McaBuildings.byId(village, buildingId) != null) return village.getId();
        }
        return com.aetherianartificer.townstead.work.site.Worksite.NO_VILLAGE;
    }

    @Nullable
    private static Building buildingAt(ServerLevel level, BlockPos pos) {
        for (Village village : VillageManager.get(level)) {
            for (Building building : McaBuildings.all(village)) {
                if (building.containsPos(pos)) return building;
            }
        }
        return null;
    }
}
