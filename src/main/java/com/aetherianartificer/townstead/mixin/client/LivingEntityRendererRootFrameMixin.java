package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.attachment.EntityRootFrames;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the pose matrix each living entity's render starts from (camera space at
 * the entity's origin, before its own rotations), so attachment physics can express
 * bone frames relative to the entity in world axes on every version and in GUI
 * previews alike.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererRootFrameMixin {

    //? if neoforge {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    *///?}
    private void townstead$pushRootFrame(LivingEntity entity, float entityYaw, float partialTick, PoseStack pose,
                                         MultiBufferSource buffers, int light, CallbackInfo ci) {
        EntityRootFrames.push(entity, pose.last().pose());
    }

    //? if neoforge {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    *///?}
    private void townstead$popRootFrame(LivingEntity entity, float entityYaw, float partialTick, PoseStack pose,
                                        MultiBufferSource buffers, int light, CallbackInfo ci) {
        EntityRootFrames.pop(entity);
    }
}
