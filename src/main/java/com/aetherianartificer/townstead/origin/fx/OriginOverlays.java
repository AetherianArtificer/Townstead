package com.aetherianartificer.townstead.origin.fx;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.origin.gene.types.OverlayGeneType;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side evaluation of a player's {@code overlay} genes. Each throttle tick it
 * resolves which overlays' conditions hold and syncs the active gene-id set to the
 * owning player for the HUD. Players with no overlay genes are skipped entirely, so
 * the common case costs nothing on the wire.
 */
public final class OriginOverlays {

    private OriginOverlays() {}

    public static void syncTo(ServerPlayer player) {
        if (player.tickCount % 5 != 0) return;
        List<Power> expressed = Powers.active(player);
        List<String> active = null;
        ConditionContext ctx = null;
        boolean hasOverlay = false;
        for (Power gene : expressed) {
            if (!(gene.component() instanceof OverlayGeneType.Instance overlay)) continue;
            hasOverlay = true;
            if (active == null) active = new ArrayList<>();
            if (overlay.condition() == null) {
                active.add(gene.id().toString());
            } else {
                if (ctx == null) ctx = new ConditionContext(player);
                if (overlay.condition().test(ctx)) active.add(gene.id().toString());
            }
        }
        if (!hasOverlay) return;
        OverlayActiveS2CPayload payload = new OverlayActiveS2CPayload(active == null ? List.of() : active);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
