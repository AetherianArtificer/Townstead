package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mca.McaFloorCompat;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import com.aetherianartificer.townstead.recognition.OptionalBuildingRecognition;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
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

    /**
     * Actions where the player may be standing at an open-air building. AUTO_SCAN is not one of
     * them: in MCA it only toggles a village flag, and recognising on it would quietly bring
     * back a building the player had just removed.
     */
    private static final Set<String> TOWNSTEAD$OPEN_AIR_SCAN_ACTIONS =
            Set.of("ADD", "ADD_BUILDING", "ADD_ROOM");

    /**
     * Actions that can change what buildings a village has. ADD_FLOOR and
     * ADD_BASEMENT attach to a structure the player is already inside, so they
     * reconcile but never take the synthetic path above.
     */
    private static final Set<String> TOWNSTEAD$RECONCILE_ACTIONS =
            Set.of("ADD", "ADD_BUILDING", "ADD_ROOM", "ADD_FLOOR", "ADD_BASEMENT",
                    "UPDATE_ROOM", "REMOVE", "REMOVE_ROOM", "REMOVE_FLOOR", "FULL_SCAN", "AUTO_SCAN",
                    "FORCE_TYPE", "SET_MAIN_ROOM", "SET_ROOM_INHERITANCE");

    //? if <1.21 {
    /*@Shadow(remap = false)
    private ReportBuildingMessage.Action action;
    *///?}

    //? if >=1.21 {
    @Inject(method = "handleServer", at = @At("HEAD"), cancellable = true, remap = false)
    //?} else {
    /*@Inject(method = "receive", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$interceptOpenAirAction(ServerPlayer player, CallbackInfo ci) {
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
            Optional<OptionalBuildingRecognition.Removed> removed =
                    OptionalBuildingRecognition.remove(level, pos);
            TOWNSTEAD$LOG.info("Optional building removal action={} pos={} result={}",
                    actName, pos, removed.map(value -> Integer.toString(value.buildingId())).orElse("none"));
            if (removed.isPresent()) {
                Village village = removed.get().village();
                com.aetherianartificer.townstead.compat.mca.BuildingReportReconciler.reconcile(
                        level, pos, village, TOWNSTEAD$LOG);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "blueprint.buildingRemoved"), true);
                McaFloorCompat.pushVillageResponse(player);
                ci.cancel();
            }
            return;
        }

        if (TOWNSTEAD$OPEN_AIR_SCAN_ACTIONS.contains(actName)) {
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
                            com.aetherianartificer.townstead.compat.mca.BuildingReportReconciler.reconcile(
                                    level, pos, v, TOWNSTEAD$LOG);
                        });
                        String message = registration == OptionalBuildingRecognition.Registration.CREATED
                                ? "blueprint.buildingAdded" : "blueprint.scan.identical";
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(message), true);
                        McaFloorCompat.pushVillageResponse(player);
                        ci.cancel();
                        return;
                    }
                }
            }
        }
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
                    if ("FULL_SCAN".equals(actName)) {
                        OptionalBuildingRecognition.RefreshResult refreshed =
                                OptionalBuildingRecognition.reconcileVillage(level, v);
                        int decorations = com.aetherianartificer.townstead.decoration.DecorationRecognizer
                                .reconcileVillage(level, v);
                        TOWNSTEAD$LOG.info("Village refresh imported {} open-air buildings, refreshed {}, and imported {} decorations",
                                refreshed.created(), refreshed.refreshed(), decorations);
                        var catalog = com.aetherianartificer.townstead.client.catalog.CatalogSyncS2CPayload
                                .snapshot(level, v);
                        //? if neoforge {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, catalog);
                        //?} else if forge {
                        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, catalog);
                        *///?}
                    }
                    com.aetherianartificer.townstead.compat.mca.BuildingReportReconciler.reconcile(
                            level, player, v, TOWNSTEAD$LOG);
                    // Floor-system MCA pushed its snapshot in the handler's
                    // finally, which runs before this hook — push again so
                    // the client sees the reconciled state.
                    McaFloorCompat.pushVillageResponse(player);
                });
    }
}
