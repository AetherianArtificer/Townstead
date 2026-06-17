package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.origin.rig.RigDefinition;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Re-poses a host humanoid's {@code body} and {@code head} bones onto a non-humanoid rig's real back
 * and head, so worn render layers that anchor to those bones (Backpacked/cape/elytra on the body,
 * helmet/pumpkin/mob-head on the head) land on the rig instead of floating at the default humanoid
 * chest/head. The host bones are invisible for an alternate rig (their layers are suppressed), so
 * moving them only affects those external attachments.
 *
 * <p>Each anchor is placed RELATIVE TO the matching rig bone: the host bone is moved onto the rig
 * bone's rest position (resolved through the rig's bone map — {@code body}-&gt;abdomen, {@code head}),
 * then the authored {@code offset}/{@code rotation} is a small nudge from there. So a pack author tunes
 * a pixel or two off the actual body part instead of typing the whole gap from the humanoid pose. The
 * spaces line up when the rig renders at scale 1.0; a scaled rig would need a scale correction here.</p>
 *
 * <p>The base anchor is applied once in the host model's {@code setupAnim}, which every back layer then
 * reads. A specific back layer can be nudged further: its render mixin calls {@link #applyItem} just
 * before it reads the bone, re-posing it to that layer's base+delta.</p>
 */
public final class RigWearables {

    private RigWearables() {}

    // The host body bone last posed by the base anchor this frame, and the rig bone it was anchored to.
    // Rendering is single-threaded and ordered setupAnim -> model -> layers per entity, so these are the
    // current entity's while its back layers render. Held so a layer mixin can re-pose without re-resolving.
    private static ModelPart anchoredBody;
    private static ModelPart anchoredBackBone;

    /**
     * Hide the host's worn boots on a non-humanoid rig by zeroing the host model's leg scale, which the
     * armor layer copies onto its own legs (so the boots draw at zero size). The helmet/chest/back path
     * is untouched, so the helmet still renders normally. {@code generic} false RESETS the legs to 1,
     * but only when they were left zeroed by a previous generic entity on this shared model — so it does
     * not fight any other per-frame leg scaler (e.g. the proportions gene).
     */
    public static void suppressHostBoots(HumanoidModel<?> host, boolean generic) {
        if (generic) {
            setLegScale(host, 0f);
        } else if (host.leftLeg.xScale == 0f || host.rightLeg.xScale == 0f) {
            setLegScale(host, 1f);
        }
    }

    private static void setLegScale(HumanoidModel<?> host, float scale) {
        host.leftLeg.xScale = scale;
        host.leftLeg.yScale = scale;
        host.leftLeg.zScale = scale;
        host.rightLeg.xScale = scale;
        host.rightLeg.yScale = scale;
        host.rightLeg.zScale = scale;
    }

    /**
     * Pose the host bones a non-humanoid rig declares anchors for: the body bone onto the rig's back
     * bone (remembered for per-item nudges) and the head bone onto the rig's head bone. The head also
     * takes this frame's look ({@code headYaw}/{@code headPitch}, the head-vs-body angles passed to
     * {@code setupAnim}), so a worn helmet turns with the head exactly like the visible head does.
     * Returns true if it posed anything, so the caller can skip the humanoid animation bridge.
     */
    public static boolean anchor(HumanoidModel<?> host, RigDefinition def, float headYaw, float headPitch) {
        boolean posed = false;
        RigModels.genericModel(def.id()); // ensure the rig root is baked so its bones resolve
        if (def.back() != null) {
            anchoredBody = host.body;
            anchoredBackBone = RigModels.bakedBone(def.id(), def.boneFor("body"));
            poseAt(anchoredBody, anchoredBackBone, def.back().base(), false, 0f, 0f);
            posed = true;
        }
        if (def.head() != null) {
            poseAt(host.head, RigModels.bakedBone(def.id(), def.boneFor("head")), def.head(), true, headYaw, headPitch);
            posed = true;
        }
        return posed;
    }

    /**
     * Re-pose the stashed body bone to a specific layer's base+delta, called from that layer's render
     * mixin. A no-op unless the entity actually has a generic rig with a back anchor this frame, so a
     * normal player's cape/elytra/backpack is never touched.
     */
    public static void applyItem(LivingEntity entity, String key) {
        if (anchoredBody == null) return;
        String rigBase = RigModels.rigBaseFor(entity);
        if (!RigModels.isGeneric(rigBase)) return;
        RigDefinition def = RigModels.definition(rigBase);
        if (def == null || def.back() == null) return;
        poseAt(anchoredBody, anchoredBackBone, def.back().forItem(key), false, 0f, 0f);
    }

    /**
     * Pose {@code target} onto {@code source}'s rest position, with rotation = (this frame's look when
     * {@code trackLook}, else the source bone's rest rotation) plus the adjust's rotation delta. The
     * adjust's offset is applied in the bone's LOCAL (rotated) frame — so a wearable stays rigidly glued
     * to the bone and swings with it (a helmet on the head's look), instead of sitting on the rotation
     * axis and being left behind. With {@code source} null (rig bone not found) it falls back to the
     * host bone's own rest position.
     */
    private static void poseAt(ModelPart target, ModelPart source, RigDefinition.Adjust a,
                               boolean trackLook, float headYaw, float headPitch) {
        target.resetPose();
        float baseX, baseY, baseZ, baseXRot, baseYRot, baseZRot;
        if (source != null) {
            baseX = source.x; baseY = source.y; baseZ = source.z;
            baseXRot = source.xRot; baseYRot = source.yRot; baseZRot = source.zRot;
        } else {
            baseX = target.x; baseY = target.y; baseZ = target.z;
            baseXRot = baseYRot = baseZRot = 0f;
        }
        // The head turns with this frame's look (the rig bone's own rotation is a frame stale); other
        // bones keep their rest rotation.
        if (trackLook) {
            baseXRot = (float) Math.toRadians(headPitch);
            baseYRot = (float) Math.toRadians(headYaw);
            baseZRot = 0f;
        }
        float[] rotation = a.rotation();
        float xRot = baseXRot + (float) Math.toRadians(rotation[0]);
        float yRot = baseYRot + (float) Math.toRadians(rotation[1]);
        float zRot = baseZRot + (float) Math.toRadians(rotation[2]);
        // Offset in the bone's local frame: rotate it by the bone's orientation (ModelPart's own ZYX
        // order) before adding to the pivot, so the wearable rides the rotation.
        float[] offset = a.offset();
        org.joml.Vector3f local = new org.joml.Vector3f(offset[0], offset[1], offset[2]);
        new org.joml.Quaternionf().rotationZYX(zRot, yRot, xRot).transform(local);
        target.x = baseX + local.x();
        target.y = baseY + local.y();
        target.z = baseZ + local.z();
        target.xRot = xRot;
        target.yRot = yRot;
        target.zRot = zRot;
    }
}
