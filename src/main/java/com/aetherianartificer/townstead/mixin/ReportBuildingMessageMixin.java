package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.dock.Dock;
import com.aetherianartificer.townstead.dock.DockBuildingSync;
import com.aetherianartificer.townstead.dock.DockScanner;
import com.aetherianartificer.townstead.recognition.BuildingRecognitionTracker;
import com.aetherianartificer.townstead.upgrade.BuildingTierReconciler;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReportBuildingMessage.class)
public abstract class ReportBuildingMessageMixin {
    private static final Logger TOWNSTEAD$LOG = LoggerFactory.getLogger("Townstead/ReportBuildingMessageMixin");

    //? if <1.21 {
    /*@Shadow(remap = false)
    private ReportBuildingMessage.Action action;
    *///?}

    //? if >=1.21 {
    @Inject(method = "handleServer", at = @At("TAIL"), remap = false)
    //?} else {
    /*@Inject(method = "receive", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$reconcileTieredBuildingsAfterBuildingAction(ServerPlayer player, CallbackInfo ci) {
        //? if >=1.21 {
        ReportBuildingMessage self = (ReportBuildingMessage) (Object) this;
        ReportBuildingMessage.Action act = self.action();
        //?} else {
        /*ReportBuildingMessage.Action act = this.action;
        *///?}
        switch (act) {
            case ADD, ADD_ROOM, REMOVE, FULL_SCAN -> {
                ServerLevel level = player.serverLevel();
                VillageManager.get(level)
                        .findNearestVillage(player)
                        .ifPresent(v -> {
                            BuildingTierReconciler.reconcileVillage(v, level);
                            // Open-air dock detection happens before the
                            // recognition diff so fresh docks show up in the
                            // tracker's "current" snapshot and fire events
                            // alongside any MCA-side adds/upgrades.
                            townstead$detectAndSyncDockFromReport(level, player);
                            BuildingRecognitionTracker.reconcile(level, v);
                        });
            }
            default -> {
            }
        }
    }

    // Larger than the fisherman's default scan radius because the player may
    // trigger a report from any corner of a sizable deck. 24 covers a ~48-
    // block footprint, well past a max-practical Wharf. Partial scans produce
    // an undersized plank component and false-downgrade the tier.
    private static final int TOWNSTEAD$REPORT_SCAN_RADIUS = 24;

    private static void townstead$detectAndSyncDockFromReport(ServerLevel level, ServerPlayer player) {
        try {
            Dock dock = DockScanner.scan(level, player.blockPosition(), TOWNSTEAD$REPORT_SCAN_RADIUS);
            if (dock != null) {
                DockBuildingSync.sync(level, dock);
            }
        } catch (Throwable t) {
            TOWNSTEAD$LOG.warn("Dock detection from report-building failed: {}", t.toString());
        }
    }
}
