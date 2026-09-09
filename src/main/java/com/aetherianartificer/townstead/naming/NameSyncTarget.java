package com.aetherianartificer.townstead.naming;

import net.conczin.mca.entity.VillagerEntityMCA;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

/**
 * Sends a composed name to the players who can see the villager.
 *
 * <p>Townstead's own target, and the one that is always registered: every surface that draws a name
 * is on the client while the name itself is server state, so without this a family name exists and
 * is never visible. Registered unconditionally, so a world with no other naming mod still shows
 * what its cultures decided.</p>
 */
public final class NameSyncTarget {

    private NameSyncTarget() {}

    public static void bootstrap() {
        VillagerNames.addTarget(NameSyncTarget::send);
    }

    public static void syncToPlayer(net.minecraft.server.level.ServerPlayer player, VillagerEntityMCA villager) {
        NameParts parts = VillagerNames.parts(villager);
        NameSyncPayload payload = new NameSyncPayload(
                villager.getId(), parts.family(), Naming.cultureOf(villager), parts.order());
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static void send(VillagerEntityMCA villager, NameParts parts) {
        if (villager.level().isClientSide) return;
        NameSyncPayload payload = new NameSyncPayload(
                villager.getId(), parts.family(), Naming.cultureOf(villager), parts.order());
        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, payload);
        *///?}
    }
}
