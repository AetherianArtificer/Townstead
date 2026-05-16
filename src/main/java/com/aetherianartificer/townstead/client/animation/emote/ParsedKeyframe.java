package com.aetherianartificer.townstead.client.animation.emote;

/**
 * A single keyframe in a parsed channel. Between two consecutive keyframes the
 * channel eases from {@code prev.value} to {@code next.value} using {@code
 * next.easing}; before the first keyframe, eases from the channel's
 * {@link ParsedBoneAnimation} default value into {@code first.value} using
 * {@code first.easing}.
 *
 * <p>{@code easingArg} is the optional parameter Emotecraft / playerAnim
 * supports for parameterized eases (Back overshoot, Elastic period, Bounce
 * gravity). Almost no published emote sets it; null means "use Penner default
 * constants," which is what every {@link EmoteEasing} member currently does.
 * Preserved on the keyframe so a future parameterized-curve variant can read
 * it without re-plumbing the loader.</p>
 */
public record ParsedKeyframe(int tick, float value, EmoteEasing easing, Float easingArg) {
    public ParsedKeyframe(int tick, float value, EmoteEasing easing) {
        this(tick, value, easing, null);
    }
}
