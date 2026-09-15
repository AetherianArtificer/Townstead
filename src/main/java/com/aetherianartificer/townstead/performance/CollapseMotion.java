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
    public static final String CHANNEL = "fatigue_collapse";
    public static final int DURATION = 104;
    private CollapseMotion() {}

    // The server owns this bundled trajectory. Client resource packs can change visual
    // limb poses, but cannot make an entity move farther or bypass collisions.
    private static final class Bundled {
        static final BedrockPerformanceClip CLIP = load();
        private static BedrockPerformanceClip load() {
            try (var stream = CollapseMotion.class.getResourceAsStream(
                    "/assets/townstead_performance/animations/townstead/fatigue.animation.json")) {
                if (stream == null) throw new IllegalStateException("Missing fatigue animation export");
                return BedrockPerformanceClip.parse(JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject())
                        .get("animation.fatigue_pass_out");
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
        var root = Bundled.CLIP.bones().get("root");
        float sample = Math.max(0, Math.min(tick, DURATION));
        float[] p = root.position().sample(sample);
        return new Vec3(-p[0] / 16D, 0, p[2] / 16D).add(anchor(root.rotation().sample(sample)));
    }

    public static Vec3 step(float previous, float current, float yaw) {
        return position(current).subtract(position(previous)).yRot((float) Math.toRadians(180 - yaw));
    }
}
