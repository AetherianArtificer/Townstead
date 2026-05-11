package com.aetherianartificer.townstead.client.animation.emote;

import java.util.List;

/**
 * Per-bone keyframe lists. Each axis has a list of keyframes plus the channel's
 * "rest" default value (typically zero for rotation/translation, one for scale).
 *
 * <p>Field naming follows Townstead's canonical Minecraft axes — {@code xRot} = X
 * rotation in radians (Emotecraft's "pitch"), {@code yRot} = "yaw", {@code zRot} =
 * "roll".</p>
 */
public record ParsedBoneAnimation(
        List<ParsedKeyframe> xPos, float xPosDefault,
        List<ParsedKeyframe> yPos, float yPosDefault,
        List<ParsedKeyframe> zPos, float zPosDefault,
        List<ParsedKeyframe> xRot, float xRotDefault,
        List<ParsedKeyframe> yRot, float yRotDefault,
        List<ParsedKeyframe> zRot, float zRotDefault,
        List<ParsedKeyframe> xScale, float xScaleDefault,
        List<ParsedKeyframe> yScale, float yScaleDefault,
        List<ParsedKeyframe> zScale, float zScaleDefault,
        List<ParsedKeyframe> bend, float bendDefault,
        List<ParsedKeyframe> bendDirection, float bendDirectionDefault,
        boolean translationKeyed,
        boolean scaleKeyed,
        boolean bendKeyed
) {
    public boolean hasAnyKeyframes() {
        return !xPos.isEmpty() || !yPos.isEmpty() || !zPos.isEmpty()
                || !xRot.isEmpty() || !yRot.isEmpty() || !zRot.isEmpty()
                || !xScale.isEmpty() || !yScale.isEmpty() || !zScale.isEmpty()
                || !bend.isEmpty() || !bendDirection.isEmpty();
    }
}
