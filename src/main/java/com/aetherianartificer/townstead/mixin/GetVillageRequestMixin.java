package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.spirit.VillageSpiritCache;
import com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload;
import net.conczin.mca.network.c2s.GetVillageRequest;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Piggybacks on MCA's "give me the nearest village's snapshot" request to
 * also send the village's current spirit state down to the client. After
 * MCA's own {@code GetVillageResponse} is dispatched, we ship a cached
 * {@link VillageSpiritSyncPayload} when one is already available. Cache misses
 * deliberately do not reconcile here: the default blueprint load path is
 * latency-sensitive, and the Spirit page has its own explicit query.
 */
@Mixin(GetVillageRequest.class)
public abstract class GetVillageRequestMixin {
    //? if neoforge {
    @Inject(method = "handleServer", at = @At("TAIL"), remap = false)
    //?} else if forge {
    /*@Inject(method = "receive", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$sendSpiritSnapshot(ServerPlayer player, CallbackInfo ci) {
        long t0 = System.nanoTime();
        try {
            if (!(player.level() instanceof ServerLevel level)) {
                Townstead.LOGGER.info("[TS-Diag/Spirit] piggyback skip reason=notServerLevel player={}",
                        player.getName().getString());
                return;
            }
            long t1 = System.nanoTime();
            Optional<Village> village = Village.findNearest(player);
            long t2 = System.nanoTime();
            if (village.isEmpty()) {
                Townstead.LOGGER.info("[TS-Diag/Spirit] piggyback skip reason=noVillage player={} findNearestUs={}",
                        player.getName().getString(), (t2 - t1) / 1_000L);
                return;
            }
            Village v = village.get();
            VillageSpiritCache.Entry entry = VillageSpiritCache.get(level, v.getId());
            long t3 = System.nanoTime();
            if (entry == null) {
                Townstead.LOGGER.info("[TS-Diag/Spirit] piggyback skip reason=cacheMiss player={} village={} findNearestUs={} cacheUs={}",
                        player.getName().getString(), v.getId(),
                        (t2 - t1) / 1_000L, (t3 - t2) / 1_000L);
                return;
            }
            VillageSpiritSyncPayload payload = VillageSpiritSyncPayload.fromCache(v.getId(), entry);
            //? if neoforge {
            PacketDistributor.sendToPlayer(player, payload);
            //?} else if forge {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
            *///?}
            long t4 = System.nanoTime();
            Townstead.LOGGER.info("[TS-Diag/Spirit] piggyback sent player={} village={} findNearestUs={} cacheUs={} sendUs={} totalUs={}",
                    player.getName().getString(), v.getId(),
                    (t2 - t1) / 1_000L, (t3 - t2) / 1_000L, (t4 - t3) / 1_000L,
                    (t4 - t0) / 1_000L);
        } catch (RuntimeException ex) {
            Townstead.LOGGER.warn("Unable to send village spirit snapshot for {}", player.getName().getString(), ex);
        }
    }
}
