package com.aetherianartificer.townstead.client.gui.inventory;

import com.aetherianartificer.townstead.compat.curios.CuriosCompat;
import com.aetherianartificer.townstead.inventory.VillagerCurioRenderC2SPayload;
import com.aetherianartificer.townstead.inventory.VillagerCurioRenderS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Client side of the per-slot render toggle: sends the request, applies the server's answer. */
public final class VillagerCurioRenderClient {

    private VillagerCurioRenderClient() {}

    public static void requestToggle(int entityId, String slotId, int index) {
        VillagerCurioRenderC2SPayload request = new VillagerCurioRenderC2SPayload(entityId, slotId, index);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(request);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(request);
        *///?}
    }

    public static void apply(VillagerCurioRenderS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            CuriosCompat.setRendered(living, payload.slotId(), payload.index(), payload.render());
        }
    }
}
