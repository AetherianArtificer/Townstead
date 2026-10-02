package com.aetherianartificer.townstead.client.devlink;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Small axis tripods drawn in a model frame while the Bench Link item is held: red X, green Y,
 * blue Z, in Java model space (Y points down the body). The plugin draws the same tripods, so
 * a mismatch shows by eye before any number is compared.
 */
public final class BenchGizmos {

    /** Attachment points: full tripod. */
    public static final float POINT = 2f / 16f;
    /** Attachment instances: a shorter tripod, so it reads apart from the point it sits on. */
    public static final float ATTACHMENT = 1.25f / 16f;

    private BenchGizmos() {}

    public static void tripod(PoseStack pose, MultiBufferSource buffers, float length) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        line(lines, last, length, 0, 0, 230, 60, 60);
        line(lines, last, 0, length, 0, 60, 200, 60);
        line(lines, last, 0, 0, length, 70, 110, 240);
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float x, float y, float z, int r, int g, int b) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        float nx = x / len, ny = y / len, nz = z / len;
        //? if >=1.21 {
        lines.addVertex(pose, 0, 0, 0).setColor(r, g, b, 255).setNormal(pose, nx, ny, nz);
        lines.addVertex(pose, x, y, z).setColor(r, g, b, 255).setNormal(pose, nx, ny, nz);
        //?} else {
        /*lines.vertex(pose.pose(), 0, 0, 0).color(r, g, b, 255).normal(pose.normal(), nx, ny, nz).endVertex();
        lines.vertex(pose.pose(), x, y, z).color(r, g, b, 255).normal(pose.normal(), nx, ny, nz).endVertex();
        *///?}
    }
}
