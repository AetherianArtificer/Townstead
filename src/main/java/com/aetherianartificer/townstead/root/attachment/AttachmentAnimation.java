package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A parsed Blockbench/Bedrock {@code .animation.json} file: named clips of per-bone
 * keyframe channels. Pure data + Gson, shared by the server (doctor validation) and
 * the client (playback), so it must stay free of sided imports.
 *
 * <p>Times are converted to ticks at parse (Bedrock authors in seconds), values stay
 * in the file's own convention — rotation degrees and position pixels in Bedrock
 * space, exactly what Blockbench shows — and the application side does the Java-space
 * sign flips, same contract as pose bone rotations. Keyframe values may be numbers,
 * {@code [x, y, z]} arrays, or {@code pre}/{@code post} objects; Molang expressions
 * are not evaluated (the offending value reads as 0 and the parse warning names it —
 * bake expressions to keyframes in Blockbench before export).</p>
 */
public final class AttachmentAnimation {

    /** What happens when a clip's time passes its length. */
    public enum Loop { ONCE, LOOP, HOLD }

    /** One bone's channels within a clip; any may be null when unauthored. */
    public record BoneTrack(@Nullable Channel rotation, @Nullable Channel position, @Nullable Channel scale) {}

    private AttachmentAnimation() {}

    /** One named animation: length in ticks, end behavior, and per-bone tracks. */
    public static final class Clip {
        public final String name;
        public final float lengthTicks;
        public final Loop loop;
        public final Map<String, BoneTrack> bones;

        Clip(String name, float lengthTicks, Loop loop, Map<String, BoneTrack> bones) {
            this.name = name;
            this.lengthTicks = lengthTicks;
            this.loop = loop;
            this.bones = bones;
        }
    }

    /** One channel's keyframes, time-ordered; linear interpolation between them. */
    public static final class Channel {
        final float[] times;      // ticks, ascending
        final float[][] values;
        final String[] easing;

        Channel(float[] times, float[][] values) {
            this(times, values, new String[times.length]);
        }

        Channel(float[] times, float[][] values, String[] easing) {
            this.times = times;
            this.values = values;
            this.easing = easing;
        }

        /** The interpolated value at {@code timeTicks} (clamped to the first/last keyframe). */
        public float[] sample(float timeTicks, float[] out) {
            if (timeTicks <= times[0]) return copy(values[0], out);
            int last = times.length - 1;
            if (timeTicks >= times[last]) return copy(values[last], out);
            int i = 1;
            while (times[i] < timeTicks) i++;
            float alpha = (timeTicks - times[i - 1]) / (times[i] - times[i - 1]);
            alpha = ease(easing[i], alpha);
            for (int a = 0; a < 3; a++) {
                out[a] = values[i - 1][a] + (values[i][a] - values[i - 1][a]) * alpha;
            }
            return out;
        }

        private static float ease(String name, float t) {
            if (name == null) return t;
            return switch (name) {
                case "easeInSine" -> 1f - (float) Math.cos(t * Math.PI / 2);
                case "easeOutSine" -> (float) Math.sin(t * Math.PI / 2);
                case "easeInOutSine" -> (1f - (float) Math.cos(t * Math.PI)) / 2f;
                case "easeInCubic" -> t * t * t;
                case "easeOutCubic" -> 1f - (float) Math.pow(1f - t, 3);
                case "easeInOutCubic" -> t < .5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
                case "easeInOutQuad" -> t < .5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
                default -> t;
            };
        }

        private static float[] copy(float[] from, float[] out) {
            System.arraycopy(from, 0, out, 0, 3);
            return out;
        }
    }

    /**
     * Every clip in the file, keyed by its authored name (Blockbench default names
     * look like {@code animation.model.flick}; the def references the trailing
     * segment or the full name). Returns an empty map on a malformed file; parse
     * problems that don't abort (Molang values, empty channels) are appended to
     * {@code warnings} when non-null.
     */
    public static Map<String, Clip> parse(JsonObject root, @Nullable List<String> warnings) {
        Map<String, Clip> out = new LinkedHashMap<>();
        if (!root.has("animations") || !root.get("animations").isJsonObject()) return out;
        for (var entry : root.getAsJsonObject("animations").entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject clip = entry.getValue().getAsJsonObject();
            Loop loop = Loop.ONCE;
            JsonElement loopField = clip.get("loop");
            if (loopField != null && loopField.isJsonPrimitive()) {
                if (loopField.getAsJsonPrimitive().isBoolean() && loopField.getAsBoolean()) loop = Loop.LOOP;
                else if (loopField.getAsJsonPrimitive().isString()
                        && loopField.getAsString().equals("hold_on_last_frame")) loop = Loop.HOLD;
            }
            float lengthTicks = 20f * (clip.has("animation_length")
                    ? clip.get("animation_length").getAsFloat() : 0f);
            Map<String, BoneTrack> bones = new LinkedHashMap<>();
            if (clip.has("bones") && clip.get("bones").isJsonObject()) {
                for (var bone : clip.getAsJsonObject("bones").entrySet()) {
                    if (!bone.getValue().isJsonObject()) continue;
                    JsonObject track = bone.getValue().getAsJsonObject();
                    String where = entry.getKey() + "/" + bone.getKey();
                    Channel rotation = channel(track.get("rotation"), 0f, where, warnings);
                    Channel position = channel(track.get("position"), 0f, where, warnings);
                    Channel scale = channel(track.get("scale"), 1f, where, warnings);
                    if (rotation == null && position == null && scale == null) continue;
                    bones.put(bone.getKey(), new BoneTrack(rotation, position, scale));
                    if (rotation != null) lengthTicks = Math.max(lengthTicks, lastTime(rotation));
                    if (position != null) lengthTicks = Math.max(lengthTicks, lastTime(position));
                    if (scale != null) lengthTicks = Math.max(lengthTicks, lastTime(scale));
                }
            }
            if (bones.isEmpty()) {
                if (warnings != null) warnings.add("clip '" + entry.getKey() + "' has no bone tracks");
                continue;
            }
            out.put(entry.getKey(), new Clip(entry.getKey(), Math.max(1f, lengthTicks), loop, bones));
        }
        return out;
    }

    private static float lastTime(Channel channel) {
        return channel.times[channel.times.length - 1];
    }

    /** A channel from a Bedrock value: keyframe map, flat array, or single number. */
    @Nullable
    private static Channel channel(@Nullable JsonElement element, float defaultComponent,
                                   String where, @Nullable List<String> warnings) {
        if (element == null) return null;
        if (element.isJsonObject()) {
            JsonObject frames = element.getAsJsonObject();
            if (frames.has("vector") || frames.has("pre") || frames.has("post")) {
                return new Channel(new float[]{0f}, new float[][]{vec(element, defaultComponent, where, warnings)});
            }
            Map<Float, String> easings = new LinkedHashMap<>();
            List<float[]> entries = new ArrayList<>(frames.size());   // (time, x, y, z)
            for (var frame : frames.entrySet()) {
                float time;
                try {
                    time = 20f * Float.parseFloat(frame.getKey());
                } catch (NumberFormatException e) {
                    continue;   // non-time key ("lerp_mode" etc. never appear at this level)
                }
                float[] value = vec(frame.getValue(), defaultComponent, where, warnings);
                entries.add(new float[]{time, value[0], value[1], value[2]});
                if (frame.getValue().isJsonObject() && frame.getValue().getAsJsonObject().has("easing")) {
                    easings.put(time, frame.getValue().getAsJsonObject().get("easing").getAsString());
                }
            }
            if (entries.isEmpty()) return null;
            entries.sort((a, b) -> Float.compare(a[0], b[0]));
            float[] times = new float[entries.size()];
            float[][] values = new float[entries.size()][];
            String[] easing = new String[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                times[i] = entries.get(i)[0];
                easing[i] = easings.get(times[i]);
                values[i] = new float[]{entries.get(i)[1], entries.get(i)[2], entries.get(i)[3]};
            }
            return new Channel(times, values, easing);
        }
        // A flat value is a single keyframe held for the whole clip.
        float[] value = vec(element, defaultComponent, where, warnings);
        return new Channel(new float[]{0f}, new float[][]{value});
    }

    /** A keyframe value: [x,y,z], single number (uniform), or a pre/post object (post wins). */
    private static float[] vec(JsonElement element, float defaultComponent,
                               String where, @Nullable List<String> warnings) {
        float[] out = {defaultComponent, defaultComponent, defaultComponent};
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement inner = object.has("vector") ? object.get("vector")
                    : object.has("post") ? object.get("post") : object.get("pre");
            return inner == null ? out : vec(inner, defaultComponent, where, warnings);
        }
        if (element.isJsonArray()) {
            var array = element.getAsJsonArray();
            for (int i = 0; i < 3 && i < array.size(); i++) {
                out[i] = number(array.get(i), defaultComponent, where, warnings);
            }
            return out;
        }
        float uniform = number(element, defaultComponent, where, warnings);
        out[0] = uniform;
        out[1] = uniform;
        out[2] = uniform;
        return out;
    }

    private static float number(JsonElement element, float fallback,
                                String where, @Nullable List<String> warnings) {
        try {
            return element.getAsFloat();
        } catch (Exception e) {
            if (warnings != null) {
                warnings.add(where + ": non-numeric keyframe value '" + element
                        + "' (Molang isn't evaluated; bake it to keyframes in Blockbench)");
            }
            return fallback;
        }
    }
}
