package com.aetherianartificer.townstead.client.animation.emote;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * One active emote playback for a single entity. Stored in the {@link
 * EmotePlaybackRegistry} keyed by entity UUID.
 *
 * <p>{@code startGameTime} is the {@code level().getGameTime()} captured when the
 * trigger arrived. The fade fields drive a smooth blend toward/away from CEM's
 * steady-state pose at the start and end of the playback:</p>
 * <ul>
 *   <li>{@code fadingOutStartGameTime &lt; 0} → playing normally; fade-in is implicit
 *       from {@code startGameTime}.</li>
 *   <li>{@code fadingOutStartGameTime &gt;= 0} → fade-out in progress; the adapter
 *       holds the emote pose at {@code frozenTick} and ramps blend down to zero.</li>
 *   <li>{@code frozenTick &lt; 0} → the adapter hasn't captured the freeze point yet
 *       and will compute it on the first fade-out frame.</li>
 * </ul>
 *
 * <p>{@code mobile} signals the source adapter to skip its movement-cancel
 * detection so the emote plays through walking. {@code skippedBones} is
 * the set of bone names whose transforms should NOT be applied to the
 * model, letting vanilla animation show through underneath (typically
 * legs and torso for arm-only reactions like wave).</p>
 */
public record EmotePlayback(
        ResourceLocation emoteId,
        long startGameTime,
        ParsedEmote.LoopType loopType,
        float speedMultiplier,
        long fadingOutStartGameTime,
        float frozenTick,
        float fadeOutDurationTicks,
        boolean mobile,
        Set<String> skippedBones
) {
    public static EmotePlayback fresh(
            ResourceLocation emoteId,
            long startGameTime,
            ParsedEmote.LoopType loopType,
            float speedMultiplier
    ) {
        return new EmotePlayback(emoteId, startGameTime, loopType, speedMultiplier, -1L, -1F, 0F,
                false, Set.of());
    }

    public static EmotePlayback fresh(
            ResourceLocation emoteId,
            long startGameTime,
            ParsedEmote.LoopType loopType,
            float speedMultiplier,
            boolean mobile,
            Set<String> skippedBones
    ) {
        return new EmotePlayback(emoteId, startGameTime, loopType, speedMultiplier, -1L, -1F, 0F,
                mobile, skippedBones == null ? Set.of() : skippedBones);
    }

    public boolean isFadingOut() {
        return fadingOutStartGameTime >= 0L;
    }

    public EmotePlayback startFadeOut(long fadingStart, float frozenTick, float durationTicks) {
        return new EmotePlayback(
                emoteId, startGameTime, loopType, speedMultiplier, fadingStart, frozenTick, durationTicks,
                mobile, skippedBones);
    }

    public EmotePlayback withFrozenTick(float frozenTick) {
        return new EmotePlayback(
                emoteId, startGameTime, loopType, speedMultiplier,
                fadingOutStartGameTime, frozenTick, fadeOutDurationTicks,
                mobile, skippedBones);
    }
}
