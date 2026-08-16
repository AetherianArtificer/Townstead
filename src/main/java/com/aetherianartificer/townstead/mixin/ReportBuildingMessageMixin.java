package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.dock.Dock;
import com.aetherianartificer.townstead.dock.DockBuildingSync;
import com.aetherianartificer.townstead.dock.DockLocationIndex;
import com.aetherianartificer.townstead.dock.DockScanner;
import com.aetherianartificer.townstead.dock.DockSuppression;
import com.aetherianartificer.townstead.enclosure.Enclosure;
import com.aetherianartificer.townstead.enclosure.EnclosureBuildingSync;
import com.aetherianartificer.townstead.enclosure.EnclosureClassifier;
import com.aetherianartificer.townstead.enclosure.EnclosureScanner;
import com.aetherianartificer.townstead.enclosure.EnclosureSuppression;
import com.aetherianartificer.townstead.enclosure.EnclosureTypeIndex;
import com.aetherianartificer.townstead.compat.mca.McaFloorCompat;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.recognition.BuildingRecognitionTracker;
import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import com.aetherianartificer.townstead.recognition.OptionalBuildingRecognition;
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

import java.util.Optional;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//? if <1.21 {
/*import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReportBuildingMessage.class)
public abstract class ReportBuildingMessageMixin {
    private static final Logger TOWNSTEAD$LOG = LoggerFactory.getLogger("Townstead/ReportBuildingMessageMixin");

    // MCA reshapes this enum between floor-system generations: ADD was split
    // into ADD_BUILDING (open ground) and ADD_ROOM (inside an existing
    // structure), and ADD_FLOOR/ADD_BASEMENT were added. Referencing the
    // constants directly throws NoSuchFieldError on whichever generation
    // dropped one, so match on the name instead and stay generation-agnostic.
    private static final Set<String> TOWNSTEAD$REMOVE_ACTIONS = Set.of("REMOVE", "REMOVE_ROOM");
    private static final String TOWNSTEAD$AUTO_SCAN = "AUTO_SCAN";

    /** Actions where the player may be standing on an open-air dock or in a pen. */
    private static final Set<String> TOWNSTEAD$SYNTHETIC_SCAN_ACTIONS =
            Set.of("ADD", "ADD_BUILDING", "ADD_ROOM", "AUTO_SCAN");

    /**
     * Actions that can change what buildings a village has. ADD_FLOOR and
     * ADD_BASEMENT attach to a structure the player is already inside, so they
     * reconcile but never take the synthetic path above.
     */
    private static final Set<String> TOWNSTEAD$RECONCILE_ACTIONS =
            Set.of("ADD", "ADD_BUILDING", "ADD_ROOM", "ADD_FLOOR", "ADD_BASEMENT",
                    "REMOVE", "REMOVE_ROOM", "FULL_SCAN", "AUTO_SCAN");

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
        String actName = act.name();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if ("FORCE_TYPE".equals(actName)) {
            String requestedType = townstead$reportData();
            if (CatalogDataLoader.isActiveSupersededBuildingType(requestedType)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "blueprint.scan.invalid_type"), true);
                ci.cancel();
                return;
            }
        }

        if (TOWNSTEAD$REMOVE_ACTIONS.contains(actName)) {
            VillageManager.get(level).findNearestVillage(player).ifPresent(v -> {
                Building dock = townstead$findDockAt(v, pos);
                if (dock != null) {
                    DockSuppression.suppress(level, v, dock);
                    return;
                }
                Building enclosure = townstead$findEnclosureAt(v, pos);
                if (enclosure != null) EnclosureSuppression.suppress(level, v, enclosure);
            });
            Optional<OptionalBuildingRecognition.Removed> removed =
                    OptionalBuildingRecognition.remove(level, pos);
            TOWNSTEAD$LOG.info("Optional building removal action={} pos={} result={}",
                    actName, pos, removed.map(value -> Integer.toString(value.buildingId())).orElse("none"));
            if (removed.isPresent()) {
                Village village = removed.get().village();
                BuildingTierReconciler.reconcileVillage(village, level);
                DockLocationIndex.rebuildVillage(level, village);
                BuildingRecognitionTracker.reconcile(level, village);
                SpiritReconciler.reconcileVillage(level, village);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "blueprint.buildingRemoved"), true);
                McaFloorCompat.pushVillageResponse(player);
                ci.cancel();
            }
            return;
        }

        if (TOWNSTEAD$SYNTHETIC_SCAN_ACTIONS.contains(actName)) {
            Optional<OptionalBuildingRecognition.Candidate> optionalCandidate =
                    OptionalBuildingRecognition.find(level, pos);
            if (optionalCandidate.isPresent()) {
                OptionalBuildingRecognition.Candidate candidate = optionalCandidate.get();
                BuildingEnclosurePolicies.Mode mode = BuildingEnclosurePolicies.modeOf(candidate.typeName());
                VillageManager manager = VillageManager.get(level);
                boolean roomWillHandle = mode.allowsRoom()
                        && OptionalBuildingRecognition.roomCanHandle(manager, pos, actName);
                if (!roomWillHandle) {
                    OptionalBuildingRecognition.Registration registration =
                            OptionalBuildingRecognition.register(level, candidate);
                    if (registration != OptionalBuildingRecognition.Registration.FAILED) {
                        manager.findNearestVillage(player).ifPresent(v -> {
                            BuildingTierReconciler.reconcileVillage(v, level);
                            DockLocationIndex.rebuildVillage(level, v);
                            BuildingRecognitionTracker.reconcile(level, v);
                            SpiritReconciler.reconcileVillage(level, v);
                        });
                        if (!TOWNSTEAD$AUTO_SCAN.equals(actName)) {
                            String message = registration == OptionalBuildingRecognition.Registration.CREATED
                                    ? "blueprint.buildingAdded" : "blueprint.scan.identical";
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(message), true);
                            McaFloorCompat.pushVillageResponse(player);
                            ci.cancel();
                        }
                        return;
                    }
                }
            }

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
                Optional<Village> villageOpt = VillageManager.get(level).findNearestVillage(player);
                boolean insideHouse = villageOpt
                        .map(v -> townstead$insideEnclosedBuilding(v, pos))
                        .orElse(false);
                if (!insideHouse) {
                    villageOpt.ifPresent(v ->
                            DockSuppression.clearAllOverlapping(level, v, dock.bounds()));
                    DockBuildingSync.sync(level, dock, pos);
                    villageOpt.ifPresent(v -> {
                        BuildingTierReconciler.reconcileVillage(v, level);
                        DockLocationIndex.rebuildVillage(level, v);
                        BuildingRecognitionTracker.reconcile(level, v);
                        SpiritReconciler.reconcileVillage(level, v);
                    });
                    if (!TOWNSTEAD$AUTO_SCAN.equals(actName)) {
                        // Floor-system MCA clients no longer request their own
                        // village refresh after actions; the handler we're
                        // cancelling would have pushed one in its finally.
                        McaFloorCompat.pushVillageResponse(player);
                        ci.cancel();
                    }
                    return;
                }
                // Player is standing inside an existing enclosed building, so
                // this footprint is a house, not a dock. Fall through to
                // enclosure / MCA handling rather than injecting a phantom dock.
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
                DockLocationIndex.rebuildVillage(level, v);
                BuildingRecognitionTracker.reconcile(level, v);
                SpiritReconciler.reconcileVillage(level, v);
            });
            if (!TOWNSTEAD$AUTO_SCAN.equals(actName)) {
                McaFloorCompat.pushVillageResponse(player);
                ci.cancel();
            }
        }
    }

    private static Building townstead$findEnclosureAt(Village village, BlockPos pos) {
        for (Building b : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            String t = b.getType();
            if (t == null || !EnclosureTypeIndex.isEnclosureType(t)) continue;
            if (b.containsPos(pos)) return b;
        }
        return null;
    }

    private static Building townstead$findDockAt(Village village, BlockPos pos) {
        for (Building b : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            String t = b.getType();
            if (t == null || !t.startsWith("dock_")) continue;
            if (b.containsPos(pos)) return b;
        }
        return null;
    }

    @Unique
    private String townstead$reportData() {
        try {
            Object value = getClass().getMethod("data").invoke(this);
            return value instanceof String text ? text : null;
        } catch (NoSuchMethodException ignored) {
            // Plain-class MCA packet shape: fall through to its private field.
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        try {
            var field = getClass().getDeclaredField("data");
            field.setAccessible(true);
            return field.get(this) instanceof String text ? text : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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
        String actName = act.name();
        if (!TOWNSTEAD$RECONCILE_ACTIONS.contains(actName)) return;

        ServerLevel level = player.serverLevel();
        VillageManager.get(level)
                .findNearestVillage(player)
                .ifPresent(v -> {
                    BuildingTierReconciler.reconcileVillage(v, level);
                    // Open-air dock detection happens before the recognition
                    // diff so fresh docks show up in the tracker's "current"
                    // snapshot and fire events alongside any MCA-side
                    // adds/upgrades. REMOVE is excluded so user dismissal
                    // sticks — the suppression HEAD hook records the bounds,
                    // and DockBuildingSync checks it before re-syncing.
                    if (!TOWNSTEAD$REMOVE_ACTIONS.contains(actName)) {
                        townstead$detectAndSyncDockFromReport(level, player, v);
                    }
                    DockLocationIndex.rebuildVillage(level, v);
                    BuildingRecognitionTracker.reconcile(level, v);
                    SpiritReconciler.reconcileVillage(level, v);
                    // Floor-system MCA pushed its snapshot in the handler's
                    // finally, which runs before this hook — push again so
                    // the client sees the reconciled state.
                    McaFloorCompat.pushVillageResponse(player);
                });
    }

    // Larger than the fisherman's default scan radius because the player may
    // trigger a report from any corner of a sizable deck. 24 covers a ~48-
    // block footprint, well past a max-practical Wharf. Partial scans produce
    // an undersized plank component and false-downgrade the tier.
    private static final int TOWNSTEAD$REPORT_SCAN_RADIUS = 24;

    private static void townstead$detectAndSyncDockFromReport(ServerLevel level, ServerPlayer player, Village village) {
        try {
            BlockPos pos = player.blockPosition();
            // Don't double-classify: a report fired from inside an existing
            // enclosed building is that house, not a dock at the player's feet.
            if (townstead$insideEnclosedBuilding(village, pos)) return;
            Dock dock = DockScanner.scanForReport(level, pos, TOWNSTEAD$REPORT_SCAN_RADIUS);
            if (dock != null) {
                DockBuildingSync.sync(level, dock, pos);
            }
        } catch (Throwable t) {
            TOWNSTEAD$LOG.warn("Dock detection from report-building failed: {}", t.toString());
        }
    }

    /**
     * Is this position inside an existing enclosed building (a house or other
     * roofed MCA building)? Dock and open-air enclosure types are excluded,
     * since those are open structures a player can legitimately stand on.
     */
    private static boolean townstead$insideEnclosedBuilding(Village village, BlockPos pos) {
        for (Building b : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            String t = b.getType();
            if (t == null) continue;
            if (t.startsWith("dock_")) continue;
            if (EnclosureTypeIndex.isEnclosureType(t)) continue;
            if (b.containsPos(pos)) return true;
        }
        return false;
    }
}
