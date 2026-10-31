package com.aetherianartificer.townstead.client.attachment.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

    public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, int argb) {
        for (Bone root : roots) root.render(pose, buffer, light, overlay, argb);
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

        void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, int argb) {
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
            for (Cube cube : cubes) cube.compile(pose.last(), buffer, light, overlay, argb);
            for (Bone child : children) child.render(pose, buffer, light, overlay, argb);
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
