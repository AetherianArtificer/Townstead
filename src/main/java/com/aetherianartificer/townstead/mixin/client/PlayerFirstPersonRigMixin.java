package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.RigSkinTone;
import com.aetherianartificer.townstead.root.Hold;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
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
    @Inject(method = "renderRightHand", at = @At("RETURN"), require = 1)
    //?} else {
    /*@Inject(method = "m_117770_", at = @At("RETURN"), remap = false, require = 1)
    *///?}
    private void townstead$overlayRightHand(PoseStack pose, MultiBufferSource buffers, int light,
                                            AbstractClientPlayer player, CallbackInfo ci) {
        if (!RigModels.isAlternate(RigModels.rigBaseFor(player))) {
            //? if neoforge {
            PlayerModel<AbstractClientPlayer> renderedModel =
                    ((PlayerRenderer) (Object) this).getModel();
            //?} else {
            /*PlayerModel<AbstractClientPlayer> renderedModel =
                    ((PlayerRenderer) (Object) this).getModel();
            *///?}
            com.aetherianartificer.townstead.client.species.SkinOverlayLayer
                    .renderFirstPersonArm(pose, buffers, light, player, renderedModel, false);
        }
    }

    //? if neoforge {
    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true, require = 1)
    //?} else {
    /*@Inject(method = "m_117813_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    *///?}
    private void townstead$rigLeftHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
        if (townstead$renderRigArm(pose, buffers, light, player, true)) ci.cancel();
    }

    //? if neoforge {
    @Inject(method = "renderLeftHand", at = @At("RETURN"), require = 1)
    //?} else {
    /*@Inject(method = "m_117813_", at = @At("RETURN"), remap = false, require = 1)
    *///?}
    private void townstead$overlayLeftHand(PoseStack pose, MultiBufferSource buffers, int light,
                                           AbstractClientPlayer player, CallbackInfo ci) {
        if (!RigModels.isAlternate(RigModels.rigBaseFor(player))) {
            //? if neoforge {
            PlayerModel<AbstractClientPlayer> renderedModel =
                    ((PlayerRenderer) (Object) this).getModel();
            //?} else {
            /*PlayerModel<AbstractClientPlayer> renderedModel =
                    ((PlayerRenderer) (Object) this).getModel();
            *///?}
            com.aetherianartificer.townstead.client.species.SkinOverlayLayer
                    .renderFirstPersonArm(pose, buffers, light, player, renderedModel, true);
        }
    }

    private static boolean townstead$renderRigArm(PoseStack pose, MultiBufferSource buffers, int light,
                                                  AbstractClientPlayer player, boolean left) {
        String rigBase = RigModels.rigBaseFor(player);
        if (!RigModels.isAlternate(rigBase)) return false;
        // A non-humanoid rig has no humanoid arm; stand in the front-leg bone the species grips with.
        if (RigModels.isGeneric(rigBase)) {
            return townstead$renderGenericArm(pose, buffers, light, player, left, rigBase);
        }
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

    /**
     * First-person arm for a non-humanoid rig: pose the rig model at rest and draw the front-leg bone the
     * species grips with on this physical side, so the spider-folk sees a front leg holding the item.
     * The bone is rendered in its natural rest pose at vanilla's first-person hand frame; any per-rig
     * nudge to seat the leg into view is dialed via the hold grip, same surface as the third-person grip.
     */
    private static boolean townstead$renderGenericArm(PoseStack pose, MultiBufferSource buffers, int light,
                                                      AbstractClientPlayer player, boolean left, String rigBase) {
        Hold.Grip grip = townstead$sideGrip(player, left);
        if (grip == null) return false;
        EntityModel<LivingEntity> model = RigModels.genericModel(rigBase);
        ResourceLocation texture = RigModels.texture(rigBase);
        if (model == null || texture == null) return false;
        model.attackTime = 0f;
        model.young = player.isBaby();
        model.riding = false;
        model.setupAnim(player, 0f, 0f, 0f, 0f, 0f);
        ModelPart bone = RigModels.bakedBone(rigBase, grip.bone());
        if (bone == null) return false;
        // Seat the leg into the first-person view: the authored first-person rotation orients it, then
        // the offset slides it into the corner. Both default to zero (leg drawn at the raw hand frame).
        float[] rot = grip.fpRotation();
        if (rot[0] != 0f) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rot[0]));
        if (rot[1] != 0f) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot[1]));
        if (rot[2] != 0f) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot[2]));
        float[] off = grip.fpOffset();
        pose.translate(off[0] / 16f, off[1] / 16f, off[2] / 16f);
        int tone = RigSkinTone.forEntity(player);
        VertexConsumer buffer = buffers.getBuffer(model.renderType(texture));
        //? if neoforge {
        bone.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, tone);
        //?} else {
        /*bone.render(pose, buffer, light, OverlayTexture.NO_OVERLAY,
                ((tone >> 16) & 0xFF) / 255f, ((tone >> 8) & 0xFF) / 255f, (tone & 0xFF) / 255f,
                ((tone >>> 24) & 0xFF) / 255f);
        *///?}
        return true;
    }

    /** The grip whose bone name marks this physical side (the leg that stands in for this first-person hand). */
    private static Hold.Grip townstead$sideGrip(LivingEntity entity, boolean left) {
        String want = left ? "left" : "right";
        Hold.Grip main = RigModels.holdGrip(entity, false);
        if (main != null && main.bone() != null && main.bone().contains(want)) return main;
        Hold.Grip off = RigModels.holdGrip(entity, true);
        if (off != null && off.bone() != null && off.bone().contains(want)) return off;
        return null;
    }
}
