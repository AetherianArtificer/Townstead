package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.aetherianartificer.townstead.client.devlink.BenchGizmos;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.aetherianartificer.townstead.root.rig.RigHitboxes;
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
 * entity frame the physics sim uses (origin at the feet, world axes). A body with no
 * rig file uses the humanoid defaults.
 */
public final class AttachmentColliders {

    /** One placed capsule, in blocks; equal ends make a sphere. */
    public record Capsule(String name, Vector3f a, Vector3f b, float radius) {

        /** The point on the capsule's core segment nearest {@code p}, into {@code dest}. */
        public Vector3f closest(Vector3f p, Vector3f dest) {
            float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
            float lengthSq = abx * abx + aby * aby + abz * abz;
            float t = lengthSq < 1e-10f ? 0f
                    : ((p.x - a.x) * abx + (p.y - a.y) * aby + (p.z - a.z) * abz) / lengthSq;
            t = Math.max(0f, Math.min(1f, t));
            return dest.set(a.x + abx * t, a.y + aby * t, a.z + abz * t);
        }
    }

    private AttachmentColliders() {}

    /** Bench gizmos: every placed collider as a wire capsule; {@code pose} at the model root. */
    public static void draw(LivingEntity entity, PoseStack pose, MultiBufferSource buffers, List<Capsule> capsules) {
        Matrix4f root = EntityRootFrames.rootFor(entity);
        if (root == null || capsules.isEmpty()) return;
        Matrix4f toLocal = new Matrix4f(root).invert().mul(pose.last().pose()).invert();
        float scale = toLocal.getScale(new Vector3f()).x;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Capsule capsule : capsules) {
            BenchGizmos.capsule(lines, pose.last(), toLocal.transformPosition(new Vector3f(capsule.a())),
                    toLocal.transformPosition(new Vector3f(capsule.b())), capsule.radius() * scale,
                    BenchGizmos.COLLIDER);
        }
    }

    public static List<Capsule> gather(LivingEntity entity, PoseStack pose, AnimationTargetMap<?> bones) {
        Matrix4f root = EntityRootFrames.rootFor(entity);
        if (root == null) return List.of();
        RigDefinition rig = RigHitboxes.definition(entity);
        List<RigDefinition.Collider> defs = rig == null ? RigDefinition.HUMANOID_COLLIDERS : rig.colliders();
        if (defs.isEmpty()) return List.of();
        Matrix4f toEntity = new Matrix4f(root).invert();
        List<Capsule> out = new ArrayList<>(defs.size());
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
            out.add(new Capsule(def.name(), a, b, def.radius() / 16f * scale));
        }
        return out;
    }
}
