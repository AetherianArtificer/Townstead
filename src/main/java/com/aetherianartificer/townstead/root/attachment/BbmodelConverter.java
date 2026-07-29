package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.GsonHelper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a Blockbench project file ({@code .bbmodel}) into the Bedrock-dialect
 * geometry and animation JSON the attachment pipeline already speaks, so packs can
 * ship Blockbench projects directly ({@code attachment/bbmodel/<name>.bbmodel}) and
 * the converted bytes ride the existing content-addressed blob sync.
 *
 * <p>Transform: coordinates pass through in the project's own space (NO X mirror);
 * rotations map per-axis as {@code (-x, -y, z)}. The loader's own Bedrock→Java Y-flip
 * (and its {@code (x,-y,-z)} rotation flip) is the whole coordinate change; an earlier
 * X-mirror here rendered every model left-right flipped (invisible on symmetric models)
 * and, combined with the rotation map, bent multi-axis cubes — verified by rendering the
 * converted geometry through the exact loader math against Blockbench's ZYX display.
 * Faces keep their slot and native UV rect; {@code box_uv} projects emit native box UV.
 * Coordinates keep the project's own origin (feet, for an avatar) — the definition's
 * {@code offset} places the model.</p>
 *
 * <p>A geometry reference may name an embedded animation as a static pose:
 * {@code "ns:file#poseName"} bakes that animation's first rotation keyframe per bone
 * additively over the rest pose (a Figura-style state pose baked at load). Embedded
 * animations also convert wholesale, keyed {@code animation.<file>.<name>}, so
 * {@code "file#clip"} animation references work against a bbmodel file too.</p>
 *
 * <p>Blockbench 5.0 flipped the X/Y sign convention of animation rotation keyframes
 * to match group rotations (its project codec inverts them when loading older files).
 * Pre-5.0 values are therefore already in the Bedrock convention our dialect uses and
 * pass verbatim; 5.0+ values take the same {@code (-x, -y, z)} transform as groups.
 * The flip is rotation-only: position keyframes take the {@code -x} mirror the pivots
 * take (nothing else), and scale keyframes are axis-signless and pass through.</p>
 */
public final class BbmodelConverter {

    private BbmodelConverter() {}

    /** Converted geometry JSON bytes, or null when the file doesn't parse. */
    public static byte[] geometry(byte[] bbmodel, String fileName, String pose) {
        try {
            JsonObject project = JsonParser.parseString(new String(bbmodel, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, JsonObject> elements = elementsById(project);
            Map<String, JsonObject> groups = groupsById(project);
            Map<String, float[]> poseDeltas = pose == null || pose.isEmpty()
                    ? Map.of() : poseDeltas(project, pose);

            JsonObject resolution = GsonHelper.getAsJsonObject(project, "resolution", new JsonObject());
            JsonObject description = new JsonObject();
            description.addProperty("identifier", "geometry." + fileName + (pose == null || pose.isEmpty() ? "" : "_" + pose));
            description.addProperty("texture_width", GsonHelper.getAsInt(resolution, "width", 16));
            description.addProperty("texture_height", GsonHelper.getAsInt(resolution, "height", 16));
            description.addProperty("visible_bounds_width", 4);
            description.addProperty("visible_bounds_height", 4);

            boolean boxUv = GsonHelper.getAsBoolean(
                    GsonHelper.getAsJsonObject(project, "meta", new JsonObject()), "box_uv", false);
            JsonArray bones = new JsonArray();
            for (JsonElement root : GsonHelper.getAsJsonArray(project, "outliner", new JsonArray())) {
                if (root.isJsonObject()) {
                    convertBone(root.getAsJsonObject(), null, elements, groups, poseDeltas, boxUv, bones);
                }
            }

            JsonObject geometry = new JsonObject();
            geometry.add("description", description);
            geometry.add("bones", bones);
            JsonArray geometries = new JsonArray();
            geometries.add(geometry);
            JsonObject out = new JsonObject();
            out.addProperty("format_version", "1.12.0");
            out.add("minecraft:geometry", geometries);
            return out.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            com.aetherianartificer.townstead.Townstead.LOGGER.error("Failed to convert bbmodel {}", fileName, e);
            return null;
        }
    }

    /**
     * Every embedded animation as a clip file ({@code animation.<file>.<name>}), or null.
     * All three keyframe channels convert: rotation (through the display->geo rotation
     * transform, with the 5.0 legacy flip), position (mirrored on X like the pivots, so a
     * bob authored upward reads upward), and scale (per-axis multipliers, default 1). A bone
     * contributes a track if it keyframes any channel; a clip with no tracked bone at all is
     * dropped, and a file with no surviving clip returns null.
     */
    public static byte[] animations(byte[] bbmodel, String fileName) {
        try {
            JsonObject project = JsonParser.parseString(new String(bbmodel, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject clips = new JsonObject();
            for (JsonElement element : GsonHelper.getAsJsonArray(project, "animations", new JsonArray())) {
                JsonObject anim = element.getAsJsonObject();
                String name = GsonHelper.getAsString(anim, "name", "");
                if (name.isEmpty()) continue;
                JsonObject bones = new JsonObject();
                boolean legacy = legacyAnimations(project);
                for (var animator : GsonHelper.getAsJsonObject(anim, "animators", new JsonObject()).entrySet()) {
                    if (!animator.getValue().isJsonObject()) continue;
                    JsonObject channel = animator.getValue().getAsJsonObject();
                    String boneName = GsonHelper.getAsString(channel, "name", "");
                    JsonObject rotation = new JsonObject();
                    JsonObject position = new JsonObject();
                    JsonObject scale = new JsonObject();
                    for (JsonElement kfElement : GsonHelper.getAsJsonArray(channel, "keyframes", new JsonArray())) {
                        JsonObject kf = kfElement.getAsJsonObject();
                        String kfChannel = GsonHelper.getAsString(kf, "channel", "");
                        String time = trimFloat(GsonHelper.getAsFloat(kf, "time", 0f));
                        switch (kfChannel) {
                            case "rotation" -> {
                                float[] v = dataPoint(kf, 0f);
                                if (v == null) break;
                                // Legacy files store animation values with X/Y inverted vs display.
                                float[] display = legacy ? new float[]{-v[0], -v[1], v[2]} : v;
                                float[] geo = toGeoRotation(display);
                                rotation.add(time, array(geo[0], geo[1], geo[2]));
                            }
                            case "position" -> {
                                float[] v = dataPoint(kf, 0f);
                                if (v == null) break;
                                // Same X mirror the pivots take (display -> geo); Y/Z pass through,
                                // and the renderer negates Y again for Java's y-down model space.
                                position.add(time, array(-v[0], v[1], v[2]));
                            }
                            case "scale" -> {
                                // Per-axis multipliers: an omitted axis is 1, not 0, or the bone
                                // collapses on the axes the author didn't touch.
                                float[] v = dataPoint(kf, 1f);
                                if (v == null) break;
                                scale.add(time, array(v[0], v[1], v[2]));
                            }
                            default -> { }
                        }
                    }
                    boolean any = !rotation.entrySet().isEmpty() || !position.entrySet().isEmpty()
                            || !scale.entrySet().isEmpty();
                    if (any && !boneName.isEmpty()) {
                        JsonObject track = new JsonObject();
                        if (!rotation.entrySet().isEmpty()) track.add("rotation", rotation);
                        if (!position.entrySet().isEmpty()) track.add("position", position);
                        if (!scale.entrySet().isEmpty()) track.add("scale", scale);
                        bones.add(boneName, track);
                    }
                }
                if (bones.entrySet().isEmpty()) continue;
                JsonObject clip = new JsonObject();
                clip.addProperty("animation_length", GsonHelper.getAsFloat(anim, "length", 1f));
                String loop = GsonHelper.getAsString(anim, "loop", "once");
                if (loop.equals("loop")) clip.addProperty("loop", true);
                else if (loop.equals("hold")) clip.addProperty("loop", "hold_on_last_frame");
                clip.add("bones", bones);
                clips.add("animation." + fileName + "." + name, clip);
            }
            if (clips.entrySet().isEmpty()) return null;
            JsonObject out = new JsonObject();
            out.addProperty("format_version", "1.8.0");
            out.add("animations", clips);
            return out.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            com.aetherianartificer.townstead.Townstead.LOGGER.error("Failed to convert bbmodel animations {}", fileName, e);
            return null;
        }
    }

    // --- geometry internals ---

    private static void convertBone(JsonObject node, String parent, Map<String, JsonObject> elements,
                                    Map<String, JsonObject> groups, Map<String, float[]> poseDeltas,
                                    boolean boxUv, JsonArray out) {
        // Blockbench 5.0 projects keep group properties in a top-level "groups" array;
        // the outliner node then carries only uuid + children.
        JsonObject props = node.has("name") ? node
                : groups.getOrDefault(GsonHelper.getAsString(node, "uuid", ""), node);
        String name = GsonHelper.getAsString(props, "name", "");
        JsonObject bone = new JsonObject();
        bone.addProperty("name", name);
        if (parent != null) bone.addProperty("parent", parent);
        float[] origin = vec(props, "origin");
        bone.add("pivot", array(origin[0], origin[1], origin[2]));
        float[] rot = vec(props, "rotation");
        float[] delta = poseDeltas.get(name);
        if (delta != null) {
            rot = new float[]{rot[0] + delta[0], rot[1] + delta[1], rot[2] + delta[2]};
        }
        if (rot[0] != 0f || rot[1] != 0f || rot[2] != 0f) {
            float[] geo = toGeoRotation(rot);
            bone.add("rotation", array(geo[0], geo[1], geo[2]));
        }
        JsonArray cubes = new JsonArray();
        for (JsonElement child : GsonHelper.getAsJsonArray(node, "children", new JsonArray())) {
            if (child.isJsonPrimitive()) {
                JsonObject element = elements.get(child.getAsString());
                if (element != null && "cube".equals(GsonHelper.getAsString(element, "type", "cube"))) {
                    cubes.add(convertCube(element, boxUv));
                }
            } else if (child.isJsonObject()) {
                convertBone(child.getAsJsonObject(), name, elements, groups, poseDeltas, boxUv, out);
            }
        }
        if (!cubes.isEmpty()) bone.add("cubes", cubes);
        out.add(bone);
    }

    private static JsonObject convertCube(JsonObject element, boolean boxUv) {
        float[] from = vec(element, "from");
        float[] to = vec(element, "to");
        JsonObject cube = new JsonObject();
        // No X mirror: coordinates pass through in the project's own space (min corner = origin).
        cube.add("origin", array(Math.min(from[0], to[0]),
                Math.min(from[1], to[1]), Math.min(from[2], to[2])));
        cube.add("size", array(Math.abs(to[0] - from[0]),
                Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2])));
        float inflate = GsonHelper.getAsFloat(element, "inflate", 0f);
        if (inflate != 0f) cube.addProperty("inflate", inflate);
        float[] rot = vec(element, "rotation");
        if (rot[0] != 0f || rot[1] != 0f || rot[2] != 0f) {
            float[] geo = toGeoRotation(rot);
            cube.add("rotation", array(geo[0], geo[1], geo[2]));
            float[] pivot = vec(element, "origin");
            cube.add("pivot", array(pivot[0], pivot[1], pivot[2]));
        }
        // Box UV is decided PER ELEMENT (the project meta.box_uv is only the default —
        // a project can say box_uv:true while every element overrides to hand-mapped
        // per-face UVs; emitting box layout for those samples unpainted texture and
        // renders invisible). Box-UV elements emit native box UV (+ mirror flag).
        if (GsonHelper.getAsBoolean(element, "box_uv", boxUv)) {
            JsonArray offset = GsonHelper.getAsJsonArray(element, "uv_offset", null);
            JsonArray uvArray = new JsonArray();
            uvArray.add(offset != null && offset.size() > 0 ? offset.get(0).getAsFloat() : 0f);
            uvArray.add(offset != null && offset.size() > 1 ? offset.get(1).getAsFloat() : 0f);
            cube.add("uv", uvArray);
            // The Bedrock->Java Y flip and the renderer's own scale(-1,-1,1) compose to a net X
            // reflection, so a drawn cube's texture is mirrored left-right against how Blockbench shows
            // it. Symmetric geometry hides that; the texture does not, and it reads worst on rotated
            // limbs (a leg's pale end lands at the wrong end). The mirror flag re-flips the texture in X
            // without moving the box, so invert the authored mirror rather than passing it through.
            if (!GsonHelper.getAsBoolean(element, "mirror_uv", false)) {
                cube.addProperty("mirror", true);
            }
            return cube;
        }
        JsonObject faces = GsonHelper.getAsJsonObject(element, "faces", new JsonObject());
        JsonObject uv = new JsonObject();
        for (var entry : faces.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject face = entry.getValue().getAsJsonObject();
            if (face.get("texture") == null || face.get("texture").isJsonNull()) continue;
            JsonArray rect = GsonHelper.getAsJsonArray(face, "uv", null);
            if (rect == null || rect.size() < 4) continue;
            float u1 = rect.get(0).getAsFloat(), v1 = rect.get(1).getAsFloat();
            float u2 = rect.get(2).getAsFloat(), v2 = rect.get(3).getAsFloat();
            // No X mirror: faces keep their slot and native UV rect.
            String slot = entry.getKey().toLowerCase(Locale.ROOT);
            JsonObject faceUv = new JsonObject();
            faceUv.add("uv", array2(u1, v1));
            faceUv.add("uv_size", array2(u2 - u1, v2 - v1));
            uv.add(slot, faceUv);
        }
        if (!uv.entrySet().isEmpty()) cube.add("uv", uv);
        return cube;
    }

    /** Whether animation keyframes use the pre-5.0 (Bedrock-matching) sign convention. */
    private static boolean legacyAnimations(JsonObject project) {
        JsonObject meta = GsonHelper.getAsJsonObject(project, "meta", new JsonObject());
        String version = GsonHelper.getAsString(meta, "format_version",
                GsonHelper.getAsString(meta, "format", "4.0"));
        try {
            int dot = version.indexOf('.');
            return Integer.parseInt(dot < 0 ? version : version.substring(0, dot)) < 5;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /**
     * First rotation keyframe per bone of the named embedded animation (a static pose),
     * in GROUP convention (added to the rest rotation before the group transform).
     */
    private static Map<String, float[]> poseDeltas(JsonObject project, String pose) {
        boolean legacy = legacyAnimations(project);
        Map<String, float[]> out = new LinkedHashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(project, "animations", new JsonArray())) {
            JsonObject anim = element.getAsJsonObject();
            if (!pose.equals(GsonHelper.getAsString(anim, "name", ""))) continue;
            for (var animator : GsonHelper.getAsJsonObject(anim, "animators", new JsonObject()).entrySet()) {
                if (!animator.getValue().isJsonObject()) continue;
                JsonObject channel = animator.getValue().getAsJsonObject();
                String boneName = GsonHelper.getAsString(channel, "name", "");
                JsonObject best = null;
                float bestTime = Float.MAX_VALUE;
                for (JsonElement kfElement : GsonHelper.getAsJsonArray(channel, "keyframes", new JsonArray())) {
                    JsonObject kf = kfElement.getAsJsonObject();
                    if (!"rotation".equals(GsonHelper.getAsString(kf, "channel", ""))) continue;
                    float time = GsonHelper.getAsFloat(kf, "time", 0f);
                    if (time < bestTime) {
                        bestTime = time;
                        best = kf;
                    }
                }
                float[] v = best == null ? null : dataPoint(best);
                if (v != null && !boneName.isEmpty()) {
                    out.put(boneName, legacy ? new float[]{-v[0], -v[1], v[2]} : v);
                }
            }
        }
        return out;
    }

    private static float[] dataPoint(JsonObject keyframe) {
        return dataPoint(keyframe, 0f);
    }

    /**
     * A keyframe's first data point, with {@code missing} standing in for an axis the
     * keyframe leaves blank (0 for rotation/position, 1 for scale).
     */
    private static float[] dataPoint(JsonObject keyframe, float missing) {
        JsonArray points = GsonHelper.getAsJsonArray(keyframe, "data_points", null);
        if (points == null || points.isEmpty() || !points.get(0).isJsonObject()) return null;
        JsonObject dp = points.get(0).getAsJsonObject();
        try {
            return new float[]{axis(dp, "x", missing), axis(dp, "y", missing), axis(dp, "z", missing)};
        } catch (NumberFormatException e) {
            return null;   // Molang expression; poses need numeric keyframes
        }
    }

    /** bbmodel data points store numbers as strings (and may hold Molang or an empty axis). */
    private static float axis(JsonObject dp, String key, float missing) {
        JsonElement value = dp.get(key);
        if (value == null || value.isJsonNull()) return missing;
        String raw = value.getAsString().trim();
        return raw.isEmpty() ? missing : Float.parseFloat(raw);
    }

    /**
     * Blockbench display rotation (degrees) → geo-dialect rotation: the per-axis sign
     * map {@code (-x, -y, z)}, identical to Blockbench's bedrock export codec. Both
     * sides compose Euler ZYX, so the map is exact for multi-axis rotations too.
     */
    private static float[] toGeoRotation(float[] bb) {
        return new float[]{-bb[0], -bb[1], bb[2]};
    }

    private static float[] vec(JsonObject json, String key) {
        float[] out = new float[3];
        JsonArray array = GsonHelper.getAsJsonArray(json, key, null);
        if (array != null) {
            for (int i = 0; i < 3 && i < array.size(); i++) out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static Map<String, JsonObject> groupsById(JsonObject project) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(project, "groups", new JsonArray())) {
            if (!element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            out.put(GsonHelper.getAsString(o, "uuid", ""), o);
        }
        return out;
    }

    private static Map<String, JsonObject> elementsById(JsonObject project) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(project, "elements", new JsonArray())) {
            JsonObject o = element.getAsJsonObject();
            out.put(GsonHelper.getAsString(o, "uuid", ""), o);
        }
        return out;
    }

    private static String trimFloat(float f) {
        return f == (long) f ? String.valueOf((long) f) : String.valueOf(f);
    }

    private static JsonArray array(float x, float y, float z) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        a.add(z);
        return a;
    }

    private static JsonArray array2(float x, float y) {
        JsonArray a = new JsonArray();
        a.add(x);
        a.add(y);
        return a;
    }
}
