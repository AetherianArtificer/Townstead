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
import net.minecraft.network.chat.MutableComponent;
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
            source.sendFailure(Component.translatable("townstead.command.building.player_only"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        VillageManager manager = VillageManager.get(level);
        Village village = manager.findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            source.sendSuccess(() -> Component.translatable("townstead.command.building.no_village",
                    pos.toString(), McaBuildingDiscovery.pendingDescription(level, pos)), false);
            return 0;
        }

        Building room = McaBuildingCompat.functionalRoomAt(level, village, pos);
        MutableComponent report = Component.translatable("townstead.command.building.header", pos.toString())
                .append("\n").append(Component.translatable("townstead.command.building.village",
                        village.getId(), village.isAutoScan()))
                .append("\n").append(Component.translatable("townstead.command.building.discovery",
                        McaBuildingDiscovery.pendingDescription(level, pos)));
        if (room == null) {
            report.append("\n").append(Component.translatable("townstead.command.building.room.none"));
            //? if >=1.21 {
            var scan = manager.analyzeRoom(pos);
            report.append("\n").append(Component.translatable(
                    "townstead.command.building.add_room_analysis", scan.result().toString(),
                    scan.matchingTypes().toString()));
            //?}
            source.sendSuccess(() -> report, false);
            return 0;
        }

        List<String> candidates = McaBuildingCompat.matchingTypeNames(village, room);
        report.append("\n").append(Component.translatable("townstead.command.building.room",
                        room.getId(), room.getType(), McaBuildingCompat.effectiveType(village, room),
                        room.isTypeForced()))
                .append("\n").append(Component.translatable("townstead.command.building.source",
                        McaBuildingCompat.reference(room)))
                .append("\n").append(Component.translatable("townstead.command.building.candidates",
                        candidates.toString()));
        //? if >=1.21 {
        report.append("\n").append(Component.translatable("townstead.command.building.structure_floor",
                room.getStructureId(), room.getFloorId(), room.getFloorRegions().size(),
                room.getFloorFootprintArea()));
        RegisteredRoomUpdate update = manager.analyzeRegisteredRoomUpdate(
                village, room.getId(), pos);
        report.append("\n").append(Component.translatable("townstead.command.building.update_analysis",
                update.result().toString(), update.playerMatchingTypes().toString()));
        //?}
        source.sendSuccess(() -> report, false);
        return 1;
    }
}
