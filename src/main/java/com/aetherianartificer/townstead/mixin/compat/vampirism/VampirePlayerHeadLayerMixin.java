package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vampirism draws a vampire player's eyes and fangs on the humanoid head. A player drawn as a
 * non-humanoid rig (a Ribbit) has no such head, so the face would float where a human head would be;
 * the rig shows the vampire look itself (an iris mask). A player drawn with MCA's villager face has
 * its own eyes, which blink and take the vampire iris tint, so Vampirism's would sit on top of them
 * as a second pair. {@code @Pseudo} + {@code require = 0} keep this
 * inert without Vampirism. The typed {@code render} keeps its name on both loaders.
 */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.client.renderer.entity.layers.VampirePlayerHeadLayer", remap = false)
public abstract class VampirePlayerHeadLayerMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/player/Player;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void townstead$hideOnRig(PoseStack pose, MultiBufferSource buffers, int light, Player player,
                                     float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                     float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (RigModels.isGeneric(RigModels.rigBaseFor(player))
                || net.conczin.mca.MCAClient.useVillagerRenderer(player.getUUID())) ci.cancel();
    }
}
