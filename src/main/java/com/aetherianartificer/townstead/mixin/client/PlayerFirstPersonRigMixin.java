package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.RigSkinTone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First-person held-item arm for alternate-rig players (the skeletownie). The full-body
 * {@link com.aetherianartificer.townstead.client.species.SpeciesRigLayer} only runs in third person;
 * the first-person arm is a separate path ({@code renderRightHand}/{@code renderLeftHand}) that MCA
 * draws with the host villager arm. We substitute the rig's own arm and cancel, so the skeletownie
 * sees its bone arm. Priority above MCA's MixinPlayerRenderer (1000) so our cancel preempts its arm.
 *
 * <p>1.20.1 Forge SRG: {@code m_117770_} renderRightHand, {@code m_117813_} renderLeftHand.</p>
 */
@Mixin(value = PlayerRenderer.class, priority = 1100)
public abstract class PlayerFirstPersonRigMixin {

    //? if neoforge {
    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_117770_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$rigRightHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        if (townstead$renderRigArm(pose, buffers, light, player, false)) ci.cancel();
    }

    //? if neoforge {
    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_117813_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$rigLeftHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        if (townstead$renderRigArm(pose, buffers, light, player, true)) ci.cancel();
    }

    private static boolean townstead$renderRigArm(PoseStack pose, MultiBufferSource buffers, int light,
                                                  AbstractClientPlayer player, boolean left) {
        String rigBase = RigModels.rigBaseFor(player);
        if (!RigModels.isAlternate(rigBase)) return false;
        HumanoidModel<LivingEntity> model = RigModels.model(rigBase);
        ResourceLocation texture = RigModels.texture(rigBase);
        if (model == null || texture == null) return false;
        model.attackTime = 0f;
        model.crouching = false;
        model.setupAnim(player, 0f, 0f, 0f, 0f, 0f);
        ModelPart arm = left ? model.leftArm : model.rightArm;
        arm.xRot = 0f;
        int tone = RigSkinTone.forEntity(player);
        VertexConsumer buffer = buffers.getBuffer(model.renderType(texture));
        //? if neoforge {
        arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, tone);
        //?} else {
        /*arm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY,
                ((tone >> 16) & 0xFF) / 255f, ((tone >> 8) & 0xFF) / 255f, (tone & 0xFF) / 255f,
                ((tone >>> 24) & 0xFF) / 255f);
        *///?}
        return true;
    }
}
