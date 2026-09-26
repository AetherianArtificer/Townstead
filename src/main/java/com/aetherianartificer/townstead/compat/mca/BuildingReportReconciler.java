package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.dock.DockLocationIndex;
import com.aetherianartificer.townstead.recognition.BuildingChecks;
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

import java.util.List;

public final class BuildingReportReconciler {
    private BuildingReportReconciler() {}

    public static void reconcileNearest(ServerPlayer player, Logger log) {
        ServerLevel level = player.serverLevel();
        VillageManager.get(level).findNearestVillage(player).ifPresent(village ->
                reconcile(level, player, village, log));
    }

    public static void reconcileNearest(ServerLevel level, BlockPos source, Logger log) {
        VillageManager.get(level).findNearestVillage(source, Village.MERGE_MARGIN).ifPresent(village ->
                reconcile(level, source, village, log));
    }

    private static final int NEAR_MISS_RANGE = 24;

    public static void reconcile(ServerLevel level, ServerPlayer player, Village village, Logger log) {
        // The reporting player is who a resulting deed (a building raised, a spirit tier reached) belongs to.
        List<BuildingChecks.Failure> failures = new java.util.ArrayList<>();
        com.aetherianartificer.townstead.compat.otectus.ReputationDeeds.withReporter(player,
                () -> failures.addAll(reconcile(level, player.blockPosition(), village, log)));
        // Tell the reporter why a room nearby is not the building its furniture suggests.
        for (BuildingChecks.Failure failure : failures) {
            if (failure.center().closerThan(player.blockPosition(), NEAR_MISS_RANGE)) {
                player.displayClientMessage(failure.message(), false);
            }
        }
    }

    public static List<BuildingChecks.Failure> reconcile(ServerLevel level, BlockPos source, Village village, Logger log) {
        com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap.ensure(level, village);
        List<BuildingChecks.Failure> failures = BuildingTierReconciler.reconcileVillage(village, level);
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
        com.aetherianartificer.townstead.profession.ProfessionSites.invalidate(level);
        // A rescan is also the moment we can tell a demolished worksite from an unloaded one, so
        // it is where retired places leave the register instead of accumulating for the life of
        // the world. Ids are never reused, so a pruned site can only be replaced by a new one.
        com.aetherianartificer.townstead.work.site.WorksiteRegister
                .get(level.getServer()).prune(level);
        return failures;
    }
}
