package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.aetherianartificer.townstead.client.animation.AnimationTransform;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Retargets the flat humanoid authoring rig into MCA's model coordinates. */
public final class BedrockPerformanceSampler {
    private BedrockPerformanceSampler() {}

    public static List<AnimationTransform> sample(BedrockPerformanceClip clip, float elapsed,
                                                 float remaining, AnimationTargetMap<?> targets) {
        return sample(clip, elapsed, remaining, targets, 1F);
    }

    public static List<AnimationTransform> sample(BedrockPerformanceClip clip, float elapsed,
                                                 float remaining, AnimationTargetMap<?> targets,
                                                 float lowerBodyWeight) {
        float time = time(clip, elapsed);
        float clipBlend = blend(clip, elapsed, remaining);
        if (clipBlend <= 0) return List.of();
        List<AnimationTransform> out = new ArrayList<>();
        for (var entry : clip.bones().entrySet()) {
            String name = entry.getKey();
            if (name.equals("root")) continue; // Applied once to the entity render matrix, never individual parts.
            boolean hinge = name.endsWith("forearm") || name.endsWith("shin");
            String target = name.replace("forearm", "arm").replace("shin", "leg");
            float blend = clipBlend * (target.endsWith("_leg") ? Mth.clamp(lowerBodyWeight, 0F, 1F) : 1F);
            if (blend <= 0) continue; // Leave every leg channel, including knee bend, to the gait.
            ModelPart part = targets.resolve(target).orElse(null);
            if (part == null) continue;
            var track = entry.getValue();
            float[] r = track.rotation() == null ? null : track.rotation().sample(time);
            if (hinge) {
                out.add(new AnimationTransform(target, null, null, null, null, null, null,
                        null, null, null, radians(r[0]) * blend, 0F, false, false, true, AnimationTransform.Operation.SET));
                continue;
            }
            float[] p = track.position() == null ? null : track.position().sample(time);
            float[] s = track.scale() == null ? null : track.scale().sample(time);
            out.add(new AnimationTransform(target,
                    p == null ? null : part.x + p[0] * blend,
                    p == null ? null : part.y - p[1] * blend,
                    p == null ? null : part.z + p[2] * blend,
                    r == null ? null : Mth.lerp(blend, part.xRot, radians(r[0])),
                    // Blockbench already exports display rotations as (-x,-y,z). The
                    // authored rig maps into MCA by flipping X/Y positions, so these
                    // exported Euler angles already have the ModelPart rotation signs.
                    r == null ? null : Mth.lerp(blend, part.yRot, radians(r[1])),
                    r == null ? null : Mth.lerp(blend, part.zRot, radians(r[2])),
                    s == null ? null : Mth.lerp(blend, part.xScale, s[0]),
                    s == null ? null : Mth.lerp(blend, part.yScale, s[1]),
                    s == null ? null : Mth.lerp(blend, part.zScale, s[2]),
                    null, null, p != null, s != null, false, AnimationTransform.Operation.SET));
        }
        return out;
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }

    private static float time(BedrockPerformanceClip clip, float elapsed) {
        return clip.loop() == BedrockPerformanceClip.Loop.LOOP
                ? elapsed % clip.durationTicks() : Math.min(elapsed, clip.durationTicks());
    }

    static float blend(BedrockPerformanceClip clip, float elapsed, float remaining) {
        float tail = clip.loop() == BedrockPerformanceClip.Loop.ONCE
                ? Math.min(remaining, clip.durationTicks() - elapsed) : remaining;
        return Mth.clamp(Math.min(elapsed / 4F, tail / 6F), 0, 1);
    }

    /** Translation in blocks, before the renderer's model-space X/Y flip. Mounted actors stay seated. */
    public static float[] rootTranslation(BedrockPerformanceClip clip, float elapsed, float remaining, boolean mounted) {
        var root = clip.bones().get("root");
        float weight = blend(clip, elapsed, remaining);
        if (mounted || root == null || root.position() == null || weight <= 0) return new float[3];
        float[] p = root.position().sample(time(clip, elapsed));
        return new float[]{-p[0] * weight / 16F, p[1] * weight / 16F, p[2] * weight / 16F};
    }
}
