package com.aetherianartificer.townstead.village;

import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * The town range: a village's building box grown by a margin on every side. It is a planning
 * boundary, not territory. The margin starts at MCA's border margin and is the value that grows
 * as a town gets stronger.
 */
public final class TownRange {
    private TownRange() {}

    public static int margin(ServerLevel level, Village village) {
        return Village.BORDER_MARGIN;
    }

    public static boolean contains(ServerLevel level, Village village, BlockPos pos) {
        return village.isWithinBorder(pos, margin(level, village));
    }

    /** The village whose town range contains {@code pos}; the closest center wins when ranges overlap. */
    public static Optional<Village> at(ServerLevel level, BlockPos pos) {
        Village best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Village village : VillageManager.get(level)) {
            if (!contains(level, village, pos)) continue;
            double distance = pos.distSqr(village.getCenter());
            if (distance < bestDistance) {
                best = village;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
