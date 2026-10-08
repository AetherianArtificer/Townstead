package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;

/** Pack-authored armor meshes using vanilla armor materials, dye, trims and glint. */
public final class RigGeometryArmor {
    private static final Map<String, HumanoidModel<LivingEntity>> MODELS = new HashMap<>();
    private RigGeometryArmor() {}
    /** Each armor layer keeps its own authored fit when vanilla copies the posed parent model. */
    public static final class FittedModel extends HumanoidModel<LivingEntity> {
        private final String rig;
        private final ModelPart[] parts;
        private final float[][] scales;

        public FittedModel(ModelPart root, String rig) {
            super(root);
            // The enclosing generic-rig pose owns age/size scaling, not HumanoidModel.
            young = false;
            this.rig = rig;
            parts = new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg};
            scales = new float[parts.length][3];
            for (int i = 0; i < parts.length; i++)
                scales[i] = new float[]{parts[i].xScale, parts[i].yScale, parts[i].zScale};
        }

        void pose() {
            var def = RigModels.definition(rig);
            String[] channels = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
            for (int i = 0; i < parts.length; i++) {
                parts[i].xScale = scales[i][0]; parts[i].yScale = scales[i][1]; parts[i].zScale = scales[i][2];
                follow(parts[i], rig, def == null ? channels[i] : def.boneFor(channels[i]));
            }
            hat.copyFrom(head);
        }

        @Override
        public void copyPropertiesTo(HumanoidModel<LivingEntity> target) {
            super.copyPropertiesTo(target);
            if (target instanceof FittedModel fitted) fitted.pose();
        }

        // Geometry armour is already authored in the same ground-origin frame as the body.
        // AgeableListModel's head/body baby offsets corrupt that frame (EntityModel even
        // defaults young to true). Draw the bones directly, like the hierarchical rig body,
        // regardless of flags copied by a host renderer. Slot visibility still applies.
        //? if neoforge {
        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
            for (ModelPart part : headParts()) part.render(pose, buffer, light, overlay, color);
            for (ModelPart part : bodyParts()) part.render(pose, buffer, light, overlay, color);
        }
        //?} else {
        /*@Override
        public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                                   float red, float green, float blue, float alpha) {
            for (ModelPart part : headParts()) part.render(pose, buffer, light, overlay, red, green, blue, alpha);
            for (ModelPart part : bodyParts()) part.render(pose, buffer, light, overlay, red, green, blue, alpha);
        }
        *///?}
    }
    public static void clear() { MODELS.clear(); }
    /** Curios renderers copying the host model must see the creature's limbs. */
    public static void poseHostLimbs(HumanoidModel<?> host, RigDefinition def) {
        HumanoidModel<LivingEntity> source = source(def.id());
        if (source == null) return;
        follow(source.rightArm, def.id(), def.boneFor("right_arm"));
        follow(source.leftArm, def.id(), def.boneFor("left_arm"));
        follow(source.rightLeg, def.id(), def.boneFor("right_leg"));
        follow(source.leftLeg, def.id(), def.boneFor("left_leg"));
        host.rightArm.copyFrom(source.rightArm);
        host.leftArm.copyFrom(source.leftArm);
        host.rightLeg.copyFrom(source.rightLeg);
        host.leftLeg.copyFrom(source.leftLeg);
    }

    private static HumanoidModel<LivingEntity> source(String rig) {
        var model = MODELS.get(rig);
        if (model == null) {
            ModelPart outer = RigModels.bakeArmorPart(rig, false);
            if (outer == null || RigModels.bakeArmorPart(rig, true) == null) return null;
            model = new FittedModel(outer, rig);
            MODELS.put(rig, model);
        }
        return model;
    }
    public static boolean enabled(String rig) {
        var def = RigModels.definition(rig);
        return def != null && RigModels.isGeneric(rig) && def.armorType() == RigDefinition.ArmorType.CUSTOM;
    }
    public static void render(LivingEntity entity, String rig, PoseStack pose, MultiBufferSource buffers,
                              int light, float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (!enabled(rig)) return;
        var source = source(rig);
        if (source == null) return; // Synced geometry may still be arriving.
        RigDefinition def = RigModels.definition(rig);
        follow(source.head, rig, def.boneFor("head"));
        source.hat.copyFrom(source.head);
        follow(source.body, rig, def.boneFor("body"));
        follow(source.rightArm, rig, def.boneFor("right_arm"));
        follow(source.leftArm, rig, def.boneFor("left_arm"));
        follow(source.rightLeg, rig, def.boneFor("right_leg"));
        follow(source.leftLeg, rig, def.boneFor("left_leg"));
        RigArmorRenderer.render(entity, rig, source, RigModels.texture(rig), pose, buffers, light,
                swing, amount, partial, age, yaw, pitch);
    }
    private static Matrix4f matrix(float[] p) {
        return new Matrix4f().translation(p[0], p[1], p[2]).rotateZYX(p[5], p[4], p[3]);
    }
    private static void follow(ModelPart target, String rig, String bone) {
        // resetPose also resets scale to 1; preserve the pack-authored armor fit.
        var initial = target.getInitialPose();
        target.setPos(initial.x, initial.y, initial.z);
        target.setRotation(initial.xRot, initial.yRot, initial.zRot);
        float[] live = RigModels.boneModelPose(rig, bone);
        float[] rest = RigModels.boneRestPose(rig, bone);
        if (live == null || rest == null) return;
        var authored = target.getInitialPose();
        Matrix4f transform = matrix(live).mul(matrix(rest).invert())
                .translate(authored.x, authored.y, authored.z)
                .rotateZYX(authored.zRot, authored.yRot, authored.xRot);
        Vector3f position = transform.getTranslation(new Vector3f());
        Vector3f angles = transform.getEulerAnglesZYX(new Vector3f());
        target.setPos(position.x, position.y, position.z);
        target.setRotation(angles.x, angles.y, angles.z);
    }
}
