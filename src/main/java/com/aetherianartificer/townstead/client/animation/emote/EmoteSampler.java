package com.aetherianartificer.townstead.client.animation.emote;

import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.aetherianartificer.townstead.client.animation.AnimationTransform;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
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
            boolean hasScale, float xScale, float yScale, float zScale,
            boolean hasBend, float bend, float bendDirection
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

        boolean bendKeyed = bone.bendKeyed();
        float bendVal = 0F, bendDirVal = 0F;
        if (bendKeyed) {
            bendVal = sampleRot(bone.bend(), bone.bendDefault(), tick);
            bendDirVal = sampleRot(bone.bendDirection(), bone.bendDirectionDefault(), tick);

            if (!EmoteReflection.isBendylibAvailable()) {
                float fallbackAxis = -bendDirVal;
                xRot += bendVal * Mth.cos(fallbackAxis);
                zRot += bendVal * Mth.sin(fallbackAxis);
                bendKeyed = false;
            }
        }

        return new BonePose(xRot, yRot, zRot, translation, xPos, yPos, zPos,
                scale, xS, yS, zS, bendKeyed, bendVal, bendDirVal);
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

        Float bendVal = null, bendDirVal = null;
        if (pose.hasBend) {
            // Bend has no "current part value" to lerp from — the vanilla
            // model carries no bend state. Scale by blend from zero so we
            // ramp in cleanly during the fade-in window.
            bendVal = pose.bend * blend;
            bendDirVal = pose.bendDirection;
        }

        return new AnimationTransform(
                target, xPos, yPos, zPos,
                xRot, yRot, zRot,
                xS, yS, zS,
                bendVal, bendDirVal,
                pose.hasTranslation, pose.hasScale, pose.hasBend,
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
                null, null,
                pose.hasTranslation, false, false,
                AnimationTransform.Operation.ADD);
    }

    private static float sampleRot(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        float v = sampleRaw(keyframes, restValue, tick);
        return Float.isFinite(v) ? v : 0F;
    }

    private static float sampleTrans(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        float v = sampleRaw(keyframes, restValue, tick);
        return Float.isFinite(v) ? v : 0F;
    }

    private static float sampleScale(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        float v = sampleRaw(keyframes, restValue, tick);
        return Float.isFinite(v) ? v : restValue;
    }

    private static float sampleRaw(List<ParsedKeyframe> keyframes, float restValue, float tick) {
        if (keyframes == null || keyframes.isEmpty()) return restValue;

        ParsedKeyframe first = keyframes.get(0);
        if (tick <= 0F) return restValue;
        if (tick <= first.tick()) {
            // Interpolating from the synthesized rest keyframe at tick 0 to the
            // first authored keyframe — use the first authored keyframe's easing
            // (the curve "into" it).
            return easeBetween(restValue, first.value(), 0F, first.tick(), tick, first.easing());
        }

        for (int i = 1; i < keyframes.size(); i++) {
            ParsedKeyframe prev = keyframes.get(i - 1);
            ParsedKeyframe next = keyframes.get(i);
            if (tick <= next.tick()) {
                // Use the PREVIOUS keyframe's easing — Emotecraft's legacy default
                // (isEasingBefore = false). This matters for snap-back keyframes
                // like backflip's tick-70 CONSTANT -> tick-71 0: with prev's
                // CONSTANT easing the value holds at the start until the end of
                // the span, then jumps; with the next keyframe's LINEAR easing
                // (what we used to do) the value lerps through 180° mid-tick and
                // the entity visibly flashes upside-down at the end of the flip.
                return easeBetween(prev.value(), next.value(), prev.tick(), next.tick(), tick, prev.easing());
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
