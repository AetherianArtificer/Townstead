package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.animation.McaAnimationBridge;
import com.aetherianartificer.townstead.origin.Animations;
import com.aetherianartificer.townstead.origin.Hold;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders a villager whose species declares a supported non-villager rig (e.g. a skeleton) as that
 * rig's vanilla model + texture, posed from the entity. MCA's villager body layers are suppressed
 * for these entities by {@code VillagerBodyLayerSuppressMixin}, so this layer is the whole body.
 * First slice: humanoid rigs only. Added to MCA's villager renderer by
 * {@code VillagerSpeciesRigLayerMixin}, mirroring the attachment layer.
 */
public class SpeciesRigLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    // The host renderer's villager-equivalence baseline (RigModels.*_HOST_BASELINE): both hosts use
    // the same villager model, but the villager and genetics-player renderers apply different base
    // scaling before layers run, so each needs its own factor to match.
    private final float hostBaseline;

    // Whether to draw the entity's worn armor fitted to the rig (see RigArmorRenderer). The villager
    // host enables this and suppresses MCA's host armor; the player host leaves it off so the player's
    // own working armor path is untouched.
    private final boolean renderArmor;

    // fly: the rig's forward glide lean for creative flight, scaled by horizontal speed so it stays
    // upright while hovering and leans into a glide while moving. Pivoted around mid-body.
    private static final float FLY_PIVOT_Y = 0.9f;
    private static final float FLY_LEAN_PER_SPEED = 45f; // degrees per block/tick of horizontal speed
    private static final float FLY_MAX_LEAN = 35f;

    public SpeciesRigLayer(RenderLayerParent<T, M> parent, float hostBaseline, boolean renderArmor) {
        super(parent);
        this.hostBaseline = hostBaseline;
        this.renderArmor = renderArmor;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        String rigBase = RigModels.rigBaseFor(entity);
        if (entity.isInvisible()) return;
        if (!RigModels.isAlternate(rigBase)) return;
        // Non-humanoid rigs (spider, etc.) render through a lean branch that drives the vanilla model's
        // own setupAnim; the humanoid path below (arm poses, crouch, held items, fitted armor) does not
        // apply to them.
        if (RigModels.isGeneric(rigBase)) {
            renderGeneric(pose, buffers, light, entity, limbSwing, limbSwingAmount, partialTick,
                    ageInTicks, netHeadYaw, headPitch, rigBase);
            return;
        }
        HumanoidModel<LivingEntity> model = RigModels.model(rigBase);
        ResourceLocation texture = RigModels.texture(rigBase);
        if (model == null || texture == null) return;

        Animations anim = RigModels.animations(entity);
        poseRig(model, entity, anim, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick);
        VertexConsumer buffer = buffers.getBuffer(model.renderType(texture));
        float scale = hostBaseline * RigModels.scaleFor(entity);
        pose.pushPose();
        if (scale != 1f) {
            // Scale around the same origin the renderer scales the body around, i.e. before vanilla's
            // -1.501 grounding translate (the last op before layers run). Undo it, scale, redo it, so
            // the rig grows from the feet instead of sinking into the ground.
            pose.translate(0f, 1.501f, 0f);
            pose.scale(scale, scale, scale);
            pose.translate(0f, -1.501f, 0f);
        }
        // fly: creative flight has no vanilla model pose, so lean the rig into a glide instead of
        // standing bolt-upright. Elytra fall-flying is already posed by setupAnim, and sleep is
        // inherited from the host renderer's lay-down transform, so neither needs handling here.
        if (anim.isHumanoid(Animations.State.FLY) && creativeFlying(entity)) {
            net.minecraft.world.phys.Vec3 vel = entity.getDeltaMovement();
            float speed = (float) Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            float lean = Math.min(speed * FLY_LEAN_PER_SPEED, FLY_MAX_LEAN);
            if (lean > 0.5f) {
                pose.translate(0f, FLY_PIVOT_Y, 0f);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(lean));
                pose.translate(0f, -FLY_PIVOT_Y, 0f);
            }
        }
        // Per-entity tint: MCA's per-villager skin value shifted by the origin's authored skin_tone,
        // so a village spans a palette of tones instead of every rig being identical.
        int tone = RigSkinTone.forEntity(entity);
        //? if neoforge {
        model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, tone);
        //?} else {
        /*model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY,
                ((tone >> 16) & 0xFF) / 255f, ((tone >> 8) & 0xFF) / 255f, (tone & 0xFF) / 255f,
                ((tone >>> 24) & 0xFF) / 255f);
        *///?}

        // Custom face (eyes/mouth) drawn on the now-posed head bone, inside the scaled pose so it
        // tracks the head. Resolved from the entity's face genes; a no-op for rigs without them.
        SpeciesFace.render(entity, rigBase, pose, buffers, light, partialTick);

        // Worn armor, fitted to the rig (same model proportions, pose, and scale), drawn before held
        // items like vanilla. The rig model is already posed above, which the armor layer copies from.
        // MCA's villager-shaped host armor is suppressed for these entities by HostArmorSuppressMixin.
        if (renderArmor) {
            RigArmorRenderer.render(entity, rigBase, model, texture, pose, buffers, light,
                    limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
        }

        // Held items, anchored to the rig bone the species names for each hand, inside the same
        // scaled pose so they match the grip. A null grip (hand can't hold) renders nothing. The
        // vanilla item layer is suppressed for alternate rigs by HeldItemSuppressMixin.
        renderHeld(entity, rigBase, entity.getMainHandItem(), pose, buffers, light, scale, false);
        renderHeld(entity, rigBase, entity.getOffhandItem(), pose, buffers, light, scale, true);
        pose.popPose();
    }

    /**
     * Render a non-humanoid rig: bake/instantiate the vanilla model (e.g. a spider), let its own
     * {@code setupAnim} pose the body plan from the entity's walk state, and draw it with the per-entity
     * tint. No humanoid extras (arm pose, crouch, held items, fitted armor) apply; a custom face still
     * draws if the rig declares one, since it resolves its bone by name from the baked root.
     */
    private void renderGeneric(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                               float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                               float netHeadYaw, float headPitch, String rigBase) {
        EntityModel<LivingEntity> model = RigModels.genericModel(rigBase);
        ResourceLocation texture = RigModels.texture(rigBase);
        if (model == null || texture == null) return;
        model.attackTime = entity.getAttackAnim(partialTick);
        model.young = entity.isBaby();
        model.riding = entity.isPassenger();
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer buffer = buffers.getBuffer(model.renderType(texture));
        float scale = hostBaseline * RigModels.scaleFor(entity);
        pose.pushPose();
        if (scale != 1f) {
            pose.translate(0f, 1.501f, 0f);
            pose.scale(scale, scale, scale);
            pose.translate(0f, -1.501f, 0f);
        }
        int tone = RigSkinTone.forEntity(entity);
        //? if neoforge {
        model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY, tone);
        //?} else {
        /*model.renderToBuffer(pose, buffer, light, OverlayTexture.NO_OVERLAY,
                ((tone >> 16) & 0xFF) / 255f, ((tone >> 8) & 0xFF) / 255f, (tone & 0xFF) / 255f,
                ((tone >>> 24) & 0xFF) / 255f);
        *///?}
        SpeciesFace.render(entity, rigBase, pose, buffers, light, partialTick);
        // Worn boots laid across the rig's legs (e.g. one per spider leg), fitted with full vanilla
        // armor fidelity. The leg bones are now posed by the setupAnim above, so the boots track.
        SpiderBootsRenderer.render(entity, rigBase, RigModels.definition(rigBase), pose, buffers, light);
        pose.popPose();
    }

    /**
     * Fully pose the rig model from the entity: the per-frame render state ({@link #prepareModel}),
     * the model's own {@code setupAnim} (walk/idle/crouch base pose), then the shared animation bridge
     * so Fresh-Animations/EMF (resolved via the species' provider chain), the origin pose layer, and
     * Emotecraft land on its bones, layered EMF -&gt; Origin -&gt; Emote. The rig is a plain
     * {@link HumanoidModel}, which the bridge handles. Shared with {@link RigArmorSync} so the host
     * body model (the armor's pose source) can be driven from this exact pose.
     */
    public static void poseRig(HumanoidModel<LivingEntity> model, LivingEntity entity, Animations anim,
                               float limbSwing, float limbSwingAmount, float ageInTicks,
                               float netHeadYaw, float headPitch, float partialTick) {
        prepareModel(model, entity, partialTick, anim);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // Route the bridge through the rig's bone map, so sources land on the bones the rig names for
        // each channel. For a vanilla body the map is identity, so this matches the old behavior.
        String rigBase = RigModels.rigBaseFor(entity);
        McaAnimationBridge.applyRig(entity, model, RigModels.root(rigBase), RigModels.definition(rigBase),
                limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    /**
     * Apply the animation state {@code LivingEntityRenderer} sets on its model before {@code
     * setupAnim}. Our rig model is a standalone instance the renderer never touches, so without this
     * the arm stays in its idle pose and held items never swing.
     */
    private static void prepareModel(HumanoidModel<LivingEntity> model, LivingEntity entity, float partialTick,
                                     Animations anim) {
        model.attackTime = entity.getAttackAnim(partialTick);
        model.young = entity.isBaby();
        // crouch: the humanoid bend, unless the species opts this rig out of crouching.
        model.crouching = anim.isHumanoid(Animations.State.CROUCH) && entity.isCrouching();
        model.riding = entity.isPassenger();
        net.minecraft.world.entity.HumanoidArm mainArm = entity.getMainArm();
        HumanoidModel.ArmPose mainPose = armPose(entity, net.minecraft.world.InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = armPose(entity, net.minecraft.world.InteractionHand.OFF_HAND);
        if (mainArm == net.minecraft.world.entity.HumanoidArm.RIGHT) {
            model.rightArmPose = mainPose;
            model.leftArmPose = offPose;
        } else {
            model.leftArmPose = mainPose;
            model.rightArmPose = offPose;
        }
    }

    /** True when the entity is a player aloft under creative/spectator flight (not standing). */
    private static boolean creativeFlying(LivingEntity entity) {
        return entity instanceof net.minecraft.world.entity.player.Player player
                && player.getAbilities().flying && !entity.onGround();
    }

    /** The arm pose for a hand, mirroring {@code HumanoidMobRenderer}: empty hand vs holding an item. */
    private static HumanoidModel.ArmPose armPose(LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        net.minecraft.world.item.UseAnim use = entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0
                ? stack.getUseAnimation() : null;
        if (use == net.minecraft.world.item.UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
        if (use == net.minecraft.world.item.UseAnim.BLOCK) return HumanoidModel.ArmPose.BLOCK;
        if (use == net.minecraft.world.item.UseAnim.SPEAR) return HumanoidModel.ArmPose.THROW_SPEAR;
        return HumanoidModel.ArmPose.ITEM;
    }

    /**
     * Render a held item anchored to the species' authored grip bone, mirroring vanilla
     * {@code ItemInHandLayer}'s standard grip frame on top of the bone's animated transform. The
     * rig scale is undone for the item itself so it stays normal-sized (anchored to the scaled
     * bone, but not blown up with the body). The hand's side (and so the item display context) is
     * taken from the bone name, and the authored offset/rotation nudge the grip.
     */
    private static void renderHeld(LivingEntity entity, String rigBase,
                                   net.minecraft.world.item.ItemStack stack, PoseStack pose,
                                   MultiBufferSource buffers, int light, float rigScale, boolean offHand) {
        if (stack.isEmpty()) return;
        Hold.Grip grip = RigModels.holdGrip(entity, offHand);
        if (grip == null) return;
        ModelPart bone = RigModels.bone(rigBase, grip.bone());
        if (bone == null) return;
        boolean left = grip.bone().contains("left");
        pose.pushPose();
        bone.translateAndRotate(pose);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        pose.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);
        // Undo the rig scale only now, AFTER reaching the (scaled) bone, so the item lands at the
        // bone at normal size instead of being pulled up toward the bone's pivot.
        if (rigScale != 1f) pose.scale(1f / rigScale, 1f / rigScale, 1f / rigScale);
        // The species' authored grip nudge (data-driven): offset in pixels, then rotation in degrees.
        float[] off = grip.offset();
        pose.translate(off[0] / 16f, off[1] / 16f, off[2] / 16f);
        float[] rot = grip.rotation();
        if (rot[0] != 0f) pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rot[0]));
        if (rot[1] != 0f) pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rot[1]));
        if (rot[2] != 0f) pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rot[2]));
        net.minecraft.world.item.ItemDisplayContext ctx = left
                ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                .renderItem(entity, stack, ctx, left, pose, buffers, light);
        pose.popPose();
    }
}
