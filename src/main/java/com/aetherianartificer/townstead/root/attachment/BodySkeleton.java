package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An attachment model's bones as plain numbers, read from its geometry the way the render bake reads it, so
 * either side can work out where they are. Everything is in Java model space: y down, pixels, each bone's
 * pivot relative to its parent's.
 */
final class BodySkeleton {

    static final class Node {
        final String name;
        final float x, y, z;
        final float xRot, yRot, zRot;
        final int group;
        final List<Node> children = new ArrayList<>();
        // The bone's own corners in its local space, in pixels; empty when it has no cubes.
        final List<float[]> corners = new ArrayList<>();
        Node parent;

        Node(String name, float x, float y, float z, float xRot, float yRot, float zRot, int group) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xRot = xRot;
            this.yRot = yRot;
            this.zRot = zRot;
            this.group = group;
        }

        /** The box around this bone's corners, min x, y, z then max, in blocks; null when it has none. */
        float[] bounds(boolean mirror) {
            if (corners.isEmpty()) return null;
            float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (float[] corner : corners) {
                float cx = (mirror ? -corner[0] : corner[0]) / 16f, cy = corner[1] / 16f, cz = corner[2] / 16f;
                box[0] = Math.min(box[0], cx);
                box[1] = Math.min(box[1], cy);
                box[2] = Math.min(box[2], cz);
                box[3] = Math.max(box[3], cx);
                box[4] = Math.max(box[4], cy);
                box[5] = Math.max(box[5], cz);
            }
            return box;
        }

        /** Half this bone's narrowest measure, in model pixels; 0 when it has no geometry to measure. */
        float halfThickness() {
            if (corners.isEmpty()) return 0f;
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (float[] corner : corners) {
                for (int i = 0; i < 3; i++) {
                    min[i] = Math.min(min[i], corner[i]);
                    max[i] = Math.max(max[i], corner[i]);
                }
            }
            return Math.min(max[0] - min[0], Math.min(max[1] - min[1], max[2] - min[2])) / 2f;
        }

        /** Where this bone's geometry ends, in blocks: along the way to its centre, as far as it reaches. */
        float[] tip(boolean mirror) {
            if (corners.isEmpty()) return null;
            Vector3f dir = new Vector3f();
            for (float[] corner : corners) dir.add(corner[0], corner[1], corner[2]);
            dir.div(corners.size());
            if (dir.lengthSquared() < 0.01f) {
                float best = -1f;
                for (float[] corner : corners) {
                    float distance = corner[0] * corner[0] + corner[1] * corner[1] + corner[2] * corner[2];
                    if (distance > best) {
                        best = distance;
                        dir.set(corner[0], corner[1], corner[2]);
                    }
                }
            }
            if (dir.lengthSquared() < 1e-6f) return null;
            dir.normalize();
            float reach = 0f;
            for (float[] corner : corners) reach = Math.max(reach, corner[0] * dir.x + corner[1] * dir.y + corner[2] * dir.z);
            return new float[]{(mirror ? -dir.x : dir.x) * reach / 16f, dir.y * reach / 16f, dir.z * reach / 16f};
        }
    }

    final List<Node> roots = new ArrayList<>();
    final Map<String, Node> byName = new LinkedHashMap<>();
    final int groups;
    // Set from the file before any slicing, as groups are: the bones starting each piece, and each box's piece.
    List<String> pieceRoots = List.of();
    int[] groupPiece = new int[0];

    private BodySkeleton(int groups) {
        this.groups = groups;
    }

    /** Null when the file has no usable bones. {@code groups} is from the file before any slicing. */
    static BodySkeleton parse(JsonObject geometry, Map<String, Integer> groups) {
        JsonArray bones = HitShapes.bonesOf(geometry);
        if (bones == null) return null;
        int count = 0;
        for (int group : groups.values()) count = Math.max(count, group + 1);
        BodySkeleton out = new BodySkeleton(count);
        Map<String, JsonObject> raw = new LinkedHashMap<>();
        for (JsonElement bone : bones) {
            JsonObject json = bone.getAsJsonObject();
            raw.put(json.get("name").getAsString(), json);
        }
        for (Map.Entry<String, JsonObject> entry : raw.entrySet()) {
            JsonObject json = entry.getValue();
            float[] pivot = vec(json, "pivot");
            String parentName = json.has("parent") ? json.get("parent").getAsString() : "";
            JsonObject parent = raw.get(parentName);
            float[] parentPivot = parent == null ? new float[3] : vec(parent, "pivot");
            float[] rotation = vec(json, "rotation");
            Node node = new Node(entry.getKey(), pivot[0] - parentPivot[0], -(pivot[1] - parentPivot[1]), pivot[2] - parentPivot[2],
                    (float) Math.toRadians(rotation[0]), (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2]),
                    HitShapes.groupOf(groups, entry.getKey()));
            addCorners(node, json, pivot);
            out.byName.put(node.name, node);
        }
        for (Map.Entry<String, JsonObject> entry : raw.entrySet()) {
            Node node = out.byName.get(entry.getKey());
            String parentName = entry.getValue().has("parent") ? entry.getValue().get("parent").getAsString() : "";
            Node parent = out.byName.get(parentName);
            if (parent == null) {
                out.roots.add(node);
            } else {
                node.parent = parent;
                parent.children.add(node);
            }
        }
        return out;
    }

    private static void addCorners(Node node, JsonObject bone, float[] pivot) {
        JsonArray cubes = bone.getAsJsonArray("cubes");
        if (cubes == null) return;
        for (JsonElement element : cubes) {
            JsonObject cube = element.getAsJsonObject();
            float[] origin = vec(cube, "origin"), size = vec(cube, "size");
            float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0f;
            float x = origin[0] - pivot[0];
            float y = -(origin[1] - pivot[1]) - size[1];
            float z = origin[2] - pivot[2];
            Quaternionf turn = null;
            float px = 0f, py = 0f, pz = 0f;
            if (cube.has("rotation")) {
                float[] rotation = vec(cube, "rotation");
                float[] cubePivot = cube.has("pivot") ? vec(cube, "pivot") : origin;
                px = cubePivot[0] - pivot[0];
                py = -(cubePivot[1] - pivot[1]);
                pz = cubePivot[2] - pivot[2];
                turn = new Quaternionf().rotationZYX((float) Math.toRadians(rotation[2]), (float) Math.toRadians(rotation[1]),
                        (float) Math.toRadians(rotation[0]));
            }
            Vector3f corner = new Vector3f();
            for (int c = 0; c < 8; c++) {
                corner.set((c & 1) == 0 ? x - inflate : x + size[0] + inflate,
                        (c & 2) == 0 ? y - inflate : y + size[1] + inflate,
                        (c & 4) == 0 ? z - inflate : z + size[2] + inflate);
                if (turn != null) {
                    corner.sub(px, py, pz);
                    turn.transform(corner);
                    corner.add(px, py, pz);
                }
                node.corners.add(new float[]{corner.x, corner.y, corner.z});
            }
        }
    }

    static float[] vec(JsonObject json, String key) {
        float[] out = new float[3];
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
