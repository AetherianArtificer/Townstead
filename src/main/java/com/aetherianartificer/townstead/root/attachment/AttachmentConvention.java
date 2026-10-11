package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Which way the numbers in an attachment file point.
 *
 * <p>{@code townstead:attachment/v2} (and {@code townstead:attachment_point/v2}) take every
 * hand-authored placement exactly as Blockbench shows it: {@code offset} in Blockbench pixels with
 * Y up, and {@code rotation} plus pose bone rotations as the degrees in Blockbench's rotation
 * panel. Copy the numbers across and the attachment sits and turns the way it does in Blockbench.</p>
 *
 * <p>The original {@code v1} files used Minecraft's model space for these: Y down for offsets, X
 * and Z reversed for whole-attachment rotations, X and Y reversed for pose bones (the .geo.json
 * convention). v1 still loads unchanged. A v2 file is converted to v1's internal form once, when
 * it loads, so the renderer, mirroring and every v1 pack behave exactly as before.</p>
 *
 * <p>Geometry and animation files are untouched by either version: a {@code .bbmodel}, or a
 * Blockbench-exported {@code .geo.json} or {@code .animation.json}, already renders as Blockbench
 * shows it.</p>
 *
 * <p>Each conversion is its own inverse, so the same calls turn internal values back into a v2
 * file's numbers for {@code /townstead attachment adjust} and {@code dump}.</p>
 */
public final class AttachmentConvention {

    public static final String ATTACHMENT_V2 = "townstead:attachment/v2";
    public static final String POINT_V2 = "townstead:attachment_point/v2";

    private AttachmentConvention() {}

    /** True when the file declares a Blockbench-space schema. */
    public static boolean isBlockbench(@Nullable JsonObject json) {
        if (json == null) return false;
        String schema = GsonHelper.getAsString(json, "schema", "");
        return schema.equals(ATTACHMENT_V2) || schema.equals(POINT_V2);
    }

    /** Blockbench offset (Y up) to and from Minecraft model pixels (Y down). */
    public static float[] offset(float[] v) {
        return new float[]{v[0], -v[1], v[2]};
    }

    /**
     * A whole-attachment rotation, Blockbench panel degrees to and from the model-space rotation
     * the renderer applies. Turning Y upside down reverses the sense of turns about X and Z.
     */
    public static float[] rotation(float[] v) {
        return new float[]{-v[0], v[1], -v[2]};
    }

    /** A pose bone rotation, Blockbench panel degrees to and from the .geo.json convention. */
    public static float[] boneRotation(float[] v) {
        return new float[]{-v[0], -v[1], v[2]};
    }

    /** The file's placements in v1's internal form: a copy for a v2 file, the file itself otherwise. */
    public static JsonObject normalize(JsonObject json) {
        if (!isBlockbench(json)) return json;
        JsonObject out = json.deepCopy();
        convert(out, "offset", AttachmentConvention::offset);
        convert(out, "rotation", AttachmentConvention::rotation);
        if (out.has("stages") && out.get("stages").isJsonObject()) {
            for (var stage : out.getAsJsonObject("stages").entrySet()) {
                if (stage.getValue().isJsonObject()) convert(stage.getValue().getAsJsonObject(), "offset", AttachmentConvention::offset);
            }
        }
        if (out.has("poses") && out.get("poses").isJsonObject()) {
            JsonObject poses = out.getAsJsonObject("poses");
            for (var entry : poses.entrySet()) {
                if (entry.getValue().isJsonObject()) pose(entry.getValue().getAsJsonObject());
            }
            if (poses.has("when") && poses.get("when").isJsonArray()) {
                for (JsonElement entry : poses.getAsJsonArray("when")) {
                    if (entry.isJsonObject()) pose(entry.getAsJsonObject());
                }
            }
        }
        return out;
    }

    private static void pose(JsonObject pose) {
        convert(pose, "rotation", AttachmentConvention::rotation);
        if (!pose.has("bones") || !pose.get("bones").isJsonObject()) return;
        for (var bone : pose.getAsJsonObject("bones").entrySet()) {
            if (bone.getValue().isJsonObject()) convert(bone.getValue().getAsJsonObject(), "rotation", AttachmentConvention::boneRotation);
        }
    }

    private static void convert(JsonObject json, String key, java.util.function.UnaryOperator<float[]> fn) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return;
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != 3) return;
        float[] v = new float[3];
        for (int i = 0; i < 3; i++) {
            if (!array.get(i).isJsonPrimitive() || !array.get(i).getAsJsonPrimitive().isNumber()) return;
            v[i] = array.get(i).getAsFloat();
        }
        json.add(key, array(fn.apply(v)));
    }

    /** A JSON number array, writing whole numbers without a trailing ".0" and never a negative zero. */
    public static JsonArray array(float[] v) {
        JsonArray out = new JsonArray();
        for (float f : v) {
            float clean = f == 0f ? 0f : f;
            out.add(clean == Math.rint(clean) && Math.abs(clean) < 1e7
                    ? new JsonPrimitive((long) clean) : new JsonPrimitive(clean));
        }
        return out;
    }
}
