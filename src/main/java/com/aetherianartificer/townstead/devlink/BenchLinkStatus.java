package com.aetherianartificer.townstead.devlink;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Server side of the Bench Link screen: builds status payloads and runs the screen's buttons. */
public final class BenchLinkStatus {

    private BenchLinkStatus() {}

    public static BenchLinkStatusS2CPayload snapshot(MinecraftServer server, boolean open) {
        int subject = BenchLink.subjectId();
        String subjectName = "";
        if (subject >= 0) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(subject);
                if (entity != null) {
                    subjectName = entity.getName().getString();
                    break;
                }
            }
        }
        return new BenchLinkStatusS2CPayload(open, BenchLink.running(), BenchLink.port(), BenchLink.token(),
                BenchLink.sessions(), subject, subjectName, BenchApi.worldName(server));
    }

    /** Opens the screen for {@code player}, or explains why they may not use it. */
    public static void open(ServerPlayer player) {
        if (!BenchLink.mayUse(player)) {
            player.displayClientMessage(Component.translatable("townstead.bench_link.no_permission"), true);
            return;
        }
        send(player, snapshot(player.server, true));
    }

    /** Refreshes every open Bench Link screen after a state change. */
    public static void broadcast(MinecraftServer server) {
        BenchLinkStatusS2CPayload payload = snapshot(server, false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (BenchLink.mayUse(player)) send(player, payload);
        }
    }

    public static void handleAction(ServerPlayer player, BenchLinkActionC2SPayload payload) {
        if (!BenchLink.mayUse(player)) return;
        switch (payload.action()) {
            case BenchLinkActionC2SPayload.START -> {
                if (!BenchLink.start(player.server)) {
                    player.displayClientMessage(Component.translatable("townstead.bench_link.start_failed"), true);
                }
            }
            case BenchLinkActionC2SPayload.STOP -> BenchLink.stop();
            case BenchLinkActionC2SPayload.CLEAR_SUBJECT -> BenchLink.setSubject(null);
            default -> { }
        }
        send(player, snapshot(player.server, false));
    }

    private static void send(ServerPlayer player, BenchLinkStatusS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
