package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.dock.Dock;
import com.aetherianartificer.townstead.dock.DockBuildingSync;
import com.aetherianartificer.townstead.dock.DockScanner;
import com.aetherianartificer.townstead.dock.DockSuppression;
import com.aetherianartificer.townstead.enclosure.Enclosure;
import com.aetherianartificer.townstead.enclosure.EnclosureBuildingSync;
import com.aetherianartificer.townstead.enclosure.EnclosureClassifier;
import com.aetherianartificer.townstead.enclosure.EnclosureScanner;
import com.aetherianartificer.townstead.enclosure.EnclosureSuppression;
import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
import com.aetherianartificer.townstead.recognition.BuildingRecognitionTracker;
import com.aetherianartificer.townstead.spirit.SpiritReconciler;
import com.aetherianartificer.townstead.upgrade.BuildingTierReconciler;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
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
    @Inject(method = "handleServer", at = @At("HEAD"), cancellable = true, remap = false)
    //?} else {
    /*@Inject(method = "receive", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$interceptDockAction(ServerPlayer player, CallbackInfo ci) {
        //? if >=1.21 {
        ReportBuildingMessage self = (ReportBuildingMessage) (Object) this;
        ReportBuildingMessage.Action act = self.action();
        //?} else {
        /*ReportBuildingMessage.Action act = this.action;
        *///?}
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if (act == ReportBuildingMessage.Action.REMOVE) {
            VillageManager.get(level).findNearestVillage(player).ifPresent(v -> {
                Building dock = townstead$findDockAt(v, pos);
                if (dock != null) {
                    DockSuppression.suppress(level, v, dock);
                    return;
                }
                Building enclosure = townstead$findEnclosureAt(v, pos);
                if (enclosure != null) EnclosureSuppression.suppress(level, v, enclosure);
            });
            return;
        }

        if (act == ReportBuildingMessage.Action.ADD
                || act == ReportBuildingMessage.Action.ADD_ROOM
                || act == ReportBuildingMessage.Action.AUTO_SCAN) {
            // MCA's flood-fill validation fails on open-air structures and
            // shows "Building too small" before our TAIL hook runs. For
            // direct Add/Add Room clicks, if the player is on a dock or
            // inside a fenced enclosure, do our synthetic sync ourselves and
            // cancel so MCA never attempts flood-fill for this click. For
            // 1.20.1's AUTO_SCAN path, still sync open-air structures, but
            // let MCA continue so the auto-scan toggle/normal refresh works.
            Dock dock;
            try {
                dock = DockScanner.scanForReport(level, pos, TOWNSTEAD$REPORT_SCAN_RADIUS);
            } catch (Throwable t) {
                TOWNSTEAD$LOG.warn("Dock detection for ADD failed: {}", t.toString());
                return;
            }
            if (dock != null) {
                VillageManager.get(level).findNearestVillage(player).ifPresent(v ->
                        DockSuppression.clearAllOverlapping(level, v, dock.bounds()));
                DockBuildingSync.sync(level, dock, pos);
                VillageManager.get(level).findNearestVillage(player).ifPresent(v -> {
                    BuildingTierReconciler.reconcileVillage(v, level);
                    BuildingRecognitionTracker.reconcile(level, v);
                    SpiritReconciler.reconcileVillage(level, v);
                });
                if (act != ReportBuildingMessage.Action.AUTO_SCAN) ci.cancel();
                return;
            }

            Enclosure enclosure;
            EnclosureTypeIndex.Spec classified;
            try {
                enclosure = EnclosureScanner.scan(level, pos);
                classified = enclosure != null ? EnclosureClassifier.classify(enclosure) : null;
            } catch (Throwable t) {
                TOWNSTEAD$LOG.warn("Enclosure detection for ADD failed: {}", t.toString());
                return;
            }
            if (enclosure == null) return;
            if (classified == null) {
                TOWNSTEAD$LOG.info("Enclosure scanned at {} (interior={} fences={} gates={} walls={} content={}) but no registered type matched",
                        pos, enclosure.interiorSize(), enclosure.fenceCount(),
                        enclosure.fenceGateCount(), enclosure.wallCount(),
                        enclosure.interiorContent());
                return;
            }
            VillageManager.get(level).findNearestVillage(player).ifPresent(v ->
                    EnclosureSuppression.clearAllOverlapping(level, v, enclosure.bounds()));
            EnclosureBuildingSync.sync(level, enclosure, classified.buildingType());
            VillageManager.get(level).findNearestVillage(player).ifPresent(v -> {
                BuildingTierReconciler.reconcileVillage(v, level);
                BuildingRecognitionTracker.reconcile(level, v);
                SpiritReconciler.reconcileVillage(level, v);
            });
            if (act != ReportBuildingMessage.Action.AUTO_SCAN) ci.cancel();
        }
    }

    private static Building townstead$findEnclosureAt(Village village, BlockPos pos) {
        for (Building b : village.getBuildings().values()) {
            String t = b.getType();
            if (t == null || !EnclosureTypeIndex.isEnclosureType(t)) continue;
            if (b.containsPos(pos)) return b;
        }
        return null;
    }

    private static Building townstead$findDockAt(Village village, BlockPos pos) {
        for (Building b : village.getBuildings().values()) {
            String t = b.getType();
            if (t == null || !t.startsWith("dock_")) continue;
            if (b.containsPos(pos)) return b;
        }
        return null;
    }

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
            case ADD, ADD_ROOM, REMOVE, FULL_SCAN, AUTO_SCAN -> {
                ServerLevel level = player.serverLevel();
                VillageManager.get(level)
                        .findNearestVillage(player)
                        .ifPresent(v -> {
                            BuildingTierReconciler.reconcileVillage(v, level);
                            // Open-air dock detection happens before the
                            // recognition diff so fresh docks show up in the
                            // tracker's "current" snapshot and fire events
                            // alongside any MCA-side adds/upgrades. REMOVE
                            // is excluded so user dismissal sticks — the
                            // suppression HEAD hook records the bounds, and
                            // DockBuildingSync checks it before re-syncing.
                            if (act != ReportBuildingMessage.Action.REMOVE) {
                                townstead$detectAndSyncDockFromReport(level, player);
                            }
                            BuildingRecognitionTracker.reconcile(level, v);
                            SpiritReconciler.reconcileVillage(level, v);
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
            Dock dock = DockScanner.scanForReport(level, player.blockPosition(), TOWNSTEAD$REPORT_SCAN_RADIUS);
            if (dock != null) {
                DockBuildingSync.sync(level, dock, player.blockPosition());
            }
        } catch (Throwable t) {
            TOWNSTEAD$LOG.warn("Dock detection from report-building failed: {}", t.toString());
        }
    }
}
