package com.aetherianartificer.townstead.chronicle.net;

import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-owned, short-lived authorization created by reading the shelves of a village's
 * Archives building. Query packets carry no trusted village identity.
 */
public final class ChronicleArchiveAccess {
    private static final long LEASE_TICKS = 20L * 30L;
    private static final Map<UUID, Lease> LEASES = new ConcurrentHashMap<>();

    public record Lease(ResourceLocation dimension, int villageId, String villageName,
                        long expiresAtTick) {}

    private ChronicleArchiveAccess() {}

    /**
     * Reading the shelves: an empty-hand click on a bookshelf that belongs to the village's
     * Archives building opens that village's history.
     */
    public static boolean tryOpenBuilding(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (!player.serverLevel().getBlockState(pos)
                .is(net.minecraft.world.level.block.Blocks.BOOKSHELF)) return false;
        Optional<Village> village =
                com.aetherianartificer.townstead.village.ArchivesBuilding.villageIfInside(player, pos);
        if (village.isEmpty()) return false;
        grantLease(player, village.get());
        return true;
    }

    private static void grantLease(ServerPlayer player, Village village) {
        long expires = player.serverLevel().getGameTime() + LEASE_TICKS;
        Lease lease = new Lease(player.serverLevel().dimension().location(), village.getId(),
                village.getName(), expires);
        LEASES.put(player.getUUID(), lease);
        sendOpen(player, new ChronicleOpenS2CPayload(lease.villageName()));
    }

    public static Optional<Lease> resolve(ServerPlayer player) {
        Lease lease = LEASES.get(player.getUUID());
        if (lease == null) return Optional.empty();
        if (!lease.dimension().equals(player.serverLevel().dimension().location())
                || player.serverLevel().getGameTime() > lease.expiresAtTick()) {
            LEASES.remove(player.getUUID());
            return Optional.empty();
        }
        Optional<Village> nearest = Village.findNearest(player);
        if (nearest.isEmpty() || nearest.get().getId() != lease.villageId()
                || !nearest.get().isWithinBorder(player)) {
            LEASES.remove(player.getUUID());
            return Optional.empty();
        }
        Lease renewed = new Lease(lease.dimension(), lease.villageId(), lease.villageName(),
                player.serverLevel().getGameTime() + LEASE_TICKS);
        LEASES.put(player.getUUID(), renewed);
        return Optional.of(renewed);
    }

    public static void clear(UUID player) { LEASES.remove(player); }
    public static void clearAll() { LEASES.clear(); }

    private static void sendOpen(ServerPlayer player, ChronicleOpenS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

}
