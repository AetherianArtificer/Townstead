package com.aetherianartificer.townstead.client.attachment.geo;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Bedrock {@code .geo.json} into an {@link AttachmentGeo}, supporting the
 * full cube spec Blockbench exports: box UV (exact vanilla layout and winding, so
 * box-only models match the old {@code ModelPart} bake pixel-for-pixel), per-face
 * UV objects, the {@code mirror} flag (bone-level default, per-cube override),
 * {@code inflate}, and per-cube {@code rotation}/{@code pivot} (baked into vertex
 * positions and normals — static rotation costs nothing at render time).
 *
 * <p>Coordinate transform is the standard Bedrock→Java change: Y negated (with
 * cube corners offset by their height); rotations pass through as {@code (x,y,z)},
 * as GeckoLib and Blockbench read a Bedrock file. A per-face UV entry that omits a face skips that face's quad, matching
 * Blockbench's transparent-face export. Returns {@code null} on a malformed file.</p>
 */
public final class AttachmentGeoLoader {

    private AttachmentGeoLoader() {}

    private record RawBone(String name, String parent, float[] pivot, float[] rotation,
                           boolean mirror, List<JsonObject> cubes) {}

    public static AttachmentGeo parse(JsonObject root) {
        try {
            JsonArray geometries = GsonHelper.getAsJsonArray(root, "minecraft:geometry");
            if (geometries.isEmpty()) return null;
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonObject description = GsonHelper.getAsJsonObject(geometry, "description", new JsonObject());
            float texWidth = GsonHelper.getAsFloat(description, "texture_width", 16);
            float texHeight = GsonHelper.getAsFloat(description, "texture_height", 16);

            Map<String, RawBone> raw = new LinkedHashMap<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(geometry, "bones", new JsonArray())) {
                JsonObject boneJson = element.getAsJsonObject();
                String name = GsonHelper.getAsString(boneJson, "name");
                List<JsonObject> cubes = new ArrayList<>();
                for (JsonElement cube : GsonHelper.getAsJsonArray(boneJson, "cubes", new JsonArray())) {
                    cubes.add(cube.getAsJsonObject());
                }
                raw.put(name, new RawBone(name,
                        GsonHelper.getAsString(boneJson, "parent", ""),
                        readVec(boneJson, "pivot"),
                        boneJson.has("rotation") ? readVec(boneJson, "rotation") : new float[3],
                        GsonHelper.getAsBoolean(boneJson, "mirror", false),
                        cubes));
            }

            Map<String, AttachmentGeo.Bone> built = new LinkedHashMap<>();
            List<AttachmentGeo.Bone> roots = new ArrayList<>();
            // Multi-pass so a child whose parent appears later still attaches correctly.
            List<RawBone> pending = new ArrayList<>(raw.values());
            boolean progress = true;
            while (!pending.isEmpty() && progress) {
                progress = false;
                for (RawBone bone : new ArrayList<>(pending)) {
                    boolean hasParent = bone.parent() != null && !bone.parent().isEmpty();
                    AttachmentGeo.Bone parent = hasParent ? built.get(bone.parent()) : null;
                    if (hasParent && parent == null) continue;
                    float[] parentPivot = hasParent ? raw.get(bone.parent()).pivot() : new float[3];
                    AttachmentGeo.Bone builtBone = buildBone(bone, parentPivot, texWidth, texHeight);
                    built.put(bone.name(), builtBone);
                    if (parent != null) parent.children.add(builtBone);
                    else roots.add(builtBone);
                    pending.remove(bone);
                    progress = true;
                }
            }
            if (!pending.isEmpty()) {
                List<String> unresolved = new ArrayList<>();
                for (RawBone bone : pending) unresolved.add(bone.name());
                Townstead.LOGGER.warn("Attachment geometry has unresolved bone parents: {}", unresolved);
            }
            return new AttachmentGeo(roots, built);
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to parse attachment geometry", e);
            return null;
        }
    }

    private static AttachmentGeo.Bone buildBone(RawBone bone, float[] parentPivot,
                                                float texWidth, float texHeight) {
        List<AttachmentGeo.Cube> cubes = new ArrayList<>();
        for (JsonObject cube : bone.cubes()) {
            cubes.add(buildCube(cube, bone, texWidth, texHeight));
        }
        return new AttachmentGeo.Bone(bone.name(),
                bone.pivot()[0] - parentPivot[0],
                -(bone.pivot()[1] - parentPivot[1]),
                bone.pivot()[2] - parentPivot[2],
                (float) Math.toRadians(bone.rotation()[0]),
                (float) Math.toRadians(bone.rotation()[1]),
                (float) Math.toRadians(bone.rotation()[2]),
                cubes, new ArrayList<>());
    }

    // Geometric quad slots, named after the vanilla polygon they replicate. Vanilla model space is
    // y-down, so the vanilla "DOWN" polygon (min-Y vertices) is the visual TOP face and vice versa;
    // Bedrock's per-face "up" therefore lands in the DOWN slot.
    private static final int F_DOWN = 0, F_UP = 1, F_WEST = 2, F_NORTH = 3, F_EAST = 4, F_SOUTH = 5;
    // Thinner than this along an axis (in pixels, after inflate) is a plane, not a box.
    private static final float FLAT = 1.0e-3f;
    private static final Vector3f[] NORMALS = {
            new Vector3f(0, -1, 0), new Vector3f(0, 1, 0), new Vector3f(-1, 0, 0),
            new Vector3f(0, 0, -1), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1),
    };

    private static AttachmentGeo.Cube buildCube(JsonObject cube, RawBone bone,
                                                float texWidth, float texHeight) {
        float[] origin = readVec(cube, "origin");
        float[] size = readVec(cube, "size");
        float inflate = GsonHelper.getAsFloat(cube, "inflate", 0f);
        boolean mirror = GsonHelper.getAsBoolean(cube, "mirror", bone.mirror());
        float sx = size[0], sy = size[1], sz = size[2];

        // Bedrock -> Java corner: negate Y and drop by the cube height (same as the ModelPart bake).
        float bx = origin[0] - bone.pivot()[0];
        float by = -(origin[1] - bone.pivot()[1]) - sy;
        float bz = origin[2] - bone.pivot()[2];
        float x1 = bx - inflate, y1 = by - inflate, z1 = bz - inflate;
        float x2 = bx + sx + inflate, y2 = by + sy + inflate, z2 = bz + sz + inflate;
        if (mirror) {
            float swap = x2;
            x2 = x1;
            x1 = swap;
        }

        // The eight corners, in vanilla Cube's vertex naming/order.
        float[][] v7 = {{x1, y1, z1}}, v0 = {{x2, y1, z1}}, v1 = {{x2, y2, z1}}, v2 = {{x1, y2, z1}};
        float[][] v3 = {{x1, y1, z2}}, v4 = {{x2, y1, z2}}, v5 = {{x2, y2, z2}}, v6 = {{x1, y2, z2}};
        float[][][] corners = {
                {v4[0], v3[0], v7[0], v0[0]},   // DOWN  (visual top)
                {v1[0], v2[0], v6[0], v5[0]},   // UP    (visual bottom)
                {v7[0], v3[0], v6[0], v2[0]},   // WEST
                {v0[0], v7[0], v2[0], v1[0]},   // NORTH
                {v4[0], v0[0], v1[0], v5[0]},   // EAST
                {v3[0], v4[0], v5[0], v6[0]},   // SOUTH
        };

        // Per-face UV rects (u1, v1, u2, v2) in texels; null skips the face.
        float[][] uvRects = new float[6][];
        JsonElement uv = cube.get("uv");
        if (uv != null && uv.isJsonObject()) {
            JsonObject faces = uv.getAsJsonObject();
            uvRects[F_DOWN] = faceRect(faces, "up", sx, sz);
            uvRects[F_UP] = faceRect(faces, "down", sx, sz);
            uvRects[F_WEST] = faceRect(faces, "west", sz, sy);
            uvRects[F_NORTH] = faceRect(faces, "north", sx, sy);
            uvRects[F_EAST] = faceRect(faces, "east", sz, sy);
            uvRects[F_SOUTH] = faceRect(faces, "south", sx, sy);
        } else {
            float u = 0, v = 0;
            if (uv != null && uv.isJsonArray()) {
                JsonArray arr = uv.getAsJsonArray();
                if (arr.size() > 0) u = arr.get(0).getAsFloat();
                if (arr.size() > 1) v = arr.get(1).getAsFloat();
            }
            // Vanilla box layout, verbatim.
            uvRects[F_DOWN] = new float[]{u + sz, v, u + sz + sx, v + sz};
            uvRects[F_UP] = new float[]{u + sz + sx, v + sz, u + sz + sx + sx, v};
            uvRects[F_WEST] = new float[]{u, v + sz, u + sz, v + sz + sy};
            uvRects[F_NORTH] = new float[]{u + sz, v + sz, u + sz + sx, v + sz + sy};
            uvRects[F_EAST] = new float[]{u + sz + sx, v + sz, u + sz + sx + sz, v + sz + sy};
            uvRects[F_SOUTH] = new float[]{u + sz + sx + sz, v + sz, u + sz + sx + sz + sx, v + sz + sy};
        }

        // A flat cube (a wing membrane, a fin) is one plane: its two faces lie on each other and the
        // attachment layers draw without culling, so both would show and z-fight. Keep the front
        // face of the box layout, which shows from behind too, and drop the zero-area edges.
        boolean flatX = Math.abs(x2 - x1) < FLAT, flatY = Math.abs(y2 - y1) < FLAT, flatZ = Math.abs(z2 - z1) < FLAT;
        if (flatX) uvRects[F_EAST] = null;
        if (flatY) uvRects[F_UP] = null;
        if (flatZ) uvRects[F_SOUTH] = null;
        if (flatX || flatZ) uvRects[F_DOWN] = uvRects[F_UP] = null;
        if (flatX || flatY) uvRects[F_NORTH] = uvRects[F_SOUTH] = null;
        if (flatY || flatZ) uvRects[F_WEST] = uvRects[F_EAST] = null;

        List<AttachmentGeo.Polygon> polygons = new ArrayList<>(6);
        for (int face = 0; face < 6; face++) {
            float[] rect = uvRects[face];
            if (rect == null) continue;
            float u1 = rect[0] / texWidth, vv1 = rect[1] / texHeight;
            float u2 = rect[2] / texWidth, vv2 = rect[3] / texHeight;
            float[][] c = corners[face];
            // Vanilla's remap corner assignment: [0]=(u2,v1) [1]=(u1,v1) [2]=(u1,v2) [3]=(u2,v2).
            AttachmentGeo.Vertex[] vertices = {
                    new AttachmentGeo.Vertex(c[0][0], c[0][1], c[0][2], u2, vv1),
                    new AttachmentGeo.Vertex(c[1][0], c[1][1], c[1][2], u1, vv1),
                    new AttachmentGeo.Vertex(c[2][0], c[2][1], c[2][2], u1, vv2),
                    new AttachmentGeo.Vertex(c[3][0], c[3][1], c[3][2], u2, vv2),
            };
            if (mirror) {
                for (int i = 0; i < vertices.length / 2; i++) {
                    AttachmentGeo.Vertex swap = vertices[i];
                    vertices[i] = vertices[vertices.length - 1 - i];
                    vertices[vertices.length - 1 - i] = swap;
                }
            }
            Vector3f normal = new Vector3f(NORMALS[face]);
            if (mirror) normal.mul(-1f, 1f, 1f);
            polygons.add(new AttachmentGeo.Polygon(vertices, normal));
        }

        // Per-cube rotation about its own pivot: static, so bake it straight into the vertices.
        if (cube.has("rotation")) {
            float[] rot = readVec(cube, "rotation");
            float[] pivot = cube.has("pivot") ? readVec(cube, "pivot") : origin;
            float px = pivot[0] - bone.pivot()[0];
            float py = -(pivot[1] - bone.pivot()[1]);
            float pz = pivot[2] - bone.pivot()[2];
            Quaternionf q = new Quaternionf().rotationZYX(
                    (float) Math.toRadians(rot[2]), (float) Math.toRadians(rot[1]),
                    (float) Math.toRadians(rot[0]));
            List<AttachmentGeo.Polygon> rotated = new ArrayList<>(polygons.size());
            Vector3f work = new Vector3f();
            for (AttachmentGeo.Polygon polygon : polygons) {
                AttachmentGeo.Vertex[] vertices = new AttachmentGeo.Vertex[polygon.vertices.length];
                for (int i = 0; i < vertices.length; i++) {
                    AttachmentGeo.Vertex v = polygon.vertices[i];
                    work.set(v.x - px, v.y - py, v.z - pz);
                    q.transform(work);
                    vertices[i] = new AttachmentGeo.Vertex(work.x + px, work.y + py, work.z + pz, v.u, v.v);
                }
                Vector3f normal = q.transform(new Vector3f(polygon.normal));
                rotated.add(new AttachmentGeo.Polygon(vertices, normal));
            }
            polygons = rotated;
        }

        return new AttachmentGeo.Cube(polygons.toArray(new AttachmentGeo.Polygon[0]));
    }

    /** A per-face UV rect from a Bedrock uv object, or null when the face is absent (skipped). */
    private static float[] faceRect(JsonObject faces, String face, float defaultW, float defaultH) {
        if (!faces.has(face) || !faces.get(face).isJsonObject()) return null;
        JsonObject entry = faces.getAsJsonObject(face);
        float[] uv = readVec2(entry, "uv");
        float w = defaultW, h = defaultH;
        if (entry.has("uv_size")) {
            float[] uvSize = readVec2(entry, "uv_size");
            w = uvSize[0];
            h = uvSize[1];
        }
        return new float[]{uv[0], uv[1], uv[0] + w, uv[1] + h};
    }

    private static float[] readVec(JsonObject json, String key) {
        float[] out = new float[3];
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static float[] readVec2(JsonObject json, String key) {
        float[] out = new float[2];
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < 2 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
