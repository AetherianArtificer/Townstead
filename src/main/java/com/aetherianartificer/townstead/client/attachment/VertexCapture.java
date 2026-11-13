package com.aetherianartificer.townstead.client.attachment;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertex buffer that keeps what a model draws instead of drawing it. Any model, vanilla or authored,
 * can hand over its faces this way, with no access to its parts and no layout to know about.
 */
final class VertexCapture implements VertexConsumer {

    private final List<float[]> vertices = new ArrayList<>();
    private float x, y, z, u, v, nx, ny, nz;

    /** What was drawn, as it arrived: position, texture position, then normal, four to a face. */
    List<float[]> vertices() {
        return vertices;
    }

    private void keep() {
        vertices.add(new float[]{x, y, z, u, v, nx, ny, nz});
    }

    //? if neoforge {
    @Override
    public void addVertex(float px, float py, float pz, int color, float tu, float tv, int overlay, int light,
                          float tnx, float tny, float tnz) {
        vertices.add(new float[]{px, py, pz, tu, tv, tnx, tny, tnz});
    }

    @Override
    public VertexConsumer addVertex(float px, float py, float pz) {
        x = px;
        y = py;
        z = pz;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float tu, float tv) {
        u = tu;
        v = tv;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int a, int b) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int a, int b) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float tnx, float tny, float tnz) {
        nx = tnx;
        ny = tny;
        nz = tnz;
        keep();
        return this;
    }
    //?} else {
    /*@Override
    public void vertex(float px, float py, float pz, float r, float g, float b, float a, float tu, float tv,
                       int overlay, int light, float tnx, float tny, float tnz) {
        vertices.add(new float[]{px, py, pz, tu, tv, tnx, tny, tnz});
    }

    @Override
    public VertexConsumer vertex(double px, double py, double pz) {
        x = (float) px;
        y = (float) py;
        z = (float) pz;
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer uv(float tu, float tv) {
        u = tu;
        v = tv;
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int a, int b) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int a, int b) {
        return this;
    }

    @Override
    public VertexConsumer normal(float tnx, float tny, float tnz) {
        nx = tnx;
        ny = tny;
        nz = tnz;
        return this;
    }

    @Override
    public void endVertex() {
        keep();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {}

    @Override
    public void unsetDefaultColor() {}
    *///?}
}
