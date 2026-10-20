package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.root.disposition.Dispositions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps each player's view of costumed mobs' nameplates current: the person's name, coloured by
 * how the mob regards that player. A plate is sent when a mob comes into view and again when its
 * stance toward the player changes (the player turns vampire, say).
 */
public final class WildPlates {
    private static final int INTERVAL = 40;
    private static final double RANGE = 64;
    private static final Map<ServerPlayer, Map<Integer, Byte>> SENT = new WeakHashMap<>();

    private WildPlates() {}

    public static WildPlateS2CPayload plate(ServerPlayer player, Mob mob, VillagerEntityMCA person) {
        byte disposition = (byte) Dispositions.between(mob, player).ordinal();
        synchronized (SENT) {
            SENT.computeIfAbsent(player, key -> new HashMap<>()).put(mob.getId(), disposition);
        }
        return new WildPlateS2CPayload(mob.getId(), person.getDisplayName().getString(), disposition);
    }

    public static void tick(ServerPlayer player) {
        if ((player.tickCount + player.getId()) % INTERVAL != 0) return;
        Map<Integer, Byte> sent;
        synchronized (SENT) {
            sent = SENT.computeIfAbsent(player, key -> new HashMap<>());
        }
        Map<Integer, Byte> seen = new HashMap<>();
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), WildCostume::has)) {
            byte disposition = (byte) Dispositions.between(mob, player).ordinal();
            seen.put(mob.getId(), disposition);
            Byte last = sent.get(mob.getId());
            if (last != null && last == disposition) continue;
            VillagerEntityMCA person = WildCostume.person(mob);
            if (person == null) continue;
            send(player, new WildPlateS2CPayload(mob.getId(), person.getDisplayName().getString(), disposition));
        }
        synchronized (SENT) {
            sent.clear();
            sent.putAll(seen);
        }
    }

    static void send(ServerPlayer player, WildPlateS2CPayload plate) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, plate);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, plate);
        *///?}
    }
}
