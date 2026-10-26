package com.aetherianartificer.townstead.client.render;

import com.aetherianartificer.townstead.rest.RestSpot;
import com.aetherianartificer.townstead.rest.RestSpotsSyncS2CPayload;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hides a sleeper whose rest spot says so ({@code sleep.hidden_when}), as a closed coffin lid
 * covers whoever lies inside.
 */
public final class HiddenSleeperRender {
    private HiddenSleeperRender() {}

    //? if neoforge {
    public static void onRenderLivingPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    //?} else {
    /*public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    *///?}
        LivingEntity entity = event.getEntity();
        if (!entity.isSleeping() || RestSpotsSyncS2CPayload.hiding().isEmpty()) return;
        entity.getSleepingPos().ifPresent(pos -> {
            if (hidden(entity.level().getBlockState(pos))) event.setCanceled(true);
        });
    }

    private static boolean hidden(BlockState state) {
        for (RestSpotsSyncS2CPayload.Hiding spot : RestSpotsSyncS2CPayload.hiding()) {
            if (!RestSpot.hides(spot.hiddenWhen(), state)) continue;
            for (RestSpot.BlockMatch block : spot.blocks()) if (block.matches(state)) return true;
        }
        return false;
    }
}
