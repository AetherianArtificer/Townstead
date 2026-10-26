package com.aetherianartificer.townstead.root.rig;

/** Pure physical-size policy. Dimensions are blocks, independent of animated model bounds. */
public record BodySize(float width, float height, float eyeHeight) {
    public static final float MIN_WIDTH = .2f, MIN_HEIGHT = .2f;
    // A centred path through an open door has only 5/16 clearance on the leaf side.
    // 0.7 fits the total opening but clips the leaf when following block-centred waypoints.
    public static final float MAX_WIDTH = .6f, MAX_HEIGHT = 1.9f;

    public enum Posture { STANDING, CROUCHING, SWIMMING }

    public static float factor(float value) {
        return Float.isFinite(value) && value > 0 ? value : 1f;
    }

    public static BodySize resolve(RigDefinition.Hitbox base, double horizontal, double vertical, Posture pose) {
        if (!base.scaleWithEntity()) horizontal = vertical = 1;
        horizontal = Double.isFinite(horizontal) && horizontal > 0 ? horizontal : 1;
        vertical = Double.isFinite(vertical) && vertical > 0 ? vertical : 1;
        float width = clamp(base.width() * horizontal, MIN_WIDTH, MAX_WIDTH);
        float standing = clamp(base.height() * vertical, MIN_HEIGHT, MAX_HEIGHT);
        float ratio = switch (pose) {
            case STANDING -> 1f;
            case CROUCHING -> Math.min(1f, base.crouchedHeight() / base.height());
            case SWIMMING -> Math.min(1f, base.swimmingHeight() / base.height());
        };
        float height = clamp(standing * ratio, MIN_HEIGHT, standing);
        float eyeRatio = base.eyeHeight() > 0 ? base.eyeHeight() / base.height() : .85f;
        return new BodySize(width, height, clamp(height * eyeRatio, .05f, height - .05f));
    }

    /** Hold expanding axes at their current size while blocked; shrinking is always allowed. */
    public BodySize deferGrowth(float currentWidth, float currentHeight) {
        float w = Math.min(width, currentWidth), h = Math.min(height, currentHeight);
        return new BodySize(w, h, clamp(eyeHeight * h / height, .05f, h - .05f));
    }

    private static float clamp(double value, float min, float max) {
        return (float)Math.max(min, Math.min(max, value));
    }
}
