package com.aetherianartificer.townstead.client.animation;

/**
 * A source-neutral transform for one external animation target.
 *
 * <p>Values are expressed in Minecraft model units and radians, matching
 * {@link net.minecraft.client.model.geom.ModelPart} fields.</p>
 */
public record AnimationTransform(
        String target,
        Float x,
        Float y,
        Float z,
        Float xRot,
        Float yRot,
        Float zRot,
        Float xScale,
        Float yScale,
        Float zScale,
        boolean applyTranslation,
        boolean applyScale,
        Operation operation
) {
    public enum Operation {
        ADD,
        SET
    }

    public static AnimationTransform rotate(String target, float xRot, float yRot, float zRot, Operation operation) {
        return new AnimationTransform(target, null, null, null, xRot, yRot, zRot, null, null, null, false, false, operation);
    }

    public static AnimationTransform translate(String target, float x, float y, float z, Operation operation) {
        return new AnimationTransform(target, x, y, z, null, null, null, null, null, null, true, false, operation);
    }
}
