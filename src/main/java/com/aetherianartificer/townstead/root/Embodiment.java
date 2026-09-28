package com.aetherianartificer.townstead.root;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Whether an entity wears its Root's body, server-side. Always for a villager; for a player only in
 * MCA's "Villager" model mode, since the "Player" and "Vanilla" modes draw a plain player. Gene
 * effects apply in every mode; only what needs that body (wall climbing, the rig) waits for it.
 * Mirrors the client's {@code RootClientStore.expresses}. Defaults to embodied on lookup failure.
 */
public final class Embodiment {
    private Embodiment() {}

    public static boolean embodied(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return true;
        try {
            int model = net.conczin.mca.server.world.data.PlayerSaveData.get(player)
                    .getEntityData().getInt("PlayerModel");
            return model == net.conczin.mca.entity.VillagerLike.PlayerModel.VILLAGER.ordinal();
        } catch (Throwable ignored) {
            return true;
        }
    }
}
