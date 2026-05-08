package com.aetherianartificer.townstead.client.animation.emote;

import net.minecraft.resources.ResourceLocation;

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
 */
public record EmotePlayback(
        ResourceLocation emoteId,
        long startGameTime,
        ParsedEmote.LoopType loopType,
        float speedMultiplier,
        long fadingOutStartGameTime,
        float frozenTick,
        float fadeOutDurationTicks
) {
    public static EmotePlayback fresh(
            ResourceLocation emoteId,
            long startGameTime,
            ParsedEmote.LoopType loopType,
            float speedMultiplier
    ) {
        return new EmotePlayback(emoteId, startGameTime, loopType, speedMultiplier, -1L, -1F, 0F);
    }

    public boolean isFadingOut() {
        return fadingOutStartGameTime >= 0L;
    }

    public EmotePlayback startFadeOut(long fadingStart, float frozenTick, float durationTicks) {
        return new EmotePlayback(
                emoteId, startGameTime, loopType, speedMultiplier, fadingStart, frozenTick, durationTicks);
    }

    public EmotePlayback withFrozenTick(float frozenTick) {
        return new EmotePlayback(
                emoteId, startGameTime, loopType, speedMultiplier,
                fadingOutStartGameTime, frozenTick, fadeOutDurationTicks);
    }
}
