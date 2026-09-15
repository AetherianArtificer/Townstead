package com.aetherianartificer.townstead.fatigue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

/** Safe release boundary for Townstead-owned temporary bed POI tickets. */
public final class EmergencyBedReservation {
    private EmergencyBedReservation() {}

    /**
     * Release the ticket only if the bed POI still exists and remains occupied.
     * Removing a bed drops its POI, while interrupted MCA logic may already have
     * returned tickets created by older Townstead versions.
     */
    public static void releaseIfClaimed(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return;
        PoiManager poiManager = level.getPoiManager();
        boolean isRegisteredHome = poiManager.getType(pos)
                .map(holder -> holder.is(PoiTypes.HOME))
                .orElse(false);
        if (!isRegisteredHome || poiManager.getFreeTickets(pos) > 0) return;
        poiManager.release(pos);
    }
}
