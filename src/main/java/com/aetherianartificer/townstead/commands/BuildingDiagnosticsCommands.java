package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildingDiscovery;
import com.mojang.brigadier.CommandDispatcher;
import net.conczin.mca.server.world.data.Building;
//? if >=1.21 {
import net.conczin.mca.server.world.data.RegisteredRoomUpdate;
//?}
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Read-only field diagnostics for MCA/Townstead building reports. */
public final class BuildingDiagnosticsCommands {
    private BuildingDiagnosticsCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("townstead")
                .then(Commands.literal("building")
                        .then(Commands.literal("diagnose")
                                .executes(c -> diagnose(c.getSource())))));
    }

    private static int diagnose(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        VillageManager manager = VillageManager.get(level);
        Village village = manager.findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            source.sendSuccess(() -> Component.literal("Building diagnose at " + pos
                    + ": no MCA village in merge range; "
                    + McaBuildingDiscovery.pendingDescription(level, pos)), false);
            return 0;
        }

        Building room = McaBuildingCompat.functionalRoomAt(level, village, pos);
        StringBuilder report = new StringBuilder("Building diagnose at ").append(pos)
                .append("\nVillage: ").append(village.getId())
                .append(" (autoScan=").append(village.isAutoScan()).append(')')
                .append("\nDiscovery: ").append(McaBuildingDiscovery.pendingDescription(level, pos));
        if (room == null) {
            report.append("\nRoom: none (MCA exact lookup)");
            //? if >=1.21 {
            var scan = manager.analyzeRoom(pos);
            report.append("\nMCA add-room analysis: ").append(scan.result())
                    .append(" candidates=").append(scan.matchingTypes());
            //?}
            source.sendSuccess(() -> Component.literal(report.toString()), false);
            return 0;
        }

        List<String> candidates = McaBuildingCompat.matchingTypeNames(village, room);
        report.append("\nRoom: id=").append(room.getId())
                .append(" direct=").append(room.getType())
                .append(" effective=").append(McaBuildingCompat.effectiveType(village, room))
                .append(" forced=").append(room.isTypeForced())
                .append("\nSource: ").append(McaBuildingCompat.reference(room))
                .append("\nNormalized MCA candidates: ").append(candidates);
        //? if >=1.21 {
        report.append("\nStructure/floor: ").append(room.getStructureId()).append('/')
                .append(room.getFloorId()).append(" regions=").append(room.getFloorRegions().size())
                .append(" cells=").append(room.getFloorFootprintArea());
        RegisteredRoomUpdate update = manager.analyzeRegisteredRoomUpdate(
                village, room.getId(), pos);
        report.append("\nMCA update analysis: ").append(update.result())
                .append(" candidates=").append(update.playerMatchingTypes());
        //?}
        source.sendSuccess(() -> Component.literal(report.toString()), false);
        return 1;
    }
}
