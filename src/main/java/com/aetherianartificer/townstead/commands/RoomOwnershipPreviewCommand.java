package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.storage.OwnershipScope;
import com.aetherianartificer.townstead.storage.RoomOwner;
import com.aetherianartificer.townstead.storage.net.RoomOwnershipSnapshotS2CPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opens the ownership editor with stable fake data for quick visual iteration. */
public final class RoomOwnershipPreviewCommand {
    private static final long PREVIEW_WORKSITE_ID = -1L;

    private RoomOwnershipPreviewCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead")
                .then(Commands.literal("debug")
                        .then(Commands.literal("ownership-screen")
                                .executes(context -> open(context.getSource())))));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<RoomOwnershipSnapshotS2CPayload.Person> people = new ArrayList<>();
        people.add(person("Astrida", RoomOwner.Kind.VILLAGER, false, true, true));
        people.add(person("Ervins", RoomOwner.Kind.VILLAGER, false, true, true));
        people.add(person("Inesis", RoomOwner.Kind.VILLAGER, true, false, false));
        people.add(new RoomOwnershipSnapshotS2CPayload.Person(
                player.getUUID(), player.getGameProfile().getName(), RoomOwner.Kind.PLAYER,
                true, false, false));
        people.add(person("Bronislaus", RoomOwner.Kind.VILLAGER, false, false, false));
        people.add(person("Inguna", RoomOwner.Kind.VILLAGER, false, false, false));
        people.add(person("Katrina", RoomOwner.Kind.VILLAGER, false, false, false));
        people.add(person("Igors", RoomOwner.Kind.VILLAGER, false, false, false));
        people.add(person("Bronwen", RoomOwner.Kind.VILLAGER, false, false, false));
        people.add(person("Maija", RoomOwner.Kind.VILLAGER, false, false, false));

        RoomOwnershipSnapshotS2CPayload payload = new RoomOwnershipSnapshotS2CPayload(
                BlockPos.ZERO, PREVIEW_WORKSITE_ID, "Big House", OwnershipScope.ROOM,
                true, true, people);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
        return 1;
    }

    private static RoomOwnershipSnapshotS2CPayload.Person person(
            String name, RoomOwner.Kind kind, boolean selected,
            boolean homeInRoom, boolean homeInBuilding) {
        UUID uuid = UUID.nameUUIDFromBytes(
                ("townstead:ownership-preview:" + name).getBytes(StandardCharsets.UTF_8));
        return new RoomOwnershipSnapshotS2CPayload.Person(
                uuid, name, kind, selected, homeInRoom, homeInBuilding);
    }
}
