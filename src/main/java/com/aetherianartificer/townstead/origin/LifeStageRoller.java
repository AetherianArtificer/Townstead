package com.aetherianartificer.townstead.origin;

import net.minecraft.util.RandomSource;

/**
 * Spawn-time roll of a villager's per-stage day durations. The origin's effective
 * Life Cycle gene supplies the absolute base day-count per stage plus a
 * {@code variance}; this scatters each stage's length around its base.
 *
 * <p>Variance is rolled independently per stage, so the same villager can have a
 * fast childhood and a long senior. A variance of {@code 0} reproduces the
 * cycle's declared day-counts exactly.</p>
 */
public final class LifeStageRoller {

    private LifeStageRoller() {}

    /**
     * Roll per-stage day-counts; returns an int[] (game-days) aligned to
     * {@code cycle.stages()}. Base durations are scattered by the per-stage
     * variance and multiplied by {@code scale} — 1.0 for brisk (MCA-fast,
     * calendar-independent), larger for lifelike (life spans years).
     */
    public static int[] roll(LifeCycle cycle, float variance, float scale, RandomSource random) {
        if (cycle == null || cycle.isEmpty()) return new int[0];
        if (variance < 0f) variance = 0f;
        if (scale <= 0f) scale = 1.0f;
        int n = cycle.size();
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            LifeStage stage = cycle.stageAt(i);
            float varRoll = variance > 0f ? 1.0f + (random.nextFloat() * 2f - 1f) * variance : 1.0f;
            out[i] = Math.max(1, Math.round(stage.days() * scale * varRoll));
        }
        return out;
    }
}
