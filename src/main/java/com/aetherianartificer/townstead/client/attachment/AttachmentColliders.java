package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.aetherianartificer.townstead.client.devlink.BenchGizmos;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.root.attachment.ChainSim;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The bearer's physics colliders for one render, placed on the live pose in the same
 * entity frame the physics sim uses (origin at the feet, world axes). A body on the
 * humanoid render path that declares none uses the humanoid defaults.
 */
public final class AttachmentColliders {

    private AttachmentColliders() {}

    /** Bench gizmos: every placed collider as a wire capsule; {@code pose} at the model root. */
    public static void draw(LivingEntity entity, PoseStack pose, MultiBufferSource buffers, List<ChainSim.Capsule> capsules) {
        Matrix4f root = EntityRootFrames.rootFor(entity);
        if (root == null || capsules.isEmpty()) return;
        Matrix4f toLocal = new Matrix4f(root).invert().mul(pose.last().pose()).invert();
        float scale = toLocal.getScale(new Vector3f()).x;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (ChainSim.Capsule capsule : capsules) {
            BenchGizmos.capsule(lines, pose.last(), toLocal.transformPosition(new Vector3f(capsule.a())),
                    toLocal.transformPosition(new Vector3f(capsule.b())), capsule.radius() * scale,
                    BenchGizmos.COLLIDER);
        }
    }

    /** The rig's own colliders, or the humanoid set for a humanoid-shaped body that declares none. */
    static List<RigDefinition.Collider> definitions(String rigBase) {
        RigDefinition rig = RigModels.isAlternate(rigBase) ? RigModels.definition(rigBase) : null;
        if (rig == null) return RigDefinition.HUMANOID_COLLIDERS;
        if (rig.colliders() != null) return rig.colliders();
        return RigModels.isGeneric(rigBase) ? List.of() : RigDefinition.HUMANOID_COLLIDERS;
    }

    public static List<ChainSim.Capsule> gather(LivingEntity entity, PoseStack pose, AnimationTargetMap<?> bones) {
        Matrix4f root = EntityRootFrames.rootFor(entity);
        if (root == null) return List.of();
        List<RigDefinition.Collider> defs = definitions(RigModels.rigBaseFor(entity));
        if (defs.isEmpty()) return List.of();
        Matrix4f toEntity = new Matrix4f(root).invert();
        List<ChainSim.Capsule> out = new ArrayList<>(defs.size());
        for (RigDefinition.Collider def : defs) {
            ModelPart part = bones.resolve(def.bone()).orElse(null);
            if (part == null) continue;
            pose.pushPose();
            part.translateAndRotate(pose);
            Matrix4f frame = new Matrix4f(toEntity).mul(pose.last().pose());
            pose.popPose();
            Vector3f a = frame.transformPosition(def.from()[0] / 16f, def.from()[1] / 16f, def.from()[2] / 16f,
                    new Vector3f());
            Vector3f b = frame.transformPosition(def.to()[0] / 16f, def.to()[1] / 16f, def.to()[2] / 16f,
                    new Vector3f());
            float scale = frame.getScale(new Vector3f()).x;
            out.add(new ChainSim.Capsule(def.name(), a, b, def.radius() / 16f * scale));
        }
        return out;
    }
}
