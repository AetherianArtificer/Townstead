package com.aetherianartificer.townstead.compat.cybernetics;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A faceplate alias that names a player on the server makes the wearer look like that player: their
 * MCA look and their Root. The look is sent to everyone who sees the wearer, under a stand-in id,
 * and clients draw it only while they render the wearer. An alias that names no one keeps
 * Cybernetics' own disguise skin.
 */
public final class FaceplateDisguise {

    private record Shown(int entityId, boolean worn, UUID shown, String look) {}

    private static final Map<UUID, Shown> SHOWN = new ConcurrentHashMap<>();

    private FaceplateDisguise() {}

    /** The id a disguised player's shown Root is kept under on the client. */
    public static int dataId(int entityId) {
        return -2 - entityId;
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        SHOWN.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Shown now = shownFor(server, player);
            Shown before = now == null ? SHOWN.remove(player.getUUID()) : SHOWN.put(player.getUUID(), now);
            if (Objects.equals(now, before)) continue;
            send(player, now, null);
        }
    }

    /** Tells a player who just started seeing {@code target} how the target looks. */
    public static void syncTo(ServerPlayer viewer, ServerPlayer target) {
        Shown shown = SHOWN.get(target.getUUID());
        if (shown != null) send(target, shown, viewer);
    }

    @Nullable
    private static Shown shownFor(MinecraftServer server, ServerPlayer player) {
        Component alias = FaceplateAlias.worn(player);
        if (alias == null) return null;
        ServerPlayer named = named(server, player, alias.getString().trim());
        if (named == null) return new Shown(player.getId(), true, null, "");
        return new Shown(player.getId(), true, named.getUUID(), look(named));
    }

    /** The player an alias names: by username first, then by the name they go by. */
    @Nullable
    private static ServerPlayer named(MinecraftServer server, ServerPlayer wearer, String alias) {
        if (alias.isEmpty()) return null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(alias)) return p;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != wearer && p.getDisplayName().getString().trim().equalsIgnoreCase(alias)) return p;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Component custom = p.getCustomName();
            if (p != wearer && custom != null && custom.getString().trim().equalsIgnoreCase(alias)) return p;
        }
        return null;
    }

    private static String look(ServerPlayer named) {
        var genes = com.aetherianartificer.townstead.root.ExpressedGenesS2CPayload.forEntity(0, named);
        return com.aetherianartificer.townstead.root.PlayerRoot.getRootId(named) + '|' + genes.genes()
                + '|' + genes.hair() + '|' + mcaLook(named);
    }

    private static String mcaLook(ServerPlayer named) {
        //? if neoforge {
        var data = net.conczin.mca.server.world.data.PlayerSaveData.get(named);
        return data.isEntityDataSet() ? Integer.toString(data.getEntityData().hashCode()) : "";
        //?} else {
        /*return "";
        *///?}
    }

    /** Sends {@code shown} (null: no disguise) for {@code wearer} to one viewer, or to all who see them. */
    private static void send(ServerPlayer wearer, @Nullable Shown shown, @Nullable ServerPlayer viewer) {
        //? if neoforge {
        int id = wearer.getId();
        ServerPlayer named = shown == null || shown.shown() == null ? null
                : wearer.server.getPlayerList().getPlayer(shown.shown());
        if (named != null) {
            var data = net.conczin.mca.server.world.data.PlayerSaveData.get(named);
            if (data.isEntityDataSet()) {
                deliver(wearer, viewer, new net.conczin.mca.network.s2c.PlayerDataMessage(named.getUUID(), data.getEntityData()));
            }
            deliver(wearer, viewer, new com.aetherianartificer.townstead.root.RootSyncS2CPayload(dataId(id),
                    com.aetherianartificer.townstead.root.PlayerRoot.getRootId(named)));
            deliver(wearer, viewer, com.aetherianartificer.townstead.root.ExpressedGenesS2CPayload.forEntity(dataId(id), named));
        }
        deliver(wearer, viewer, new FaceplateDisguiseS2CPayload(id, shown != null,
                named == null ? null : named.getUUID()));
        //?}
    }

    //? if neoforge {
    private static void deliver(ServerPlayer wearer, @Nullable ServerPlayer viewer,
                                net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (viewer != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, payload);
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(wearer, payload);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(wearer, payload);
    }
    //?}
}
