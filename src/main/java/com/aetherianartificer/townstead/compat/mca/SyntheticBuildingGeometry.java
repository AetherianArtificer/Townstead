package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.village.TownsteadVillageSavedData;
import net.minecraft.core.BlockPos;

/** Exact containment policy for Townstead-owned external-building overlays. */
public final class SyntheticBuildingGeometry {
    private SyntheticBuildingGeometry() {}

    public static boolean contains(TownsteadVillageSavedData.BuildingOverlay overlay, BlockPos pos) {
        if (overlay == null || overlay.bounds().length != 6 || pos == null) return false;
        if ("dock".equals(overlay.kind())) {
            for (long[] positions : overlay.blockPositions().values()) {
                for (long packed : positions) {
                    BlockPos surface = BlockPos.of(packed);
                    if (surface.getX() == pos.getX() && surface.getZ() == pos.getZ()
                            && pos.getY() >= surface.getY() && pos.getY() <= surface.getY() + 2) {
                        return true;
                    }
                }
            }
            return false;
        }

        int[] bounds = overlay.bounds();
        return pos.getX() >= bounds[0] && pos.getX() <= bounds[3]
                && pos.getY() >= bounds[1] - 1 && pos.getY() <= bounds[4] + 2
                && pos.getZ() >= bounds[2] && pos.getZ() <= bounds[5];
    }
}
