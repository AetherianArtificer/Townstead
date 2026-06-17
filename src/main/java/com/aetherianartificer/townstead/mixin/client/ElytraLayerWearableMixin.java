package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigWearables;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nudges worn elytra onto a non-humanoid rig's back: just before the elytra layer reads the host body
 * bone, re-pose it to the rig's {@code back} base+{@code elytra} delta. A no-op for normal entities
 * and any rig without a back anchor. {@code require = 0} so a mapping miss falls back to the base.
 */
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerWearableMixin {

    //? if neoforge {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), require = 0)
    //?} else {
    /*@Inject(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", remap = false, at = @At("HEAD"), require = 0)
    *///?}
    private void townstead$anchorElytra(PoseStack pose, MultiBufferSource buffers, int light,
                                        LivingEntity entity, float limbSwing, float limbSwingAmount,
                                        float partialTick, float ageInTicks, float netHeadYaw, float headPitch,
                                        CallbackInfo ci) {
        RigWearables.applyItem(entity, "elytra");
    }
}
