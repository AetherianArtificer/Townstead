package com.aetherianartificer.townstead.client.devlink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

/**
 * Small axis tripods drawn in a model frame while the Bench Link item is held: red X, green Y,
 * blue Z, in Java model space (Y points down the body). The plugin draws the same tripods, so
 * a mismatch shows by eye before any number is compared. Attachment physics draws its
 * colliders, chain points and swing limits with the same lines.
 */
public final class BenchGizmos {

    /** Attachment points: full tripod. */
    public static final float POINT = 2f / 16f;
    /** Attachment instances: a shorter tripod, so it reads apart from the point it sits on. */
    public static final float ATTACHMENT = 1.25f / 16f;

    /** Physics colliders. */
    public static final int COLLIDER = 0x40D0E0;
    /** Simulated chain points. */
    public static final int CHAIN = 0xF0D040;
    /** Where the animated pose puts each chain point. */
    public static final int CHAIN_GOAL = 0x909090;
    /** Each chain segment's swing limit. */
    public static final int CHAIN_LIMIT = 0xF08030;
    private static final int CIRCLE_STEPS = 16;

    private BenchGizmos() {}

    public static void tripod(PoseStack pose, MultiBufferSource buffers, float length) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        line(lines, last, length, 0, 0, 230, 60, 60);
        line(lines, last, 0, length, 0, 60, 200, 60);
        line(lines, last, 0, 0, length, 70, 110, 240);
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float x, float y, float z, int r, int g, int b) {
        segment(lines, pose, 0, 0, 0, x, y, z, r, g, b);
    }

    /** One line from {@code a} to {@code b} in the pose's frame; {@code rgb} is 0xRRGGBB. */
    public static void segment(VertexConsumer lines, PoseStack.Pose pose, Vector3f a, Vector3f b, int rgb) {
        segment(lines, pose, a.x, a.y, a.z, b.x, b.y, b.z, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
    }

    /** A circle of {@code radius} around {@code center}, facing along {@code axis}. */
    public static void circle(VertexConsumer lines, PoseStack.Pose pose, Vector3f center, Vector3f axis,
                              float radius, int rgb) {
        if (axis.lengthSquared() < 1e-10f || radius <= 0f) return;
        Vector3f n = new Vector3f(axis).normalize();
        Vector3f u = Math.abs(n.y) < 0.9f ? new Vector3f(0, 1, 0).cross(n) : new Vector3f(1, 0, 0).cross(n);
        u.normalize();
        Vector3f v = new Vector3f(n).cross(u);
        Vector3f last = new Vector3f(center).fma(radius, u);
        Vector3f next = new Vector3f();
        for (int i = 1; i <= CIRCLE_STEPS; i++) {
            float angle = (float) (Math.PI * 2 * i / CIRCLE_STEPS);
            next.set(center).fma(radius * (float) Math.cos(angle), u).fma(radius * (float) Math.sin(angle), v);
            segment(lines, pose, last, next, rgb);
            last.set(next);
        }
    }

    /** A wire capsule: rings at both ends in three planes, joined along the core. */
    public static void capsule(VertexConsumer lines, PoseStack.Pose pose, Vector3f a, Vector3f b, float radius, int rgb) {
        Vector3f core = new Vector3f(b).sub(a);
        Vector3f axis = core.lengthSquared() < 1e-10f ? new Vector3f(0, 1, 0) : new Vector3f(core).normalize();
        Vector3f side = Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0).cross(axis) : new Vector3f(1, 0, 0).cross(axis);
        side.normalize();
        Vector3f other = new Vector3f(axis).cross(side);
        for (Vector3f end : new Vector3f[]{a, b}) {
            circle(lines, pose, end, axis, radius, rgb);
            circle(lines, pose, end, side, radius, rgb);
            circle(lines, pose, end, other, radius, rgb);
        }
        for (Vector3f offset : new Vector3f[]{side, other, new Vector3f(side).negate(), new Vector3f(other).negate()}) {
            segment(lines, pose, new Vector3f(a).fma(radius, offset), new Vector3f(b).fma(radius, offset), rgb);
        }
    }

    private static void segment(VertexConsumer lines, PoseStack.Pose pose, float ax, float ay, float az,
                                float bx, float by, float bz, int r, int g, int b) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        //? if >=1.21 {
        lines.addVertex(pose, ax, ay, az).setColor(r, g, b, 255).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, bx, by, bz).setColor(r, g, b, 255).setNormal(pose, nx, ny, nz);
        //?} else {
        /*lines.vertex(pose.pose(), ax, ay, az).color(r, g, b, 255).normal(pose.normal(), nx, ny, nz).endVertex();
        lines.vertex(pose.pose(), bx, by, bz).color(r, g, b, 255).normal(pose.normal(), nx, ny, nz).endVertex();
        *///?}
    }
}
