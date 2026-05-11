package com.aetherianartificer.townstead.client.animation.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

/**
 * Applies the active emote's "body" bone translation and rotation at the entity
 * render level, mirroring Emotecraft's {@code PlayerRendererMixin.applyBodyTransforms}.
 *
 * <p>Emotecraft's keyframe values for the body bone are interpreted in <b>block
 * units</b> at this layer, not model units. A value like {@code torso.y = 0.76}
 * becomes a 0.76-block jump (~3/4 of a block), which is the visible "lift" the
 * player sees during emotes like "Over here". The model-part-level translation
 * we apply elsewhere only adds a 0.76 / 16 ≈ 0.05-block intra-model shift, which
 * is invisible without the matrix-level translation here.</p>
 *
 * <p>The {@code +0.7 / -0.7} pivot offset matches Emotecraft's approach: lift to
 * roughly the entity's center, rotate around that, then bring the matrix back so
 * subsequent rendering proceeds from the entity's feet.</p>
 */
public final class EmoteBodyTransformSampler {
    private static final double PIVOT_HEIGHT = 0.7;

    private EmoteBodyTransformSampler() {}

    public static void apply(LivingEntity entity, PoseStack poseStack, float partialTick) {
        UUID uuid = entity.getUUID();
        EmotePlayback playback = EmotePlaybackRegistry.get(uuid);
        if (playback == null) return;

        ParsedEmote emote = EmoteRegistry.get(playback.emoteId()).orElse(null);
        if (emote == null) return;

        ParsedBoneAnimation bodyBone = emote.bones().get("torso");
        if (bodyBone == null) bodyBone = emote.bones().get("body");
        if (bodyBone == null) return;

        long now = entity.level().getGameTime();
        float elapsed = ((now - playback.startGameTime()) + partialTick) * playback.speedMultiplier();
        float t = resolveTime(elapsed, emote, playback.loopType());
        if (t < 0F && playback.frozenTick() < 0F) return;
        if (t < 0F) t = playback.frozenTick();

        float blend = blend(playback, now, partialTick, elapsed);
        if (blend <= 0F) return;

        float xPos = bodyBone.translationKeyed()
                ? sample(bodyBone.xPos(), bodyBone.xPosDefault(), t) * blend
                : 0F;
        float yPos = bodyBone.translationKeyed()
                ? sample(bodyBone.yPos(), bodyBone.yPosDefault(), t) * blend
                : 0F;
        float zPos = bodyBone.translationKeyed()
                ? sample(bodyBone.zPos(), bodyBone.zPosDefault(), t) * blend
                : 0F;

        float xRot = sample(bodyBone.xRot(), bodyBone.xRotDefault(), t) * blend;
        float yRot = sample(bodyBone.yRot(), bodyBone.yRotDefault(), t) * blend;
        float zRot = sample(bodyBone.zRot(), bodyBone.zRotDefault(), t) * blend;

        boolean hasTrans = xPos != 0F || yPos != 0F || zPos != 0F;
        boolean hasRot = xRot != 0F || yRot != 0F || zRot != 0F;
        if (!hasTrans && !hasRot) return;

        poseStack.translate(xPos, yPos + PIVOT_HEIGHT, zPos);
        if (hasRot) {
            poseStack.mulPose(Axis.ZP.rotation(zRot));
            poseStack.mulPose(Axis.YP.rotation(yRot));
            poseStack.mulPose(Axis.XP.rotation(xRot));
        }
        poseStack.translate(0, -PIVOT_HEIGHT, 0);
    }

    private static float blend(EmotePlayback playback, long now, float partialTick, float elapsedTicks) {
        if (playback.isFadingOut()) {
            float duration = playback.fadeOutDurationTicks() > 0F ? playback.fadeOutDurationTicks() : 6.0F;
            float fadeElapsed = (now - playback.fadingOutStartGameTime()) + partialTick;
            if (fadeElapsed <= 0F) return 1F;
            if (fadeElapsed >= duration) return 0F;
            return 1F - (fadeElapsed / duration);
        }
        return Math.min(1F, Math.max(0F, elapsedTicks / 4.0F));
    }

    private static float resolveTime(float elapsed, ParsedEmote emote, ParsedEmote.LoopType loopType) {
        int stop = Math.max(emote.stopTick(), Math.max(emote.endTick(), 1));
        if (loopType == ParsedEmote.LoopType.LOOP) {
            int loopStart = Math.max(0, emote.returnToTick());
            int loopSpan = Math.max(1, stop - loopStart);
            if (elapsed < 0F) return 0F;
            if (elapsed <= stop) return elapsed;
            float past = elapsed - stop;
            return loopStart + (past % loopSpan);
        }
        if (elapsed < 0F) return 0F;
        if (elapsed > stop) return -1F;
        return elapsed;
    }

    private static float sample(List<ParsedKeyframe> keyframes, float restValue, float tick) {
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
                // Match Emotecraft's legacy isEasingBefore=false semantics: the
                // curve through the span is governed by the previous keyframe's
                // easing (e.g. CONSTANT -> hold until the end, then snap).
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
