package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the host's held-item draw for a villager/player with an alternate species rig, so the
 * item is not drawn at the unscaled MCA arm. {@code SpeciesRigLayer} re-renders it at the rig's hand
 * inside the rig's scale instead.
 *
 * <p>Targets the leaf {@code renderArmWithItem} rather than {@code render}, because MCA's own
 * {@code MixinItemInHandLayer} also injects at {@code render} HEAD-cancellable (drawing both hands on
 * the host, then cancelling). Racing that injection is non-deterministic; both MCA's override and the
 * vanilla path funnel through {@code renderArmWithItem}, so suppressing the leaf kills the host draw
 * regardless of which {@code render} path ran. Without this, a skeleton guard shows its sword twice:
 * once at the rig bone and once at the host arm.</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class HeldItemSuppressMixin {

    //? if neoforge {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_117184_", remap = false, at = @At("HEAD"), cancellable = true, require = 0)
    *///?}
    private void townstead$suppressForAltRig(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx,
                                             HumanoidArm arm, PoseStack pose, MultiBufferSource buffers,
                                             int light, CallbackInfo ci) {
        if (RigModels.isAlternate(RigModels.rigBaseFor(entity))) ci.cancel();
    }
}
