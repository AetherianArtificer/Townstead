package com.aetherianartificer.townstead.dock;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One-shot cleanup for saves corrupted by the earlier grouped-docks bug, where
 * MCA's grouped-detection path auto-created a {@code dock_*} Building in
 * parallel with {@link DockBuildingSync} on every refresh — runaway duplicate
 * buildings accumulated at the same plank footprint until the game crashed.
 *
 * <p>Groups overlapping {@code dock_*} Buildings by intersecting bounds and
 * keeps one representative per group, preferring an ID produced by
 * {@link DockBuildingSync#synthIdFor} (identified by the Integer.MIN_VALUE
 * sign bit). Removes the rest. Runs once per level on server start.
 */
public final class DockDuplicatePurger {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/DockDuplicatePurger");

    private DockDuplicatePurger() {}

    public static void purgeAll(Village village) {
        Map<Integer, Building> all = village.getBuildings();
        List<Map.Entry<Integer, Building>> docks = new ArrayList<>();
        for (Map.Entry<Integer, Building> e : all.entrySet()) {
            Building b = e.getValue();
            String t = b.getType();
            if (t != null && t.startsWith("dock_")) docks.add(e);
        }
        if (docks.size() < 2) return;

        List<List<Map.Entry<Integer, Building>>> groups = new ArrayList<>();
        for (Map.Entry<Integer, Building> entry : docks) {
            List<Map.Entry<Integer, Building>> match = null;
            for (List<Map.Entry<Integer, Building>> group : groups) {
                for (Map.Entry<Integer, Building> member : group) {
                    if (intersects(member.getValue(), entry.getValue())) {
                        match = group;
                        break;
                    }
                }
                if (match != null) break;
            }
            if (match == null) {
                List<Map.Entry<Integer, Building>> fresh = new ArrayList<>();
                fresh.add(entry);
                groups.add(fresh);
            } else {
                match.add(entry);
            }
        }

        int totalRemoved = 0;
        for (List<Map.Entry<Integer, Building>> group : groups) {
            if (group.size() < 2) continue;
            int keeperId = pickKeeper(group);
            for (Map.Entry<Integer, Building> e : group) {
                if (e.getKey() == keeperId) continue;
                village.removeBuilding(e.getKey());
                totalRemoved++;
            }
            Building keeper = all.get(keeperId);
            LOG.warn("[DockPurge] village={} dock group of {} duplicates at [{},{},{}]..[{},{},{}]; kept id={} ({})",
                    village.getId(), group.size(),
                    keeper.getPos0().getX(), keeper.getPos0().getY(), keeper.getPos0().getZ(),
                    keeper.getPos1().getX(), keeper.getPos1().getY(), keeper.getPos1().getZ(),
                    keeperId, keeper.getType());
        }
        if (totalRemoved > 0) {
            village.calculateDimensions();
            village.markDirty();
            LOG.warn("[DockPurge] village={} removed {} duplicate dock buildings", village.getId(), totalRemoved);
        }
    }

    /**
     * Prefer a synthetic ID from {@link DockBuildingSync#synthIdFor} (sign bit
     * set) so future syncs update in place. Otherwise keep the lowest ID.
     */
    private static int pickKeeper(List<Map.Entry<Integer, Building>> group) {
        int fallback = Integer.MAX_VALUE;
        for (Map.Entry<Integer, Building> e : group) {
            int id = e.getKey();
            if ((id & Integer.MIN_VALUE) != 0) return id;
            if (id < fallback) fallback = id;
        }
        return fallback;
    }

    private static boolean intersects(Building a, Building b) {
        BlockPos a0 = a.getPos0();
        BlockPos a1 = a.getPos1();
        BlockPos b0 = b.getPos0();
        BlockPos b1 = b.getPos1();
        return a0.getX() <= b1.getX() && a1.getX() >= b0.getX()
                && a0.getY() <= b1.getY() && a1.getY() >= b0.getY()
                && a0.getZ() <= b1.getZ() && a1.getZ() >= b0.getZ();
    }
}
