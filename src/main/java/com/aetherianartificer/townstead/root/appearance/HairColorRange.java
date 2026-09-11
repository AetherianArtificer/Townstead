package com.aetherianartificer.townstead.root.appearance;

import com.aetherianartificer.townstead.root.GeneRange;
import net.minecraft.util.RandomSource;

/** One rectangular region of MCA's genetic hair colormap. Both axes are normalized to {@code [0,1]}. */
public record HairColorRange(GeneRange darkness, GeneRange redness, int weight) {

    public HairColorRange {
        darkness = darkness == null ? new GeneRange(0f, 1f) : darkness;
        redness = redness == null ? new GeneRange(0f, 1f) : redness;
        weight = Math.max(0, weight);
    }

    public float sampleDarkness(RandomSource random) { return darkness.sample(random); }
    public float sampleRedness(RandomSource random) { return redness.sample(random); }

    public float clampDarkness(float value) { return clamp(value, darkness); }
    public float clampRedness(float value) { return clamp(value, redness); }

    /** Squared distance to this range; zero means the color is already allowed. */
    public float distanceSquared(float currentDarkness, float currentRedness) {
        float d = currentDarkness - clampDarkness(currentDarkness);
        float r = currentRedness - clampRedness(currentRedness);
        return d * d + r * r;
    }

    private static float clamp(float value, GeneRange range) {
        return Math.max(range.min(), Math.min(range.max(), value));
    }
}
