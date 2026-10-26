package com.aetherianartificer.townstead.livery;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps each client's view of who wears what livery. A slow sweep resolves every loaded villager and
 * player and sends only what changed; a player who starts tracking someone gets their livery then.
 * A role, a proclamation or a change of home is picked up within one sweep.
 */
public final class LiverySync {
    private static final int SWEEP_TICKS = 100;
    private static final Map<UUID, LiveryView> SENT = new HashMap<>();
    private static int ticks;
    private static MinecraftServer server;

    private LiverySync() {}

    public static void tick(MinecraftServer current) {
        // A new world in the same game starts from nothing: nothing has been sent to anyone yet.
        if (server != current) {
            server = current;
            SENT.clear();
        }
        if (++ticks < SWEEP_TICKS) return;
        ticks = 0;
        Map<UUID, Boolean> live = new HashMap<>();
        for (ServerLevel level : current.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof VillagerEntityMCA) && !(entity instanceof ServerPlayer)) continue;
                live.put(entity.getUUID(), true);
                LiveryView view = LiveryService.resolve(level, entity);
                if (Objects.equals(view, SENT.get(entity.getUUID()))) continue;
                if (view == null) SENT.remove(entity.getUUID());
                else SENT.put(entity.getUUID(), view);
                send(entity, new LiveryS2CPayload(entity.getId(), view));
            }
        }
        SENT.keySet().retainAll(live.keySet());
    }

    /** The tracker learns the livery of someone they have just come to see. */
    public static void onStartTracking(ServerPlayer viewer, Entity target) {
        if (!(target instanceof VillagerEntityMCA) && !(target instanceof ServerPlayer)) return;
        LiveryView view = SENT.get(target.getUUID());
        if (view == null) return;
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, new LiveryS2CPayload(target.getId(), view));
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(viewer, new LiveryS2CPayload(target.getId(), view));
        *///?}
    }

    /** A player's own livery, when they join. Tracking only ever covers other entities. */
    public static void onLogin(ServerPlayer player) {
        onStartTracking(player, player);
    }

    /** Sweeps on the next tick rather than waiting: after a proclamation, say. */
    public static void refresh() {
        ticks = SWEEP_TICKS;
    }

    private static void send(Entity entity, LiveryS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer self) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(self, payload);
        *///?}
    }
}
