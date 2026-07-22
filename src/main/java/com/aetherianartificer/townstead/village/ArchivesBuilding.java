package com.aetherianartificer.townstead.village;

import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * The Archives building: the village's records office. Shared containment logic for every
 * feature the building anchors: chronicle access from its shelves, the career screen from
 * its sign, and vocation declarations made inside it.
 */
public final class ArchivesBuilding {

    public static final String TYPE = "archives";
    /** Stored buildings keep the type string they were detected under; accept dev-era names. */
    private static final java.util.Set<String> ACCEPTED_TYPES =
            java.util.Set.of(TYPE, "trades_hall", "career_center");

    private ArchivesBuilding() {}

    /**
     * The player's village, if {@code pos} lies inside one of its Archives buildings and the
     * player is within the village border.
     */
    public static Optional<Village> villageIfInside(ServerPlayer player, BlockPos pos) {
        Optional<Village> nearest = Village.findNearest(player);
        if (nearest.isEmpty() || !nearest.get().isWithinBorder(player)) return Optional.empty();
        boolean inside = nearest.get().getBuildings().values().stream()
                .anyMatch(building -> ACCEPTED_TYPES.contains(building.getType())
                        && contains(building.getPos0(), building.getPos1(), pos));
        return inside ? nearest : Optional.empty();
    }

    private static boolean contains(BlockPos cornerA, BlockPos cornerB, BlockPos pos) {
        return within(cornerA.getX(), cornerA.getY(), cornerA.getZ(),
                cornerB.getX(), cornerB.getY(), cornerB.getZ(),
                pos.getX(), pos.getY(), pos.getZ());
    }

    /** Inclusive box containment, tolerant of unordered corners. */
    static boolean within(int ax, int ay, int az, int bx, int by, int bz,
                          int px, int py, int pz) {
        return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
                && py >= Math.min(ay, by) && py <= Math.max(ay, by)
                && pz >= Math.min(az, bz) && pz <= Math.max(az, bz);
    }
}
