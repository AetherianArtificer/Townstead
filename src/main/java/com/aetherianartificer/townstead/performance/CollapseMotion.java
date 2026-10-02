package com.aetherianartificer.townstead.performance;

import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceClip;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Shared numeric motion only; safe on a dedicated server (no client/render classes). */
public final class CollapseMotion {
    public static final String CLIP = "townstead_performance:fatigue_pass_out";
    /** A calm lie-down into the same held pose, for a villager sleeping where no bed is free. */
    public static final String LIE_DOWN_CLIP = "townstead_performance:fatigue_lie_down";
    public static final String CHANNEL = "fatigue_collapse";
    public static final int DURATION = 104;
    private CollapseMotion() {}

    /** Whether {@code clip} ends lying on the ground; such a clip owns the pose while it plays. */
    public static boolean isGround(String clip) {
        return CLIP.equals(clip) || LIE_DOWN_CLIP.equals(clip);
    }

    public static int duration(String clip) {
        return LIE_DOWN_CLIP.equals(clip) ? (int) Bundled.ALL.get(clip).durationTicks() : DURATION;
    }

    // The server owns this bundled trajectory. Client resource packs can change visual
    // limb poses, but cannot make an entity move farther or bypass collisions.
    private static final class Bundled {
        static final java.util.Map<String, BedrockPerformanceClip> ALL = load();
        private static java.util.Map<String, BedrockPerformanceClip> load() {
            try (var stream = CollapseMotion.class.getResourceAsStream(
                    "/assets/townstead_performance/animations/townstead/fatigue.animation.json")) {
                if (stream == null) throw new IllegalStateException("Missing fatigue animation export");
                var clips = BedrockPerformanceClip.parse(JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                return java.util.Map.of(CLIP, clips.get("animation.fatigue_pass_out"),
                        LIE_DOWN_CLIP, clips.get("animation.fatigue_lie_down"));
            } catch (Exception e) { throw new IllegalStateException("Invalid bundled collapse motion", e); }
        }
    }

    /** Convert exported Euler rotations into the renderer's pre-model-flip coordinates. */
    public static Quaternionf rotation(float[] degrees) {
        float unit = (float) (Math.PI / 180);
        return new Quaternionf().rotationZYX(degrees[2] * unit, -degrees[1] * unit, -degrees[0] * unit);
    }

    /** Horizontal shift of the body's midpoint as it tips around the authored foot pivot. */
    public static Vec3 anchor(float[] rotation) {
        Vector3f center = new Vector3f(0, 1, 0).rotate(rotation(rotation));
        return new Vec3(center.x, 0, center.z);
    }

    public static Vec3 position(float tick) {
        return position(CLIP, tick);
    }

    public static Vec3 position(String clip, float tick) {
        var root = Bundled.ALL.get(clip).bones().get("root");
        float sample = Math.max(0, Math.min(tick, duration(clip)));
        float[] p = root.position().sample(sample);
        return new Vec3(-p[0] / 16D, 0, p[2] / 16D).add(anchor(root.rotation().sample(sample)));
    }

    public static Vec3 step(float previous, float current, float yaw) {
        return step(CLIP, previous, current, yaw);
    }

    public static Vec3 step(String clip, float previous, float current, float yaw) {
        return position(clip, current).subtract(position(clip, previous)).yRot((float) Math.toRadians(180 - yaw));
    }
}
