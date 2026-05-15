package com.aetherianartificer.townstead.client.animation.emote;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Townstead's source-neutral representation of an Emotecraft animation, decoupled
 * from any Emotecraft / playerAnim class so the per-frame render path holds no
 * reflective references.
 *
 * <p>Bone keys use Townstead's canonical part names ({@code head}, {@code body},
 * {@code left_arm}, ...), already mapped from Emotecraft's camelCase by
 * {@link EmoteBoneMapping}.</p>
 */
public record ParsedEmote(
        ResourceLocation id,
        String displayName,
        int beginTick,
        int endTick,
        int stopTick,
        int returnToTick,
        LoopType loopType,
        boolean easingBefore,
        boolean nsfw,
        Map<String, ParsedBoneAnimation> bones
) {
    /** Mirrors Emotecraft's notion of looping; {@link #LOOP} corresponds to {@code isInfinite=true}. */
    public enum LoopType { PLAY_ONCE, LOOP }
}
