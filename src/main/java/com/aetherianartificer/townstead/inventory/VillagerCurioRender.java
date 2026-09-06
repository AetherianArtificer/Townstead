package com.aetherianartificer.townstead.inventory;

import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server side of the per-slot render toggle on a villager's Curios. The request must come from the
 * player who has that villager's inventory open; the new flag goes back to them and to everyone
 * tracking the villager. Curios persists the flag with the rest of the slot data.
 */
public final class VillagerCurioRender {

    private VillagerCurioRender() {}

    public static void toggle(ServerPlayer player, VillagerCurioRenderC2SPayload request) {
        if (!CuriosCompat.present()) return;
        Entity entity = player.level().getEntity(request.entityId());
        if (!(entity instanceof VillagerEntityMCA villager) || !villager.isAlive()) return;
        if (!(player.containerMenu instanceof VillagerInventoryMenu menu) || menu.villager() != villager) return;

        boolean render = !CuriosCompat.isRendered(villager, request.slotId(), request.index());
        CuriosCompat.setRendered(villager, request.slotId(), request.index(), render);
        VillagerCurioRenderS2CPayload sync = new VillagerCurioRenderS2CPayload(
                villager.getId(), request.slotId(), request.index(), render);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, sync);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, sync);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, sync);
        *///?}
    }
}
