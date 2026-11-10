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
                villager.getId(), familyFor(player, villager, parts), com.aetherianartificer.townstead.culture.Cultures.rootOf(Naming.cultureOf(villager)), parts.order(),
                rule(villager), traditionOf(villager), parts.familyLocked());
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    /** Tells this player and everyone who can see them about their family name. */
    public static void syncPlayer(net.minecraft.server.level.ServerPlayer player) {
        NameSyncPayload payload = playerPayload(player);
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(player, payload);
        *///?}
    }

    public static void syncPlayerTo(net.minecraft.server.level.ServerPlayer watcher, net.minecraft.server.level.ServerPlayer player) {
        NameSyncPayload payload = playerPayload(player);
        //? if neoforge {
        PacketDistributor.sendToPlayer(watcher, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(watcher, payload);
        *///?}
    }

    public static void sendPlayerFamilies(net.minecraft.server.level.ServerPlayer player) {
        PlayerFamiliesS2CPayload payload = new PlayerFamiliesS2CPayload(
                NamingRegisterSavedData.get(player.server).playerFamilies());
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    public static void sendCatalog(net.minecraft.server.level.ServerPlayer player) {
        NamingCatalogS2CPayload payload = NamingCatalogS2CPayload.snapshot();
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static NameSyncPayload playerPayload(net.minecraft.server.level.ServerPlayer player) {
        NamingTradition tradition = Naming.traditionOf(player);
        String culture = com.aetherianartificer.townstead.root.PlayerRoot.getCulture(player);
        return new NameSyncPayload(player.getId(), Naming.familyName(player),
                culture.isEmpty() ? "" : com.aetherianartificer.townstead.culture.Cultures.rootOf(culture), PlayerNames.order(player),
                tradition == null ? NamingTradition.FamilyType.NONE : tradition.family().type(),
                tradition == null ? "" : tradition.id().toString(), PlayerNames.isLocked(player));
    }

    private static void send(VillagerEntityMCA villager, NameParts parts) {
        if (villager.level().isClientSide) return;
        com.aetherianartificer.townstead.persona.PersonaDefinition persona =
                com.aetherianartificer.townstead.persona.PersonaService.definition(villager);
        if (persona != null && persona.familyToldOnly() && villager.level() instanceof net.minecraft.server.level.ServerLevel level) {
            // Each player sees the family name only once they have been told it.
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                if (player.distanceToSqr(villager) < SYNC_RANGE * SYNC_RANGE) syncToPlayer(player, villager);
            }
            return;
        }
        NameSyncPayload payload = new NameSyncPayload(
                villager.getId(), parts.family(), com.aetherianartificer.townstead.culture.Cultures.rootOf(Naming.cultureOf(villager)), parts.order(),
                rule(villager), traditionOf(villager), parts.familyLocked());
        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, payload);
        *///?}
    }

    private static final double SYNC_RANGE = 160;

    private static String familyFor(net.minecraft.server.level.ServerPlayer player, VillagerEntityMCA villager, NameParts parts) {
        return com.aetherianartificer.townstead.persona.PersonaService.familyNameKnown(villager, player) ? parts.family() : "";
    }

    /** The family-name rule this villager's tradition uses, or none when they have no tradition. */
    static NamingTradition.FamilyType rule(VillagerEntityMCA villager) {
        NamingTradition tradition = Naming.traditionOf(villager);
        return tradition == null ? NamingTradition.FamilyType.NONE : tradition.family().type();
    }

    /** The id of the tradition this villager is named by, or empty. */
    static String traditionOf(VillagerEntityMCA villager) {
        NamingTradition tradition = Naming.traditionOf(villager);
        return tradition == null ? "" : tradition.id().toString();
    }
}
