package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.spirit.SpiritReconciler;
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
 * MCA's own {@code GetVillageResponse} is dispatched, we reconcile the
 * village's spirits (cheap) and ship a {@link VillageSpiritSyncPayload} with
 * whatever the cache now holds. The client stashes it in
 * {@code ClientVillageSpiritStore}, where the blueprint Spirit page reads
 * from.
 */
@Mixin(GetVillageRequest.class)
public abstract class GetVillageRequestMixin {
    //? if neoforge {
    @Inject(method = "handleServer", at = @At("TAIL"), remap = false)
    //?} else if forge {
    /*@Inject(method = "receive", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$sendSpiritSnapshot(ServerPlayer player, CallbackInfo ci) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Optional<Village> village = Village.findNearest(player);
        if (village.isEmpty()) return;
        Village v = village.get();
        VillageSpiritCache.Entry entry = VillageSpiritCache.get(level, v.getId());
        if (entry == null) {
            SpiritReconciler.reconcileVillage(level, v);
            entry = VillageSpiritCache.get(level, v.getId());
        }
        if (entry == null) return;
        VillageSpiritSyncPayload payload = VillageSpiritSyncPayload.fromCache(v.getId(), entry);
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
