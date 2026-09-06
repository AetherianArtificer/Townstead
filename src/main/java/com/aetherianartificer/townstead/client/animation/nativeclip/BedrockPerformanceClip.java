package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Numeric Bedrock animation exports. Times remain fractional ticks; no integer-frame baking. */
public record BedrockPerformanceClip(float durationTicks, Loop loop, Map<String, Track> bones) {
    public enum Loop { ONCE, LOOP, HOLD }
    public record Track(Channel rotation, Channel position, Channel scale) {}
    private record Frame(float tick, float[] pre, float[] post, boolean smooth) {}
    public static final Set<String> BONES = Set.of("root", "head", "body", "torso", "left_arm", "right_arm",
            "left_leg", "right_leg", "left_forearm", "right_forearm", "left_shin", "right_shin");

    public static final class Channel {
        private final List<Frame> frames;
        private Channel(List<Frame> frames) { this.frames = List.copyOf(frames); }
        public float endTick() { return frames.get(frames.size() - 1).tick; }
        public float[] sample(float tick) {
            Frame first = frames.get(0);
            if (tick < first.tick) return first.pre.clone();
            for (int i = 1; i < frames.size(); i++) {
                Frame b = frames.get(i), a = frames.get(i - 1);
                if (tick >= b.tick) continue;
                float t = (tick - a.tick) / (b.tick - a.tick);
                float[] out = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    float p1 = a.post[axis], p2 = b.pre[axis];
                    if (a.smooth || b.smooth) {
                        float p0 = frames.get(Math.max(0, i - 2)).post[axis];
                        float p3 = frames.get(Math.min(frames.size() - 1, i + 1)).pre[axis];
                        out[axis] = .5F * ((2 * p1) + (-p0 + p2) * t
                                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t
                                + (-p0 + 3 * p1 - 3 * p2 + p3) * t * t * t);
                    } else out[axis] = p1 + (p2 - p1) * t;
                }
                return out;
            }
            return frames.get(frames.size() - 1).post.clone();
        }
    }

    /** Map keys are full exported names, not ambiguous final name segments. */
    public static Map<String, BedrockPerformanceClip> parse(JsonObject root) {
        if (!root.has("format_version")) throw new IllegalArgumentException("Missing Bedrock format_version");
        JsonObject animations = root.getAsJsonObject("animations");
        if (animations == null || animations.size() == 0) throw new IllegalArgumentException("No animations");
        Map<String, BedrockPerformanceClip> result = new LinkedHashMap<>();
        for (var entry : animations.entrySet()) {
            JsonObject json = entry.getValue().getAsJsonObject();
            for (String key : json.keySet()) {
                if (!Set.of("animation_length", "loop", "bones").contains(key))
                    throw new IllegalArgumentException(entry.getKey() + ": unsupported animation field " + key);
            }
            Loop loop = Loop.ONCE;
            if (json.has("loop")) {
                String value = json.get("loop").getAsString();
                loop = switch (value) {
                    case "true" -> Loop.LOOP;
                    case "false" -> Loop.ONCE;
                    case "hold_on_last_frame" -> Loop.HOLD;
                    default -> throw new IllegalArgumentException("Unsupported loop expression " + value);
                };
            }
            float duration = json.has("animation_length") ? number(json.get("animation_length")) * 20 : 0;
            float last = 0;
            Map<String, Track> tracks = new LinkedHashMap<>();
            for (var bone : json.getAsJsonObject("bones").entrySet()) {
                String name = bone.getKey();
                if (!BONES.contains(name)) throw new IllegalArgumentException("Unknown humanoid bone " + name);
                JsonObject channels = bone.getValue().getAsJsonObject();
                for (String key : channels.keySet()) {
                    if (!Set.of("rotation", "position", "scale").contains(key))
                        throw new IllegalArgumentException(name + ": unsupported channel " + key);
                }
                Channel rotation = channel(channels.get("rotation"));
                Channel position = channel(channels.get("position"));
                Channel scale = channel(channels.get("scale"));
                if (rotation == null && position == null && scale == null) continue;
                if (name.equals("root") && (rotation != null || scale != null))
                    throw new IllegalArgumentException("root supports position only; keep its rest rotation and scale neutral");
                if (name.endsWith("forearm") || name.endsWith("shin")) {
                    if (position != null || scale != null) throw new IllegalArgumentException(name + ": hinge supports rotation only");
                    for (Frame frame : rotation.frames) {
                        if (frame.pre[1] != 0 || frame.pre[2] != 0 || frame.post[1] != 0 || frame.post[2] != 0)
                            throw new IllegalArgumentException(name + ": hinge rotates on X only");
                    }
                }
                for (Channel c : new Channel[]{rotation, position, scale}) if (c != null) last = Math.max(last, c.endTick());
                tracks.put(name.equals("torso") ? "body" : name, new Track(rotation, position, scale));
            }
            if (json.getAsJsonObject("bones").has("body") && json.getAsJsonObject("bones").has("torso"))
                throw new IllegalArgumentException("Use body or torso, not both");
            if (!json.has("animation_length")) duration = Math.max(1, last);
            if (!Float.isFinite(duration) || duration <= 0 || duration > 1200 || last > duration + .001F)
                throw new IllegalArgumentException("Animation duration must be 0..60 seconds and contain every keyframe");
            if (tracks.isEmpty()) throw new IllegalArgumentException("No humanoid tracks in " + entry.getKey());
            result.put(entry.getKey(), new BedrockPerformanceClip(duration, loop, Map.copyOf(tracks)));
        }
        return Map.copyOf(result);
    }

    private static Channel channel(JsonElement value) {
        if (value == null) return null;
        List<Frame> frames = new ArrayList<>();
        if (!value.isJsonObject() || value.getAsJsonObject().has("pre") || value.getAsJsonObject().has("post")) {
            frames.add(frame(0, value));
        } else {
            for (var entry : value.getAsJsonObject().entrySet()) {
                float tick = Float.parseFloat(entry.getKey()) * 20;
                if (!Float.isFinite(tick) || tick < 0 || tick > 1200) throw new IllegalArgumentException("Invalid keyframe time");
                frames.add(frame(tick, entry.getValue()));
            }
        }
        if (frames.isEmpty()) throw new IllegalArgumentException("Empty animation channel");
        frames.sort(Comparator.comparingDouble(Frame::tick));
        for (int i = 1; i < frames.size(); i++) if (frames.get(i).tick == frames.get(i - 1).tick)
            throw new IllegalArgumentException("Duplicate numeric keyframe time");
        return new Channel(frames);
    }

    private static Frame frame(float tick, JsonElement value) {
        if (!value.isJsonObject()) { float[] v = vector(value); return new Frame(tick, v, v, false); }
        JsonObject json = value.getAsJsonObject();
        for (String key : json.keySet()) if (!Set.of("pre", "post", "lerp_mode").contains(key))
            throw new IllegalArgumentException("Unsupported keyframe field " + key);
        JsonElement pre = json.has("pre") ? json.get("pre") : json.get("post");
        JsonElement post = json.has("post") ? json.get("post") : pre;
        String interpolation = json.has("lerp_mode") ? json.get("lerp_mode").getAsString() : "linear";
        if (!interpolation.equals("linear") && !interpolation.equals("catmullrom"))
            throw new IllegalArgumentException("Unsupported interpolation " + interpolation);
        return new Frame(tick, vector(pre), vector(post), interpolation.equals("catmullrom"));
    }

    private static float[] vector(JsonElement value) {
        if (value == null) throw new IllegalArgumentException("Keyframe needs pre or post");
        if (!value.isJsonArray()) { float v = number(value); return new float[]{v, v, v}; }
        var array = value.getAsJsonArray();
        if (array.size() == 1) { float v = number(array.get(0)); return new float[]{v, v, v}; }
        if (array.size() != 3) throw new IllegalArgumentException("Keyframe vector needs one or three components");
        return new float[]{number(array.get(0)), number(array.get(1)), number(array.get(2))};
    }

    private static float number(JsonElement value) {
        try {
            float result = value.getAsFloat();
            if (Float.isFinite(result)) return result;
        } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException("Non-numeric animation value; bake Molang expressions in Blockbench: " + value);
    }
}
