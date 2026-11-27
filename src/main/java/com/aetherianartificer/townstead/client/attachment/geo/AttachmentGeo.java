package com.aetherianartificer.townstead.client.attachment.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Townstead's own baked attachment model, replacing the vanilla {@link
 * net.minecraft.client.model.geom.ModelPart} bake for attachments. Owning the
 * bake buys what vanilla can't do: per-face UVs, the Bedrock {@code mirror}
 * flag, per-cube rotations (baked into the vertices), name-indexed bones at any
 * depth for morphs, and per-bone mutable pose state for the later physics and
 * state-pose phases. Box-UV quad construction replicates vanilla's {@code
 * ModelPart.Cube} math exactly, so box-only models render identically to the
 * old path.
 *
 * <p>Bones are shared across every entity wearing the attachment: pose fields
 * (scale, rotation offsets, visibility) mutated for one draw must be restored
 * afterwards, same contract as the proportions mixin.</p>
 */
public final class AttachmentGeo {

    private final List<Bone> roots;
    private final Map<String, Bone> byName;

    AttachmentGeo(List<Bone> roots, Map<String, Bone> byName) {
        this.roots = roots;
        this.byName = byName;
        for (Bone root : roots) linkParents(root);
    }

    private static void linkParents(Bone bone) {
        for (Bone child : bone.children) {
            child.parent = bone;
            linkParents(child);
        }
    }

    /** The bone with this authored name at any depth, or null. */
    public Bone bone(String name) {
        return byName.get(name);
    }

    public Set<String> boneNames() {
        return byName.keySet();
    }

    /**
     * Bends what's drawn: given a point in the space {@link #render} starts in (blocks), moves it where a soft
     * part has squashed or swung it. {@link #at} picks the bender that takes over at a bone, with everything under
     * it, so one part can bend in pieces (a sac, then its tip on a joint of its own).
     */
    public interface Deformer {
        /** The bender that takes over from this bone down, or null to keep the one above it (none at the top). */
        @org.jetbrains.annotations.Nullable
        Deformer at(String bone);

        void deform(Vector3f point);

        /** Which rigid plate a texture point is on, from 1, or 0 for none. */
        default int plate(float u, float v) {
            return 0;
        }

        /** Moves a point of a rigid plate with that plate as one piece, about {@code centre} at rest. */
        default void deformPlate(Vector3f point, Vector3f centre) {
            deform(point);
        }

        /** Strips a side each bent face is cut into: more where plates need room to stay whole. */
        default int detail() {
            return 3;
        }
    }

    // The most strips a side a bent face is cut into, so the bend can curve it.
    private static final int SUBDIVIDE = 8;
    // Render thread only: set around one attachment's draws.
    private static Deformer deformer;
    private static PoseStack.Pose deformRoot;
    private static final Matrix4f DEFORM_INVERSE = new Matrix4f();
    private static final Matrix3f DEFORM_INVERSE_NORMAL = new Matrix3f();

    /** Bends the draws that follow, until set back to null. Render thread only. */
    public static void deformer(@org.jetbrains.annotations.Nullable Deformer bend) {
        deformer = bend;
    }

    public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, int argb) {
        Deformer bend = deformer;
        if (bend != null) {
            deformRoot = pose.last();
            DEFORM_INVERSE.set(deformRoot.pose()).invert();
            DEFORM_INVERSE_NORMAL.set(DEFORM_INVERSE);
        }
        for (Bone root : roots) root.render(pose, buffer, light, overlay, argb, bend == null ? null : bend.at(root.name));
    }

    /** Receives one visible bone's box, in whatever space the frame given to {@link #measure} leads to. */
    @FunctionalInterface
    public interface BoneBox {
        void accept(String bone, float minX, float minY, float minZ, float maxX, float maxY, float maxZ);
    }

    /** Each visible bone's box as it would draw now, physics included, in the same order as a draw. */
    public void measure(org.joml.Matrix4f frame, BoneBox out) {
        for (Bone root : roots) root.measure(frame, out);
    }

    /**
     * A deep copy re-baked mirrored across X (for a {@code mirror} attachment point):
     * pivots and vertices flip, Y/Z rotations negate, polygon winding reverses and
     * normal X flips — proper lighting, unlike a {@code scale(-1,1,1)} hack. Bone
     * names are kept so morphs address both bakes identically.
     */
    public AttachmentGeo mirrored() {
        java.util.List<Bone> mirroredRoots = new java.util.ArrayList<>(roots.size());
        Map<String, Bone> mirroredByName = new java.util.LinkedHashMap<>();
        for (Bone root : roots) mirroredRoots.add(mirrorBone(root, mirroredByName));
        return new AttachmentGeo(mirroredRoots, mirroredByName);
    }

    private static Bone mirrorBone(Bone bone, Map<String, Bone> byName) {
        java.util.List<Cube> cubes = new java.util.ArrayList<>(bone.cubes.size());
        for (Cube cube : bone.cubes) {
            Polygon[] polygons = new Polygon[cube.polygons.length];
            for (int i = 0; i < polygons.length; i++) {
                Polygon p = cube.polygons[i];
                Vertex[] vertices = new Vertex[p.vertices.length];
                for (int j = 0; j < vertices.length; j++) {
                    // Reverse order restores front-face winding after the X flip.
                    Vertex v = p.vertices[p.vertices.length - 1 - j];
                    vertices[j] = new Vertex(-v.x, v.y, v.z, v.u, v.v);
                }
                polygons[i] = new Polygon(vertices, new Vector3f(-p.normal.x(), p.normal.y(), p.normal.z()));
            }
            cubes.add(new Cube(polygons));
        }
        java.util.List<Bone> children = new java.util.ArrayList<>(bone.children.size());
        Bone mirrored = new Bone(bone.name, -bone.x, bone.y, bone.z,
                bone.xRot, -bone.yRot, -bone.zRot, cubes, children);
        byName.put(mirrored.name, mirrored);
        for (Bone child : bone.children) children.add(mirrorBone(child, byName));
        return mirrored;
    }

    public static final class Bone {
        public final String name;
        final float x, y, z;                 // pivot offset from parent (model px, Java space)

        /** Pivot offset from the parent bone (model px, Java space) — the chain direction between joints. */
        public float[] pivotOffset() {
            return new float[]{x, y, z};
        }
        public float xRot, yRot, zRot;        // pose rotation (radians); initialized to the authored pose
        public float xOff, yOff, zOff;        // pose translation (model px, Java space); animation position channel
        public float xScale = 1f, yScale = 1f, zScale = 1f;
        public boolean visible = true;
        /** Physics result: replaces the Euler pose rotation for this draw when set. */
        public Quaternionf physicsRotation;
        final List<Cube> cubes;
        final List<Bone> children;
        Bone parent;
        private float[] tip;
        private float[] bounds;

        Bone(String name, float x, float y, float z, float xRot, float yRot, float zRot,
             List<Cube> cubes, List<Bone> children) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xRot = xRot;
            this.yRot = yRot;
            this.zRot = zRot;
            this.cubes = cubes;
            this.children = children;
        }

        public Bone parent() {
            return parent;
        }

        public List<Bone> children() {
            return children;
        }

        /** The pose rotation as a quaternion (Euler fields, ZYX). */
        public Quaternionf poseRotation(Quaternionf dest) {
            return dest.rotationZYX(zRot, yRot, xRot);
        }

        /** Multiplies this bone's local transform (pivot, rotation, scale) onto {@code m}, as render does. */
        public Matrix4f applyLocal(Matrix4f m, Quaternionf rotation) {
            return m.translate((x + xOff) / 16f, (y + yOff) / 16f, (z + zOff) / 16f)
                    .rotate(rotation).scale(xScale, yScale, zScale);
        }

        /** Pivot of this bone in its parent's space, in blocks. */
        public Vector3f pivot(Vector3f dest) {
            return dest.set((x + xOff) / 16f, (y + yOff) / 16f, (z + zOff) / 16f);
        }

        /**
         * Where this bone's own geometry ends, in its local space (blocks): along the
         * direction to its vertex centroid, as far as the farthest vertex reaches. Null
         * when the bone has no geometry to measure.
         */
        public float[] tip() {
            if (tip != null) return tip.length == 0 ? null : tip;
            Vector3f dir = new Vector3f();
            int count = 0;
            for (Cube cube : cubes) {
                for (Polygon polygon : cube.polygons) {
                    for (Vertex v : polygon.vertices) {
                        dir.add(v.x, v.y, v.z);
                        count++;
                    }
                }
            }
            if (count > 0) dir.div(count);
            if (dir.lengthSquared() < 0.01f) {
                // Geometry centred on the pivot: aim at the farthest vertex instead.
                float best = -1f;
                for (Cube cube : cubes) {
                    for (Polygon polygon : cube.polygons) {
                        for (Vertex v : polygon.vertices) {
                            float d = v.x * v.x + v.y * v.y + v.z * v.z;
                            if (d > best) {
                                best = d;
                                dir.set(v.x, v.y, v.z);
                            }
                        }
                    }
                }
            }
            if (dir.lengthSquared() < 1e-6f) {
                tip = new float[0];
                return null;
            }
            dir.normalize();
            float reach = 0f;
            for (Cube cube : cubes) {
                for (Polygon polygon : cube.polygons) {
                    for (Vertex v : polygon.vertices) {
                        reach = Math.max(reach, v.x * dir.x + v.y * dir.y + v.z * dir.z);
                    }
                }
            }
            tip = new float[]{dir.x * reach / 16f, dir.y * reach / 16f, dir.z * reach / 16f};
            return tip;
        }

        /** Half this bone's narrowest measure, in model pixels; 0 when it has no geometry to measure. */
        public float halfThickness() {
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            boolean any = false;
            for (Cube cube : cubes) {
                for (Polygon polygon : cube.polygons) {
                    for (Vertex v : polygon.vertices) {
                        any = true;
                        min[0] = Math.min(min[0], v.x);
                        min[1] = Math.min(min[1], v.y);
                        min[2] = Math.min(min[2], v.z);
                        max[0] = Math.max(max[0], v.x);
                        max[1] = Math.max(max[1], v.y);
                        max[2] = Math.max(max[2], v.z);
                    }
                }
            }
            if (!any) return 0f;
            return Math.min(max[0] - min[0], Math.min(max[1] - min[1], max[2] - min[2])) / 2f;
        }

        private void measure(Matrix4f parent, BoneBox out) {
            if (!visible) return;
            Matrix4f world = applyLocal(new Matrix4f(parent),
                    physicsRotation != null ? physicsRotation : poseRotation(new Quaternionf()));
            float[] local = localBounds();
            if (local != null) {
                float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
                Vector3f corner = new Vector3f();
                for (int c = 0; c < 8; c++) {
                    corner.set((c & 1) == 0 ? local[0] : local[3], (c & 2) == 0 ? local[1] : local[4],
                            (c & 4) == 0 ? local[2] : local[5]);
                    world.transformPosition(corner);
                    box[0] = Math.min(box[0], corner.x);
                    box[1] = Math.min(box[1], corner.y);
                    box[2] = Math.min(box[2], corner.z);
                    box[3] = Math.max(box[3], corner.x);
                    box[4] = Math.max(box[4], corner.y);
                    box[5] = Math.max(box[5], corner.z);
                }
                out.accept(name, box[0], box[1], box[2], box[3], box[4], box[5]);
            }
            for (Bone child : children) child.measure(world, out);
        }

        /** The box around this bone's own cubes, in its local space (blocks); null when it has none. */
        private float[] localBounds() {
            if (bounds != null) return bounds.length == 0 ? null : bounds;
            float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            boolean any = false;
            for (Cube cube : cubes) {
                for (Polygon polygon : cube.polygons) {
                    for (Vertex v : polygon.vertices) {
                        any = true;
                        box[0] = Math.min(box[0], v.x / 16f);
                        box[1] = Math.min(box[1], v.y / 16f);
                        box[2] = Math.min(box[2], v.z / 16f);
                        box[3] = Math.max(box[3], v.x / 16f);
                        box[4] = Math.max(box[4], v.y / 16f);
                        box[5] = Math.max(box[5], v.z / 16f);
                    }
                }
            }
            bounds = any ? box : new float[0];
            return any ? box : null;
        }

        void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, int argb,
                    @org.jetbrains.annotations.Nullable Deformer bent) {
            if (!visible) return;
            pose.pushPose();
            pose.translate((x + xOff) / 16f, (y + yOff) / 16f, (z + zOff) / 16f);
            if (physicsRotation != null) {
                pose.mulPose(physicsRotation);
            } else if (xRot != 0f || yRot != 0f || zRot != 0f) {
                pose.mulPose(new Quaternionf().rotationZYX(zRot, yRot, xRot));
            }
            if (xScale != 1f || yScale != 1f || zScale != 1f) {
                pose.scale(xScale, yScale, zScale);
            }
            Deformer top = deformer;
            boolean bending = bent != null && top != null && deformRoot != null;
            for (Cube cube : cubes) {
                if (bending) cube.compileBent(pose.last(), deformRoot, bent, buffer, light, overlay, argb);
                else cube.compile(pose.last(), buffer, light, overlay, argb);
            }
            for (Bone child : children) {
                Deformer own = top == null ? null : top.at(child.name);
                child.render(pose, buffer, light, overlay, argb, own != null ? own : bent);
            }
            pose.popPose();
        }
    }

    static final class Cube {
        final Polygon[] polygons;

        Cube(Polygon[] polygons) {
            this.polygons = polygons;
        }

        void compile(PoseStack.Pose ms, VertexConsumer vc, int light, int overlay, int argb) {
            int a = argb >>> 24 & 0xFF;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            for (Polygon polygon : polygons) {
                float nx = polygon.normal.x();
                float ny = polygon.normal.y();
                float nz = polygon.normal.z();
                for (Vertex v : polygon.vertices) {
                    float px = v.x / 16f;
                    float py = v.y / 16f;
                    float pz = v.z / 16f;
                    //? if >=1.21 {
                    vc.addVertex(ms, px, py, pz).setColor(r, g, b, a).setUv(v.u, v.v)
                            .setOverlay(overlay).setLight(light).setNormal(ms, nx, ny, nz);
                    //?} else {
                    /*vc.vertex(ms.pose(), px, py, pz).color(r, g, b, a).uv(v.u, v.v)
                            .overlayCoords(overlay).uv2(light).normal(ms.normal(), nx, ny, nz).endVertex();
                    *///?}
                }
            }
        }

        void compileBent(PoseStack.Pose bone, PoseStack.Pose root, Deformer bend, VertexConsumer vc, int light,
                         int overlay, int argb) {
            int a = argb >>> 24 & 0xFF;
            int r = argb >> 16 & 0xFF;
            int g = argb >> 8 & 0xFF;
            int b = argb & 0xFF;
            for (Polygon polygon : polygons) bentFace(polygon, bone, root, bend, vc, light, overlay, r, g, b, a);
        }
    }

    // Render thread scratch for bent faces.
    private static final float[][] GRID_POS = new float[(SUBDIVIDE + 1) * (SUBDIVIDE + 1)][3];
    private static final float[][] GRID_UV = new float[(SUBDIVIDE + 1) * (SUBDIVIDE + 1)][2];
    private static final Vector3f P = new Vector3f(), A = new Vector3f(), B = new Vector3f(), N = new Vector3f();
    private static final Vector3f FACING = new Vector3f();
    private static final int[] GRID_PLATE = new int[(SUBDIVIDE + 1) * (SUBDIVIDE + 1)];
    private static final Vector3f[] PLATE_CENTRES = new Vector3f[16];
    private static final int[] PLATE_COUNTS = new int[16];

    static {
        for (int i = 0; i < PLATE_CENTRES.length; i++) PLATE_CENTRES[i] = new Vector3f();
    }

    /** One face of a bent bone: cut into a grid, each point bent, each small face lit by its own normal. */
    static void bentFace(Polygon polygon, PoseStack.Pose bone, PoseStack.Pose root, Deformer bend, VertexConsumer vc,
                         int light, int overlay, int r, int g, int b, int a) {
        Vertex[] vs = polygon.vertices;
        // Where the face would point, carried into the root's space, so bending never turns it inside out.
        bone.normal().transform(FACING.set(polygon.normal));
        DEFORM_INVERSE_NORMAL.transform(FACING);
        if (vs.length != 4) {
            for (Vertex v : vs) {
                bend(bone, v.x, v.y, v.z, bend);
                emit(vc, root, P.x, P.y, P.z, v.u, v.v, r, g, b, a, overlay, light, FACING);
            }
            return;
        }
        int side = Math.max(1, Math.min(SUBDIVIDE, bend.detail())), row = side + 1;
        boolean plates = false;
        for (int i = 0; i < PLATE_COUNTS.length; i++) {
            PLATE_COUNTS[i] = 0;
            PLATE_CENTRES[i].zero();
        }
        // First where each point sits at rest, and which plate it's on.
        for (int t = 0; t <= side; t++) {
            for (int s = 0; s <= side; s++) {
                float fs = s / (float) side, ft = t / (float) side;
                // Bilinear across the face: corners 0 to 1 along one edge, 3 to 2 along the other.
                float x = lerp(lerp(vs[0].x, vs[1].x, fs), lerp(vs[3].x, vs[2].x, fs), ft);
                float y = lerp(lerp(vs[0].y, vs[1].y, fs), lerp(vs[3].y, vs[2].y, fs), ft);
                float z = lerp(lerp(vs[0].z, vs[1].z, fs), lerp(vs[3].z, vs[2].z, fs), ft);
                bone.pose().transformPosition(P.set(x / 16f, y / 16f, z / 16f));
                DEFORM_INVERSE.transformPosition(P);
                int n = t * row + s;
                float[] at = GRID_POS[n];
                at[0] = P.x;
                at[1] = P.y;
                at[2] = P.z;
                float[] uv = GRID_UV[n];
                uv[0] = lerp(lerp(vs[0].u, vs[1].u, fs), lerp(vs[3].u, vs[2].u, fs), ft);
                uv[1] = lerp(lerp(vs[0].v, vs[1].v, fs), lerp(vs[3].v, vs[2].v, fs), ft);
                int plate = Math.min(bend.plate(uv[0], uv[1]), PLATE_CENTRES.length - 1);
                GRID_PLATE[n] = plate;
                if (plate > 0) {
                    plates = true;
                    PLATE_CENTRES[plate].add(P);
                    PLATE_COUNTS[plate]++;
                }
            }
        }
        if (plates) {
            for (int i = 1; i < PLATE_CENTRES.length; i++) {
                if (PLATE_COUNTS[i] > 0) PLATE_CENTRES[i].div(PLATE_COUNTS[i]);
            }
        }
        // Then bent: the membrane follows the grid, a plate moves as one piece about its centre.
        for (int n = 0; n < row * row; n++) {
            float[] at = GRID_POS[n];
            P.set(at[0], at[1], at[2]);
            int plate = GRID_PLATE[n];
            if (plate > 0) bend.deformPlate(P, PLATE_CENTRES[plate]);
            else bend.deform(P);
            at[0] = P.x;
            at[1] = P.y;
            at[2] = P.z;
        }
        for (int t = 0; t < side; t++) {
            for (int s = 0; s < side; s++) {
                int i0 = t * row + s, i1 = i0 + 1, i2 = i0 + row + 1, i3 = i0 + row;
                float[] c0 = GRID_POS[i0], c1 = GRID_POS[i1], c2 = GRID_POS[i2], c3 = GRID_POS[i3];
                A.set(c2[0] - c0[0], c2[1] - c0[1], c2[2] - c0[2]);
                B.set(c3[0] - c1[0], c3[1] - c1[1], c3[2] - c1[2]);
                A.cross(B, N);
                if (N.lengthSquared() < 1e-12f) N.set(FACING);
                if (N.dot(FACING) < 0f) N.negate();
                if (N.lengthSquared() > 1e-12f) N.normalize();
                emitAt(vc, root, i0, r, g, b, a, overlay, light);
                emitAt(vc, root, i1, r, g, b, a, overlay, light);
                emitAt(vc, root, i2, r, g, b, a, overlay, light);
                emitAt(vc, root, i3, r, g, b, a, overlay, light);
            }
        }
    }

    /** A point of a bone, in model pixels, into the root's space and bent; left in {@link #P}. */
    private static void bend(PoseStack.Pose bone, float x, float y, float z, Deformer bend) {
        bone.pose().transformPosition(P.set(x / 16f, y / 16f, z / 16f));
        DEFORM_INVERSE.transformPosition(P);
        bend.deform(P);
    }

    private static void emitAt(VertexConsumer vc, PoseStack.Pose root, int i, int r, int g, int b, int a,
                               int overlay, int light) {
        float[] at = GRID_POS[i];
        float[] uv = GRID_UV[i];
        emit(vc, root, at[0], at[1], at[2], uv[0], uv[1], r, g, b, a, overlay, light, N);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose root, float x, float y, float z, float u, float v,
                             int r, int g, int b, int a, int overlay, int light, Vector3f n) {
        //? if >=1.21 {
        vc.addVertex(root, x, y, z).setColor(r, g, b, a).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(root, n.x, n.y, n.z);
        //?} else {
        /*vc.vertex(root.pose(), x, y, z).color(r, g, b, a).uv(u, v)
                .overlayCoords(overlay).uv2(light).normal(root.normal(), n.x, n.y, n.z).endVertex();
        *///?}
    }

    static final class Polygon {
        final Vertex[] vertices;
        final Vector3f normal;

        Polygon(Vertex[] vertices, Vector3f normal) {
            this.vertices = vertices;
            this.normal = normal;
        }
    }

    static final class Vertex {
        final float x, y, z;
        final float u, v;

        Vertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }
}
