package com.aetherianartificer.townstead.client.animation.emote;

import com.aetherianartificer.townstead.client.animation.AnimationSourceAdapter;
import com.aetherianartificer.townstead.client.animation.AnimationSourceContext;
import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.aetherianartificer.townstead.client.animation.AnimationTransform;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;

import java.util.List;
import java.util.UUID;

/**
 * Animation source for Emotecraft-driven, AI- or player-triggered emotes.
 *
 * <p>Per frame: looks up the entity's active {@link EmotePlayback} (or returns
 * empty), samples the corresponding {@link ParsedEmote} at the current tick using
 * {@link EmoteSampler}, and emits {@link AnimationTransform.Operation#SET}
 * transforms only for bones the emote actually keyframes. Bones the emote doesn't
 * keyframe are not emitted, so CEM's earlier SET stays in effect — "emote owns
 * the limbs it animates, CEM keeps the rest."</p>
 *
 * <p>Fade-in and fade-out blend smoothly toward / away from CEM's pose so a
 * starting or stopping emote doesn't snap.</p>
 */
public final class EmotecraftAnimationSourceAdapter implements AnimationSourceAdapter {
    private static final String ID = "emotes";
    private static final float FADE_IN_TICKS = 4.0F;
    private static final float FADE_OUT_TICKS = 6.0F;
    private static final float MOVEMENT_FADE_OUT_TICKS = 2.0F;
    private static final float MOVEMENT_LIMB_DISTANCE_THRESHOLD = 0.1F;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return EmoteReflection.isAvailable();
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        UUID uuid = context.entity().getUUID();
        EmotePlayback playback = EmotePlaybackRegistry.get(uuid);
        if (playback == null) return List.of();

        ParsedEmote emote = EmoteRegistry.get(playback.emoteId()).orElse(null);
        if (emote == null) {
            EmotePlaybackRegistry.remove(uuid);
            return List.of();
        }

        long now = context.entity().level().getGameTime();
        float partialTick = context.animationProgress() - context.entity().tickCount;
        AnimationTargetMap<?> targets = AnimationTargetMap.forMcaModel(context.model());

        if (!playback.isFadingOut() && context.limbDistance() > MOVEMENT_LIMB_DISTANCE_THRESHOLD) {
            float elapsedTicks = (now - playback.startGameTime()) * playback.speedMultiplier();
            if (elapsedTicks > FADE_IN_TICKS) {
                // Player/villager started moving — quick fade-out so the legs
                // unstick instead of drifting frozen. Gated on full fade-in so
                // residual limb-swing from before the emote started doesn't
                // trip the cancel.
                playback = startFadeOutFrom(uuid, emote, playback, now, MOVEMENT_FADE_OUT_TICKS);
            }
        }

        if (playback.isFadingOut()) {
            return collectFadingOut(uuid, emote, playback, now, partialTick, targets);
        }
        return collectActive(uuid, emote, playback, now, partialTick, targets);
    }

    private static EmotePlayback startFadeOutFrom(
            UUID uuid,
            ParsedEmote emote,
            EmotePlayback playback,
            long now,
            float durationTicks
    ) {
        float elapsedAtFadeStart =
                (now - playback.startGameTime()) * playback.speedMultiplier();
        float frozen = resolveTime(elapsedAtFadeStart, emote, playback.loopType());
        if (frozen < 0F) frozen = Math.max(emote.endTick(), 0);
        EmotePlayback fading = playback.startFadeOut(now, frozen, durationTicks);
        EmotePlaybackRegistry.put(uuid, fading);
        return fading;
    }

    private List<AnimationTransform> collectActive(
            UUID uuid,
            ParsedEmote emote,
            EmotePlayback playback,
            long now,
            float partialTick,
            AnimationTargetMap<?> targets
    ) {
        float elapsed = ((now - playback.startGameTime()) + partialTick) * playback.speedMultiplier();
        float t = resolveTime(elapsed, emote, playback.loopType());
        if (t < 0F) {
            // PLAY_ONCE complete — start a fade-out from the held end pose.
            float frozen = Math.max(emote.endTick(), 0);
            EmotePlayback fading = playback.startFadeOut(now, frozen, FADE_OUT_TICKS);
            EmotePlaybackRegistry.put(uuid, fading);
            return EmoteSampler.sample(emote, frozen, fadeOutBlend(now, partialTick, fading), targets);
        }

        float blend = Math.min(1F, Math.max(0F, elapsed / FADE_IN_TICKS));
        return EmoteSampler.sample(emote, t, blend, targets);
    }

    private List<AnimationTransform> collectFadingOut(
            UUID uuid,
            ParsedEmote emote,
            EmotePlayback playback,
            long now,
            float partialTick,
            AnimationTargetMap<?> targets
    ) {
        if (playback.frozenTick() < 0F) {
            // Capture the freeze point on the first fade-out frame.
            float elapsedAtFadeStart =
                    (playback.fadingOutStartGameTime() - playback.startGameTime()) * playback.speedMultiplier();
            float frozen = resolveTime(elapsedAtFadeStart, emote, playback.loopType());
            if (frozen < 0F) frozen = Math.max(emote.endTick(), 0);
            playback = playback.withFrozenTick(frozen);
            EmotePlaybackRegistry.put(uuid, playback);
        }

        float blend = fadeOutBlend(now, partialTick, playback);
        if (blend <= 0F) {
            EmotePlaybackRegistry.remove(uuid);
            return List.of();
        }
        return EmoteSampler.sample(emote, playback.frozenTick(), blend, targets);
    }

    private static float fadeOutBlend(long now, float partialTick, EmotePlayback playback) {
        float duration = playback.fadeOutDurationTicks() > 0F
                ? playback.fadeOutDurationTicks()
                : FADE_OUT_TICKS;
        float fadeElapsed = (now - playback.fadingOutStartGameTime()) + partialTick;
        if (fadeElapsed <= 0F) return 1F;
        if (fadeElapsed >= duration) return 0F;
        return 1F - (fadeElapsed / duration);
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
        if (elapsed > stop) return -1F; // PLAY_ONCE complete; signal a transition to fade-out
        return elapsed;
    }

    public void invalidate() {
        // Reserved for symmetry with EmfAnimationSourceAdapter; no per-adapter cache today.
    }
}
