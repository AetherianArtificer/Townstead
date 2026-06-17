package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigWearables;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nudges the cape onto a non-humanoid rig's back: just before the cape layer reads the host body
 * bone, re-pose it to the rig's {@code back} base+{@code cape} delta. A no-op for normal players and
 * any rig without a back anchor. {@code require = 0} so a mapping miss falls back to the base anchor.
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerWearableMixin {

    //? if neoforge {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", at = @At("HEAD"), require = 0)
    //?} else {
    /*@Inject(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V", remap = false, at = @At("HEAD"), require = 0)
    *///?}
    private void townstead$anchorCape(PoseStack pose, MultiBufferSource buffers, int light,
                                      AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                      float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                      CallbackInfo ci) {
        RigWearables.applyItem(player, "cape");
    }
}
