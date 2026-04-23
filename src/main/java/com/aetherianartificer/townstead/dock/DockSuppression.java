package com.aetherianartificer.townstead.dock;

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
 * Remembers dock bounds the player has explicitly dismissed via the
 * blueprint's "Remove Building" action, so {@link DockBuildingSync} doesn't
 * immediately re-create them from the still-present plank structure.
 *
 * <p>A suppression entry is cleared when the detected dock's plank footprint
 * changes relative to the suppressed bounds (player added or removed planks,
 * or the tier shifted). That lets the player change their mind by extending
 * the dock: next scan will sync again.
 *
 * <p>In-memory per-level. Not persisted across restarts — restart brings
 * pre-existing docks back. If that turns out to matter, promote to SavedData.
 */
public final class DockSuppression {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/DockSuppression");

    // Key: "<dim>|<villageId>". Value: list of suppressed bounds.
    private static final Map<String, List<int[]>> SUPPRESSED = new ConcurrentHashMap<>();

    private DockSuppression() {}

    public static void suppress(ServerLevel level, Village village, Building dock) {
        if (level == null || village == null || dock == null) return;
        BlockPos p0 = dock.getPos0();
        BlockPos p1 = dock.getPos1();
        int[] bb = {p0.getX(), p0.getY(), p0.getZ(), p1.getX(), p1.getY(), p1.getZ()};
        SUPPRESSED.computeIfAbsent(keyOf(level, village), k -> new ArrayList<>()).add(bb);
        LOG.info("[DockSuppress] village={} suppressed dock bounds [{},{},{}]..[{},{},{}]",
                village.getId(), bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]);
    }

    /**
     * Is this detected dock's footprint suppressed? Returns true iff the
     * bounds exactly match an entry (same min and max corners) — any change
     * in the plank shape lifts the suppression.
     */
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
     * Clear all suppression entries whose bounds overlap the detected dock.
     * Called on an explicit player ADD: their intent to re-register the
     * dock overrides any prior remove.
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
            if (overlaps) {
                it.remove();
                LOG.info("[DockSuppress] village={} cleared suppression on explicit re-add [{},{},{}]..[{},{},{}]",
                        village.getId(), bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]);
            }
        }
    }

    /**
     * Clear any suppression whose bounds overlap the detected dock but don't
     * match exactly. Called when a dock is re-detected after its shape
     * changed — the new footprint is legitimately different, so the old
     * suppression no longer applies.
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
            if (overlaps && !exact) {
                it.remove();
                LOG.info("[DockSuppress] village={} cleared stale suppression [{},{},{}]..[{},{},{}] (shape changed)",
                        village.getId(), bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]);
            }
        }
    }

    private static String keyOf(ServerLevel level, Village village) {
        return level.dimension().location().toString() + "|" + village.getId();
    }
}
