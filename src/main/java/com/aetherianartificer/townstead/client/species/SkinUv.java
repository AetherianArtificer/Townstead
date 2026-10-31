package com.aetherianartificer.townstead.client.species;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fits a 64x64 MCA outfit to a rig body whose model is laid out for another texture size (a Stray
 * or Wither Skeleton is 64x32): its UVs are scaled at draw time, so the head samples the outfit's
 * head and not half of it plus the arms below.
 */
final class SkinUv {

    private static final float SKIN_SIZE = 64f;
    private static final float[] NONE = {1f, 1f};
    private static final Map<ModelPart, float[]> SCALES = new WeakHashMap<>();

    private SkinUv() {}

    /** {@code buffer} with UVs scaled for this model, or {@code buffer} itself when it already fits. */
    static VertexConsumer fit(HumanoidModel<?> model, VertexConsumer buffer) {
        float[] scale = SCALES.computeIfAbsent(model.head, SkinUv::measure);
        return scale == NONE ? buffer : new Scaled(buffer, scale[0], scale[1]);
    }

    // A humanoid head is an 8x8x8 cube at the texture's corner, so its UVs span 32x16 pixels: the
    // largest UV it draws gives the texture size the model was built for.
    private static float[] measure(ModelPart head) {
        Scaled probe = new Scaled(null, 1f, 1f);
        boolean visible = head.visible;
        head.visible = true;
        try {
            head.render(new PoseStack(), probe, 0, 0);
        } finally {
            head.visible = visible;
        }
        if (probe.maxU <= 0f || probe.maxV <= 0f) return NONE;
        float width = Math.round(32f / probe.maxU);
        float height = Math.round(16f / probe.maxV);
        if (width == SKIN_SIZE && height == SKIN_SIZE) return NONE;
        if (width < 16f || height < 16f || width > 1024f || height > 1024f) return NONE;
        return new float[] {width / SKIN_SIZE, height / SKIN_SIZE};
    }

    /** Passes vertices on with scaled UVs; with no target it only measures them. */
    private static final class Scaled implements VertexConsumer {
        private final @Nullable VertexConsumer target;
        private final float su;
        private final float sv;
        float maxU;
        float maxV;

        Scaled(@Nullable VertexConsumer target, float su, float sv) {
            this.target = target;
            this.su = su;
            this.sv = sv;
        }

        //? if neoforge {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (target != null) target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (target != null) target.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
            if (target != null) target.setUv(u * su, v * sv);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (target != null) target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (target != null) target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (target != null) target.setNormal(x, y, z);
            return this;
        }
        //?} else {
        /*@Override
        public VertexConsumer vertex(double x, double y, double z) {
            if (target != null) target.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            if (target != null) target.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
            if (target != null) target.uv(u * su, v * sv);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            if (target != null) target.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            if (target != null) target.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            if (target != null) target.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            if (target != null) target.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            if (target != null) target.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            if (target != null) target.unsetDefaultColor();
        }
        *///?}
    }
}
