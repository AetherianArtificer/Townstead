package com.aetherianartificer.townstead.client.species;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Pushes every vertex out along its face normal, so a layer drawn on the same model as the body
 * under it (a rig's worn skin, a hand's skin overlay) sits just off that surface instead of on it.
 * Coplanar translucent layers z-fight: neither wins the depth test reliably once the body fades.
 * This is MCA's clothing-layer cube dilation, applied at draw time to an already-baked model.
 */
public final class ShellOffset {

    /** One MCA clothing dilation (0.0625 model pixels), in blocks. */
    public static final float CLOTHING = 0.0625f / 16f;

    private ShellOffset() {}

    public static VertexConsumer wrap(VertexConsumer buffer, float distance) {
        return distance == 0f ? buffer : new Shell(buffer, distance);
    }

    private static final class Shell implements VertexConsumer {
        private final VertexConsumer target;
        private final float distance;

        Shell(VertexConsumer target, float distance) {
            this.target = target;
            this.distance = distance;
        }

        //? if neoforge {
        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            target.addVertex(x + nx * distance, y + ny * distance, z + nz * distance,
                    color, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            target.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            target.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            target.setNormal(x, y, z);
            return this;
        }
        //?} else {
        /*@Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v,
                           int overlay, int light, float nx, float ny, float nz) {
            target.vertex(x + nx * distance, y + ny * distance, z + nz * distance,
                    r, g, b, a, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            target.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            target.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            target.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            target.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            target.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            target.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            target.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            target.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            target.unsetDefaultColor();
        }
        *///?}
    }
}
