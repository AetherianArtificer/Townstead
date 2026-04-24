package com.aetherianartificer.townstead.enclosure;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers enclosure bounds the player has dismissed via "Remove Building"
 * so subsequent ADD/refresh cycles don't immediately re-inject the same
 * synthetic Building from the still-present fences and signature blocks.
 * Parallel to {@code DockSuppression}; in-memory per level, not persisted.
 */
public final class EnclosureSuppression {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/EnclosureSuppression");
    private static final Map<String, List<int[]>> SUPPRESSED = new ConcurrentHashMap<>();

    private EnclosureSuppression() {}

    public static void suppress(ServerLevel level, Village village, Building building) {
        if (level == null || village == null || building == null) return;
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        int[] bb = {p0.getX(), p0.getY(), p0.getZ(), p1.getX(), p1.getY(), p1.getZ()};
        SUPPRESSED.computeIfAbsent(keyOf(level, village), k -> new ArrayList<>()).add(bb);
        LOG.info("[EnclosureSuppress] village={} suppressed [{},{},{}]..[{},{},{}]",
                village.getId(), bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]);
    }

    public static boolean isSuppressed(ServerLevel level, Village village, BoundingBox detected) {
        if (level == null || village == null || detected == null) return false;
        List<int[]> list = SUPPRESSED.get(keyOf(level, village));
        if (list == null || list.isEmpty()) return false;
        for (int[] bb : list) {
            if (bb[0] == detected.minX() && bb[1] == detected.minY() && bb[2] == detected.minZ()
                    && bb[3] == detected.maxX() && bb[4] == detected.maxY() && bb[5] == detected.maxZ()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drop suppressions whose bounds overlap the detected enclosure but don't
     * match exactly — the shape changed, so the prior dismissal doesn't apply
     * to the new footprint.
     */
    public static void clearOverlapping(ServerLevel level, Village village, BoundingBox detected) {
        if (level == null || village == null || detected == null) return;
        List<int[]> list = SUPPRESSED.get(keyOf(level, village));
        if (list == null || list.isEmpty()) return;
        Iterator<int[]> it = list.iterator();
        while (it.hasNext()) {
            int[] bb = it.next();
            boolean exact = bb[0] == detected.minX() && bb[1] == detected.minY() && bb[2] == detected.minZ()
                    && bb[3] == detected.maxX() && bb[4] == detected.maxY() && bb[5] == detected.maxZ();
            boolean overlaps = bb[0] <= detected.maxX() && bb[3] >= detected.minX()
                    && bb[1] <= detected.maxY() && bb[4] >= detected.minY()
                    && bb[2] <= detected.maxZ() && bb[5] >= detected.minZ();
            if (overlaps && !exact) it.remove();
        }
    }

    /**
     * Drop any suppressions overlapping the detected bounds — called on
     * explicit player ADD so a re-add overrides a prior remove.
     */
    public static void clearAllOverlapping(ServerLevel level, Village village, BoundingBox detected) {
        if (level == null || village == null || detected == null) return;
        List<int[]> list = SUPPRESSED.get(keyOf(level, village));
        if (list == null || list.isEmpty()) return;
        Iterator<int[]> it = list.iterator();
        while (it.hasNext()) {
            int[] bb = it.next();
            boolean overlaps = bb[0] <= detected.maxX() && bb[3] >= detected.minX()
                    && bb[1] <= detected.maxY() && bb[4] >= detected.minY()
                    && bb[2] <= detected.maxZ() && bb[5] >= detected.minZ();
            if (overlaps) it.remove();
        }
    }

    private static String keyOf(ServerLevel level, Village village) {
        return level.dimension().location().toString() + "|" + village.getId();
    }
}
