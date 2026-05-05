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
        try {
            if (!(player.level() instanceof ServerLevel level)) return;
            Optional<Village> village = Village.findNearest(player);
            if (village.isEmpty()) return;
            Village v = village.get();
            VillageSpiritCache.Entry entry = VillageSpiritCache.get(level, v.getId());
            if (entry == null) return;
            VillageSpiritSyncPayload payload = VillageSpiritSyncPayload.fromCache(v.getId(), entry);
            //? if neoforge {
            PacketDistributor.sendToPlayer(player, payload);
            //?} else if forge {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
            *///?}
        } catch (RuntimeException ex) {
            Townstead.LOGGER.warn("Unable to send village spirit snapshot for {}", player.getName().getString(), ex);
        }
    }
}
