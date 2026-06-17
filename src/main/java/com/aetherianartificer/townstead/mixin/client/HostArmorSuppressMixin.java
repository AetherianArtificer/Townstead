package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigArmorRenderer;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses MCA's villager-shaped host armor for an alternate-rig villager, so it does not float as a
 * wider, out-of-sync silhouette around the rig. {@link RigArmorRenderer} draws the armor fitted to the
 * rig instead.
 *
 * <p>Targets {@code HumanoidArmorLayer.render} (the {@code LivingEntity} overload), which every host
 * armor layer is. Two guards keep this narrow: the {@link RigArmorRenderer#isRendering()} check lets
 * our own rig-fitted draw through (it is also a {@code HumanoidArmorLayer}), and players are skipped so
 * the player's own working armor path is untouched. Normal villagers and vanilla mobs are not
 * alternate rigs, so they are never cancelled.</p>
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HostArmorSuppressMixin {

    //? if neoforge {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", remap = false, at = @At("HEAD"), cancellable = true, require = 0)
    *///?}
    private void townstead$suppressHostArmor(PoseStack pose, MultiBufferSource buffers, int light,
                                             LivingEntity entity, float limbSwing, float limbSwingAmount,
                                             float partialTick, float ageInTicks, float netHeadYaw,
                                             float headPitch, CallbackInfo ci) {
        if (RigArmorRenderer.isRendering()) return;
        // Players keep their own armor path entirely (helmet/etc. render normally and ride the head
        // anchor). A generic-rig player's mis-fitting boots are hidden instead by zeroing the host
        // model's leg scale (see the host-model animation mixins), which the armor copies.
        if (entity instanceof Player) return;
        if (RigModels.isAlternate(RigModels.rigBaseFor(entity))) ci.cancel();
    }
}
