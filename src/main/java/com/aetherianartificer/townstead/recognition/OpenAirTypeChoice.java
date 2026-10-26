package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.compat.mca.BuildingReportReconciler;
import com.aetherianartificer.townstead.compat.mca.McaFloorCompat;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When several kinds of open-air building fit the same ground, the player picks one on MCA's own
 * room-type screen, as they do for rooms. Only choices this class asked for are answered here;
 * MCA's room choices pass through untouched.
 */
public final class OpenAirTypeChoice {
    private static final Logger LOG = LoggerFactory.getLogger("Townstead/OpenAirTypeChoice");

    private record Pending(BlockPos source, List<String> types) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private OpenAirTypeChoice() {}

    /** Opens the choice screen. False when this MCA has no such screen, so the caller decides. */
    public static boolean ask(ServerPlayer player, BlockPos source,
                              List<OptionalBuildingRecognition.Candidate> ties,
                              ReportBuildingMessage.Action action) {
        List<String> types = ties.stream().map(OptionalBuildingRecognition.Candidate::typeName).toList();
        try {
            //? if neoforge {
            net.conczin.mca.network.Network.sendToPlayer(
                    new net.conczin.mca.network.s2c.BuildingPolymorphMessage(types, source, action, -1), player);
            //?} else {
            /*net.conczin.mca.cobalt.network.NetworkHandler.sendToPlayer(
                    new net.conczin.mca.network.s2c.BuildingPolymorphMessage(types, source, false), player);
            *///?}
        } catch (LinkageError missing) {
            return false;
        }
        PENDING.put(player.getUUID(), new Pending(source.immutable(), types));
        return true;
    }

    /**
     * Applies the player's answer to a choice this class asked for. False when the answer belongs
     * to one of MCA's own room choices.
     */
    public static boolean confirm(ServerPlayer player, BlockPos source, String chosenType) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || source == null || !pending.source().equals(source)
                || !pending.types().contains(chosenType)) {
            return false;
        }
        PENDING.remove(player.getUUID());
        // As MCA does for rooms: an answer from somewhere else is about a different place.
        if (!player.blockPosition().equals(source)) return true;
        ServerLevel level = player.serverLevel();
        OptionalBuildingRecognition.findType(level, source, chosenType).ifPresentOrElse(
                candidate -> commit(player, source, candidate),
                () -> player.displayClientMessage(Component.translatable("blueprint.scan.invalid_type"), true));
        return true;
    }

    /** Registers a site the player added or chose, and reports it. False when it could not be added. */
    public static boolean commit(ServerPlayer player, BlockPos source, OptionalBuildingRecognition.Candidate candidate) {
        ServerLevel level = player.serverLevel();
        OptionalBuildingRecognition.Registration registration =
                OptionalBuildingRecognition.registerByHand(level, candidate);
        if (registration == OptionalBuildingRecognition.Registration.FAILED) return false;
        // Added by hand: a removal the player made here no longer stands.
        DismissedSites.changed(level, source);
        VillageManager.get(level).findNearestVillage(player)
                .ifPresent(village -> BuildingReportReconciler.reconcile(level, source, village, LOG));
        String message = registration == OptionalBuildingRecognition.Registration.CREATED
                ? "townstead.blueprint.building_added" : "blueprint.scan.identical";
        player.displayClientMessage(Component.translatable(message), true);
        McaFloorCompat.pushVillageResponse(player);
        return true;
    }
}
