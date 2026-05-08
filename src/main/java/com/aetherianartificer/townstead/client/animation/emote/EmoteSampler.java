package com.aetherianartificer.townstead.client.animation.emote;

import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.aetherianartificer.townstead.client.animation.AnimationTransform;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateless evaluator: turns a {@link ParsedEmote} sampled at tick {@code t} into a
 * list of {@link AnimationTransform}s using {@link AnimationTransform.Operation#SET}.
 *
 * <p>SET semantics matter: the emote source runs after CEM/EMF in {@code
 * McaAnimationBridge.SOURCES}, so any bone the emote keyframes is fully owned by
 * the emote that frame; bones the emote doesn't reference get no transform emitted
 * and keep CEM's earlier SET. Defensive clamps protect against malformed emotes
 * from snapping the rig.</p>
 *
 * <p>The {@code blend} parameter linearly interpolates from the {@link ModelPart}'s
 * current value (set by an earlier source like CEM) toward the emote-sampled value
 * — {@code 1.0} = full emote, {@code 0.0} = no change. The adapter uses this for
 * fade-in at start of playback and fade-out at end / on cancel, so the bone
 * doesn't snap.</p>
 */
public final class EmoteSampler {
    private static final float TRANSLATION_CLAMP = 24.0F;
    private static final float ROTATION_CLAMP = (float) (Math.PI * 0.75);

    private EmoteSampler() {}

    public static List<AnimationTransform> sample(
            ParsedEmote emote,
            float tick,
            float blend,
            AnimationTargetMap<?> targets
    ) {
        if (blend <= 0F) return List.of();
        float clampedBlend = blend >= 1F ? 1F : blend;

        List<AnimationTransform> out = new ArrayList<>();
        for (Map.Entry<String, ParsedBoneAnimation> entry : emote.bones().entrySet()) {
            String target = entry.getKey();
            ParsedBoneAnimation bone = entry.getValue();
            if (!bone.hasAnyKeyframes()) continue;

            ModelPart part = targets.resolve(target).orElse(null);
            if (part == null) continue;

            float emoteXRot = sampleRot(bone.xRot(), bone.xRotDefault(), tick);
            float emoteYRot = sampleRot(bone.yRot(), bone.yRotDefault(), tick);
            float emoteZRot = sampleRot(bone.zRot(), bone.zRotDefault(), tick);
            float xRot = Mth.lerp(clampedBlend, part.xRot, emoteXRot);
            float yRot = Mth.lerp(clampedBlend, part.yRot, emoteYRot);
            float zRot = Mth.lerp(clampedBlend, part.zRot, emoteZRot);

            boolean translation = bone.translationKeyed();
            float xPos = 0F, yPos = 0F, zPos = 0F;
            if (translation) {
                float ex = sampleTrans(bone.xPos(), bone.xPosDefault(), tick);
                float ey = sampleTrans(bone.yPos(), bone.yPosDefault(), tick);
                float ez = sampleTrans(bone.zPos(), bone.zPosDefault(), tick);
                xPos = Mth.lerp(clampedBlend, part.x, ex);
                yPos = Mth.lerp(clampedBlend, part.y, ey);
                zPos = Mth.lerp(clampedBlend, part.z, ez);
            }

            boolean scale = bone.scaleKeyed();
            float xS = 1F, yS = 1F, zS = 1F;
            if (scale) {
                float ex = sampleScale(bone.xScale(), bone.xScaleDefault(), tick);
                float ey = sampleScale(bone.yScale(), bone.yScaleDefault(), tick);
                float ez = sampleScale(bone.zScale(), bone.zScaleDefault(), tick);
                xS = Mth.lerp(clampedBlend, part.xScale, ex);
                yS = Mth.lerp(clampedBlend, part.yScale, ey);
                zS = Mth.lerp(clampedBlend, part.zScale, ez);
            }

            out.add(new AnimationTransform(
                    target,
                    translation ? xPos : null,
                    translation ? yPos : null,
                    translation ? zPos : null,
                    xRot, yRot, zRot,
                    scale ? xS : null,
                    scale ? yS : null,
                    scale ? zS : null,
                    translation,
                    scale,
                    AnimationTransform.Operation.SET
            ));
        }
        return out;
    }

    private static float sampleRot(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        return clamp(sampleRaw(keyframes, restValue, tick), ROTATION_CLAMP);
    }

    private static float sampleTrans(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        return clamp(sampleRaw(keyframes, restValue, tick), TRANSLATION_CLAMP);
    }

    private static float sampleScale(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        float v = sampleRaw(keyframes, restValue, tick);
        if (!Float.isFinite(v)) return restValue;
        if (v < 0.01F) return 0.01F;
        if (v > 16F) return 16F;
        return v;
    }

    private static float clamp(float v, float bound) {
        if (!Float.isFinite(v)) return 0F;
        if (v > bound) return bound;
        if (v < -bound) return -bound;
        return v;
    }

    private static float sampleRaw(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        if (keyframes == null || keyframes.isEmpty()) return restValue;

        ParsedKeyframe first = keyframes.get(0);
        if (tick <= 0F) return restValue;
        if (tick <= first.tick()) {
            return easeBetween(restValue, first.value(), 0F, first.tick(), tick, first.easing());
        }

        for (int i = 1; i < keyframes.size(); i++) {
            ParsedKeyframe prev = keyframes.get(i - 1);
            ParsedKeyframe next = keyframes.get(i);
            if (tick <= next.tick()) {
                return easeBetween(prev.value(), next.value(), prev.tick(), next.tick(), tick, next.easing());
            }
        }
        return keyframes.get(keyframes.size() - 1).value();
    }

    private static float easeBetween(float a, float b, float startTick, float endTick, float tick, EmoteEasing easing) {
        float span = endTick - startTick;
        if (span <= 0F) return b;
        float alpha = Mth.clamp((tick - startTick) / span, 0F, 1F);
        return Mth.lerp(easing.apply(alpha), a, b);
    }
}
