package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.hunger.FishermanHookLinkStore;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic-only mixin: logs whenever FishingHookRenderer.render is invoked
 * for a hook that has an entry in FishermanHookLinkStore. The actual bobber
 * drawing is handled by FishermanLineRenderer in RenderLevelStageEvent so we
 * control the RenderType buffer lifecycle and can explicitly flush via
 * endBatch. A prior mixin-driven quad draw never appeared because our local
 * RenderType instance — a different object from vanilla's cached RENDER_TYPE
 * despite identical composition — ended up in a dynamic buffer that vanilla
 * never flushed.
 *
 * Kept as a lightweight signal that tells us the render pipeline reaches
 * our hooks. Flip DEBUG_VILLAGER_AI on to see the log.
 */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    private static int townstead$diagTick = 0;

    //? if neoforge {
    @Inject(method = "render", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_7392_", remap = false, at = @At("HEAD"), require = 0)
    *///?}
    private void townstead$logRenderCall(
            FishingHook hook, float yaw, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            CallbackInfo ci
    ) {
        if (!com.aetherianartificer.townstead.TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (++townstead$diagTick % 40 != 0) return;
        com.aetherianartificer.townstead.Townstead.LOGGER.info(
                "[BobberMixin] render fired hookId={} owner={} linkedVillagerId={}",
                hook.getId(),
                hook.getPlayerOwner(),
                FishermanHookLinkStore.villagerFor(hook.getId()));
    }
}
