package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Slices one bone of a Bedrock geometry into a run of chained sub-bones so a model
 * that was never authored as a chain can bend — the physics equivalent of bendy-lib's
 * cuboid subdivision, done once at bake time on the geometry JSON so the ordinary
 * loader, physics, morphs, and mirroring all apply unchanged.
 *
 * <p>The bone's cubes are cut along one axis ({@code auto} = the bone's longest
 * extent) into equal slices ordered from the end nearest the bone pivot (the chain
 * root) outward. The first slice keeps the original bone name, pivot, rotation, and
 * parent; each further slice becomes {@code <name>__segK} parented to the previous
 * with its pivot on the cut plane. Box-UV cubes get exact per-face UV sub-rects, so
 * the texture doesn't smear or shift. Cubes that can't be cut cleanly (per-cube
 * rotation, per-face UV) ride whole in the slice holding their center. Children of
 * the segmented bone re-parent to the tip slice (tufts follow the tip).</p>
 */
public final class AttachmentGeoSegmenter {

    private static final float EPS = 1e-3f;

    private AttachmentGeoSegmenter() {}

    /**
     * A deep copy of {@code root} with {@code boneName} sliced into {@code segments}
     * chained bones along {@code axis} ("x"/"y"/"z"/"auto"). Returns the untouched
     * copy when the bone is missing or has no sliceable extent.
     */
    public static JsonObject segment(JsonObject root, String boneName, int segments, String axis) {
        JsonObject out = root.deepCopy();
        try {
            JsonArray geometries = GsonHelper.getAsJsonArray(out, "minecraft:geometry");
            if (geometries.isEmpty()) return out;
            JsonObject geometry = geometries.get(0).getAsJsonObject();
            JsonArray bones = GsonHelper.getAsJsonArray(geometry, "bones", new JsonArray());
            JsonObject target = null;
            for (JsonElement element : bones) {
                if (GsonHelper.getAsString(element.getAsJsonObject(), "name", "").equals(boneName)) {
                    target = element.getAsJsonObject();
                    break;
                }
            }
            if (target == null || segments < 2) return out;
            JsonArray cubes = GsonHelper.getAsJsonArray(target, "cubes", new JsonArray());
            if (cubes.isEmpty()) return out;

            // Bone extent per axis over all cubes, in absolute Bedrock coords.
            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (JsonElement element : cubes) {
                float[] origin = vec(element.getAsJsonObject(), "origin");
                float[] size = vec(element.getAsJsonObject(), "size");
                for (int i = 0; i < 3; i++) {
                    min[i] = Math.min(min[i], origin[i]);
                    max[i] = Math.max(max[i], origin[i] + size[i]);
                }
            }
            int a = axisIndex(axis, min, max);
            float lo = min[a], hi = max[a];
            if (hi - lo < EPS * segments) return out;

            // Root end = the end nearest the bone pivot; slices run root -> tip.
            float[] pivot = vec(target, "pivot");
            boolean rootAtMin = Math.abs(pivot[a] - lo) <= Math.abs(pivot[a] - hi);
            float step = (hi - lo) / segments;

            JsonArray[] sliceCubes = new JsonArray[segments];
            for (int s = 0; s < segments; s++) sliceCubes[s] = new JsonArray();
            for (JsonElement element : cubes) {
                JsonObject cube = element.getAsJsonObject();
                distribute(cube, a, lo, step, segments, rootAtMin, sliceCubes);
            }

            // Rebuild the bones array: the target keeps slice 0; __segK bones chain after it.
            JsonArray rebuilt = new JsonArray();
            for (JsonElement element : bones) {
                JsonObject bone = element.getAsJsonObject();
                if (bone == target) {
                    bone = bone.deepCopy();
                    bone.add("cubes", sliceCubes[0]);
                    rebuilt.add(bone);
                    String parent = boneName;
                    for (int s = 1; s < segments; s++) {
                        JsonObject seg = new JsonObject();
                        seg.addProperty("name", boneName + "__seg" + (s + 1));
                        seg.addProperty("parent", parent);
                        float boundary = rootAtMin ? lo + s * step : hi - s * step;
                        JsonArray segPivot = new JsonArray();
                        for (int i = 0; i < 3; i++) segPivot.add(i == a ? boundary : pivot[i]);
                        seg.add("pivot", segPivot);
                        seg.add("cubes", sliceCubes[s]);
                        rebuilt.add(seg);
                        parent = boneName + "__seg" + (s + 1);
                    }
                } else {
                    // Children of the segmented bone follow the tip slice (tufts and tips).
                    if (GsonHelper.getAsString(bone, "parent", "").equals(boneName)) {
                        bone = bone.deepCopy();
                        bone.addProperty("parent", boneName + "__seg" + segments);
                    }
                    rebuilt.add(bone);
                }
            }
            geometry.add("bones", rebuilt);
            return out;
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to segment attachment bone {}", boneName, e);
            return root.deepCopy();
        }
    }

    /** Assign or split one cube across the slices. */
    private static void distribute(JsonObject cube, int a, float lo, float step, int segments,
                                   boolean rootAtMin, JsonArray[] sliceCubes) {
        float[] origin = vec(cube, "origin");
        float[] size = vec(cube, "size");
        float cMin = origin[a], cMax = origin[a] + size[a];
        boolean splittable = !cube.has("rotation")
                && !(cube.has("uv") && cube.get("uv").isJsonObject())
                && size[a] > EPS;
        if (!splittable) {
            float center = (cMin + cMax) / 2f;
            sliceCubes[sliceFor(center, lo, step, segments, rootAtMin)].add(cube);
            return;
        }
        for (int s = 0; s < segments; s++) {
            float sA = rootAtMin ? lo + s * step : lo + (segments - 1 - s) * step;
            float sB = sA + step;
            float from = Math.max(cMin, sA);
            float to = Math.min(cMax, sB);
            if (to - from < EPS) continue;
            sliceCubes[s].add(subCube(cube, a, from, to, Math.abs(from - cMin) < EPS,
                    Math.abs(to - cMax) < EPS));
        }
    }

    private static int sliceFor(float value, float lo, float step, int segments, boolean rootAtMin) {
        int raw = (int) Math.min(segments - 1, Math.max(0, (value - lo) / step));
        return rootAtMin ? raw : segments - 1 - raw;
    }

    /**
     * The sub-cube covering {@code [from, to]} along axis {@code a}, its box UV
     * translated into exact per-face UV sub-rects (the vanilla box layout the loader
     * replicates). {@code hasMinCap}/{@code hasMaxCap} keep the end faces only on the
     * outermost slices.
     */
    private static JsonObject subCube(JsonObject cube, int a, float from, float to,
                                      boolean hasMinCap, boolean hasMaxCap) {
        float[] origin = vec(cube, "origin");
        float[] size = vec(cube, "size");
        float sx = size[0], sy = size[1], sz = size[2];
        float[] uvArr = {0, 0};
        if (cube.has("uv") && cube.get("uv").isJsonArray()) {
            JsonArray arr = cube.getAsJsonArray("uv");
            if (arr.size() > 0) uvArr[0] = arr.get(0).getAsFloat();
            if (arr.size() > 1) uvArr[1] = arr.get(1).getAsFloat();
        }
        float u = uvArr[0], v = uvArr[1];
        float la = from - origin[a];   // slice range local to the cube's min along the axis
        float lb = to - origin[a];

        JsonObject out = new JsonObject();
        JsonArray newOrigin = new JsonArray();
        JsonArray newSize = new JsonArray();
        for (int i = 0; i < 3; i++) {
            newOrigin.add(i == a ? from : origin[i]);
            newSize.add(i == a ? to - from : size[i]);
        }
        out.add("origin", newOrigin);
        out.add("size", newSize);
        if (cube.has("inflate")) out.add("inflate", cube.get("inflate"));
        if (cube.has("mirror")) out.add("mirror", cube.get("mirror"));

        JsonObject faces = new JsonObject();
        switch (a) {
            case 2 -> {  // Z: north/south are the caps; east/west/up/down carry z strips
                if (hasMinCap) face(faces, "north", u + sz, v + sz, sx, sy);
                if (hasMaxCap) face(faces, "south", u + 2 * sz + sx, v + sz, sx, sy);
                face(faces, "west", u + (sz - lb), v + sz, lb - la, sy);
                face(faces, "east", u + sz + sx + la, v + sz, lb - la, sy);
                face(faces, "up", u + sz, v + (sz - lb), sx, lb - la);
                face(faces, "down", u + sz + sx, v + sz - la, sx, la - lb);
            }
            case 1 -> {  // Y: up/down caps; all four sides carry y strips (v runs top=yMax)
                if (hasMaxCap) face(faces, "up", u + sz, v, sx, sz);
                if (hasMinCap) face(faces, "down", u + sz + sx, v + sz, sx, -sz);
                float vTop = v + sz + (sy - lb);
                face(faces, "west", u, vTop, sz, lb - la);
                face(faces, "north", u + sz, vTop, sx, lb - la);
                face(faces, "east", u + sz + sx, vTop, sz, lb - la);
                face(faces, "south", u + 2 * sz + sx, vTop, sx, lb - la);
            }
            default -> {  // X: east/west caps; north/south/up/down carry x strips
                if (hasMinCap) face(faces, "west", u, v + sz, sz, sy);
                if (hasMaxCap) face(faces, "east", u + sz + sx, v + sz, sz, sy);
                face(faces, "north", u + sz + la, v + sz, lb - la, sy);
                face(faces, "south", u + 2 * sz + sx + (sx - lb), v + sz, lb - la, sy);
                face(faces, "up", u + sz + la, v, lb - la, sz);
                face(faces, "down", u + sz + sx + la, v + sz, lb - la, -sz);
            }
        }
        out.add("uv", faces);
        return out;
    }

    private static void face(JsonObject faces, String key, float u, float v, float w, float h) {
        JsonObject entry = new JsonObject();
        JsonArray uv = new JsonArray();
        uv.add(u);
        uv.add(v);
        entry.add("uv", uv);
        JsonArray uvSize = new JsonArray();
        uvSize.add(w);
        uvSize.add(h);
        entry.add("uv_size", uvSize);
        faces.add(key, entry);
    }

    private static int axisIndex(String axis, float[] min, float[] max) {
        switch (axis == null ? "" : axis.toLowerCase(java.util.Locale.ROOT)) {
            case "x": return 0;
            case "y": return 1;
            case "z": return 2;
            default:
                int best = 2;
                for (int i = 0; i < 3; i++) {
                    if (max[i] - min[i] > max[best] - min[best] + EPS) best = i;
                }
                return best;
        }
    }

    private static float[] vec(JsonObject json, String key) {
        float[] out = new float[3];
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
