package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.dock.Dock;
import com.aetherianartificer.townstead.dock.DockBuildingSync;
import com.aetherianartificer.townstead.dock.DockLocationIndex;
import com.aetherianartificer.townstead.dock.DockScanner;
import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
import com.aetherianartificer.townstead.recognition.BuildingRecognitionTracker;
import com.aetherianartificer.townstead.spirit.SpiritReconciler;
import com.aetherianartificer.townstead.upgrade.BuildingTierReconciler;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class BuildingReportReconciler {
    private static final int REPORT_SCAN_RADIUS = 24;

    private BuildingReportReconciler() {}

    public static void reconcileNearest(ServerPlayer player, boolean detectDock, Logger log) {
        ServerLevel level = player.serverLevel();
        VillageManager.get(level).findNearestVillage(player).ifPresent(village ->
                reconcile(level, player, village, detectDock, log));
    }

    public static void reconcileNearest(ServerLevel level, BlockPos source, boolean detectDock, Logger log) {
        VillageManager.get(level).findNearestVillage(source, Village.MERGE_MARGIN).ifPresent(village ->
                reconcile(level, source, village, detectDock, log));
    }

    public static void reconcile(ServerLevel level, ServerPlayer player, Village village, boolean detectDock, Logger log) {
        reconcile(level, player.blockPosition(), village, detectDock, log);
    }

    public static void reconcile(ServerLevel level, BlockPos source, Village village, boolean detectDock, Logger log) {
        BuildingTierReconciler.reconcileVillage(village, level);
        if (detectDock) {
            detectAndSyncDockFromReport(level, source, village, log);
        }
        DockLocationIndex.rebuildVillage(level, village);
        BuildingRecognitionTracker.reconcile(level, village);
        SpiritReconciler.reconcileVillage(level, village);
        // Floor-system v2 never validates external buildings, so demolished landings/pens
        // are cleaned up here, at report time with the village's chunks loaded.
        com.aetherianartificer.townstead.village.VillageSanitizer.sweepDemolishedSynthetics(level, village);
        // A rescan is exactly when a room's shape may have changed, so it is where a worksite's
        // remembered extent stops being trustworthy. Without this the only correction is the
        // freshness backstop, which is a bound on being wrong rather than a way of being right.
        for (Building building : McaBuildings.all(village)) {
            com.aetherianartificer.townstead.work.site.Worksites.invalidateExtent(level, building);
        }
        // A rescan is also the moment we can tell a demolished worksite from an unloaded one, so
        // it is where retired places leave the register instead of accumulating for the life of
        // the world. Ids are never reused, so a pruned site can only be replaced by a new one.
        com.aetherianartificer.townstead.work.site.WorksiteRegister
                .get(level.getServer()).prune(level);
    }

    private static void detectAndSyncDockFromReport(ServerLevel level, BlockPos source, Village village, Logger log) {
        try {
            if (insideEnclosedBuilding(level, village, source)) return;
            Dock dock = DockScanner.scanForReport(level, source, REPORT_SCAN_RADIUS);
            if (dock != null) {
                DockBuildingSync.sync(level, dock, source);
            }
        } catch (Throwable t) {
            if (log != null) {
                log.warn("Dock detection from report-building failed: {}", t.toString());
            }
        }
    }

    public static boolean insideEnclosedBuilding(ServerLevel level, Village village, BlockPos pos) {
        for (Building b : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            String t = b.getType();
            if (t == null) continue;
            if (t.startsWith("dock_")) continue;
            if (EnclosureTypeIndex.isEnclosureType(t)) continue;
            if (McaBuildingCompat.contains(level, village, b, pos)) return true;
        }
        return false;
    }
}
