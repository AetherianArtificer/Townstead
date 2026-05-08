package com.aetherianartificer.townstead.client.animation.emote;

/**
 * A single keyframe in a parsed channel. Between two consecutive keyframes the
 * channel eases from {@code prev.value} to {@code next.value} using {@code
 * next.easing}; before the first keyframe, eases from the channel's
 * {@link ParsedBoneAnimation} default value into {@code first.value} using
 * {@code first.easing}.
 */
public record ParsedKeyframe(int tick, float value, EmoteEasing easing) {
}
