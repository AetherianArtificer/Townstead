package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the vanilla held-item layer for a villager/player with an alternate species rig, so
 * the item is not drawn at the unscaled MCA arm. {@code SpeciesRigLayer} re-renders it at the rig's
 * hand inside the rig's scale instead.
 */
@Mixin(ItemInHandLayer.class)
public abstract class HeldItemSuppressMixin {

    //? if neoforge {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_6494_", remap = false, at = @At("HEAD"), cancellable = true, require = 0)
    *///?}
    private void townstead$suppressForAltRig(PoseStack pose, MultiBufferSource buffers, int light,
                                             LivingEntity entity, float limbSwing, float limbSwingAmount,
                                             float partialTick, float ageInTicks, float netHeadYaw,
                                             float headPitch, CallbackInfo ci) {
        if (RigModels.isAlternate(RigModels.rigBaseFor(entity))) ci.cancel();
    }
}
