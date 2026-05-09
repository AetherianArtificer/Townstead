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
 * list of {@link AnimationTransform}s.
 *
 * <p>Each emote bone is mapped through {@link EmoteBoneMapping} into a {@link
 * EmoteBoneMapping.Targets#primary primary} part — receiving a SET that lerps from
 * the part's current value (whatever CEM put there) toward the emote-sampled value
 * by {@code blend} — and zero or more {@link EmoteBoneMapping.Targets#propagateTo
 * propagation} parts that receive an ADD scaled by {@code blend}, simulating the
 * parent-child relationships ({@code torso} parents head/arms, {@code body}
 * parents everything) that vanilla {@code HumanoidModel}'s flat sibling layout
 * doesn't otherwise express.</p>
 *
 * <p>The output is ordered: all SETs first, then all ADDs, so an ADD propagated
 * onto a child that has its own SET keyframe lands on top of the SET rather than
 * being overwritten by it.</p>
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

        List<AnimationTransform> setOps = new ArrayList<>();
        List<AnimationTransform> addOps = new ArrayList<>();

        for (Map.Entry<String, ParsedBoneAnimation> entry : emote.bones().entrySet()) {
            String boneName = entry.getKey();
            ParsedBoneAnimation bone = entry.getValue();
            if (!bone.hasAnyKeyframes()) continue;

            EmoteBoneMapping.Targets mapping = EmoteBoneMapping.mapTargets(boneName);
            if (mapping.isEmpty()) continue;

            BonePose pose = samplePose(bone, tick);

            if (mapping.primary() != null) {
                ModelPart part = targets.resolve(mapping.primary()).orElse(null);
                if (part != null) setOps.add(buildSetTransform(mapping.primary(), pose, part, clampedBlend));
            }

            for (String childTarget : mapping.propagateTo()) {
                ModelPart part = targets.resolve(childTarget).orElse(null);
                if (part == null) continue;
                addOps.add(buildAddTransform(childTarget, pose, clampedBlend));
            }
        }

        if (addOps.isEmpty()) return setOps;
        List<AnimationTransform> combined = new ArrayList<>(setOps.size() + addOps.size());
        combined.addAll(setOps);
        combined.addAll(addOps);
        return combined;
    }

    private record BonePose(
            float xRot, float yRot, float zRot,
            boolean hasTranslation, float xPos, float yPos, float zPos,
            boolean hasScale, float xScale, float yScale, float zScale
    ) {}

    private static BonePose samplePose(ParsedBoneAnimation bone, float tick) {
        float xRot = sampleRot(bone.xRot(), bone.xRotDefault(), tick);
        float yRot = sampleRot(bone.yRot(), bone.yRotDefault(), tick);
        float zRot = sampleRot(bone.zRot(), bone.zRotDefault(), tick);

        boolean translation = bone.translationKeyed();
        float xPos = 0F, yPos = 0F, zPos = 0F;
        if (translation) {
            xPos = sampleTrans(bone.xPos(), bone.xPosDefault(), tick);
            yPos = sampleTrans(bone.yPos(), bone.yPosDefault(), tick);
            zPos = sampleTrans(bone.zPos(), bone.zPosDefault(), tick);
        }

        boolean scale = bone.scaleKeyed();
        float xS = 1F, yS = 1F, zS = 1F;
        if (scale) {
            xS = sampleScale(bone.xScale(), bone.xScaleDefault(), tick);
            yS = sampleScale(bone.yScale(), bone.yScaleDefault(), tick);
            zS = sampleScale(bone.zScale(), bone.zScaleDefault(), tick);
        }

        return new BonePose(xRot, yRot, zRot, translation, xPos, yPos, zPos, scale, xS, yS, zS);
    }

    private static AnimationTransform buildSetTransform(String target, BonePose pose, ModelPart part, float blend) {
        float xRot = Mth.lerp(blend, part.xRot, pose.xRot);
        float yRot = Mth.lerp(blend, part.yRot, pose.yRot);
        float zRot = Mth.lerp(blend, part.zRot, pose.zRot);

        Float xPos = null, yPos = null, zPos = null;
        if (pose.hasTranslation) {
            xPos = Mth.lerp(blend, part.x, pose.xPos);
            yPos = Mth.lerp(blend, part.y, pose.yPos);
            zPos = Mth.lerp(blend, part.z, pose.zPos);
        }

        Float xS = null, yS = null, zS = null;
        if (pose.hasScale) {
            xS = Mth.lerp(blend, part.xScale, pose.xScale);
            yS = Mth.lerp(blend, part.yScale, pose.yScale);
            zS = Mth.lerp(blend, part.zScale, pose.zScale);
        }

        return new AnimationTransform(
                target, xPos, yPos, zPos,
                xRot, yRot, zRot,
                xS, yS, zS,
                pose.hasTranslation, pose.hasScale,
                AnimationTransform.Operation.SET);
    }

    /**
     * ADD with the bone's emote-space delta scaled by {@code blend}. Used for
     * propagating a parent bone's transform onto its conceptual children. For
     * scale, ADD on top of the rest value of {@code 1} would yield {@code 1 +
     * deltaScale}, which is wrong; we don't propagate scale at all, only rotation
     * and translation.
     */
    private static AnimationTransform buildAddTransform(String target, BonePose pose, float blend) {
        return new AnimationTransform(
                target,
                pose.hasTranslation ? pose.xPos * blend : null,
                pose.hasTranslation ? pose.yPos * blend : null,
                pose.hasTranslation ? pose.zPos * blend : null,
                pose.xRot * blend, pose.yRot * blend, pose.zRot * blend,
                null, null, null,
                pose.hasTranslation, false,
                AnimationTransform.Operation.ADD);
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
