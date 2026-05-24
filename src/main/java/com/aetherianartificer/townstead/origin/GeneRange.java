package com.aetherianartificer.townstead.origin;

import net.minecraft.util.RandomSource;

/**
 * Inclusive [min, max] range a genome may pin an MCA float gene to. Both bounds
 * are clamped to [0, 1] and swapped if inverted, so a parsed range is always
 * valid. A zero-width range pins the gene to a constant.
 */
public record GeneRange(float min, float max) {
    public GeneRange {
        min = clamp01(min);
        max = clamp01(max);
        if (min > max) {
            float t = min;
            min = max;
            max = t;
        }
    }

    public float sample(RandomSource random) {
        return max <= min ? min : min + random.nextFloat() * (max - min);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
