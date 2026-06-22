package com.aetherianartificer.townstead.origin;

/**
 * Pure mapping between the editor's age slider position ({@code 0..1}) and a
 * biological age in days, for a villager's rolled {@code stageDays}.
 *
 * <p>The bar is <em>equidistant by stage</em>: every life stage owns an equal
 * {@code 1/n} slice of the slider regardless of its day-span, so the tiny
 * childhood stages are as draggable as the long adult one. Within a stage the
 * mapping is linear in days. No Minecraft state — testable in isolation.</p>
 */
public final class LifeStageBar {

    private LifeStageBar() {}

    /** Stage index containing {@code bioAge}, or {@code -1} if there are no stages. */
    public static int stageIndexForBioAge(int[] stageDays, int bioAge) {
        if (stageDays == null || stageDays.length == 0) return -1;
        int cumulative = 0;
        for (int i = 0; i < stageDays.length; i++) {
            cumulative += Math.max(0, stageDays[i]);
            if (bioAge < cumulative) return i;
        }
        return stageDays.length - 1;
    }

    /** Slider position {@code v} in {@code [0,1]} → biological age in days. */
    public static int bioForSliderValue(int[] stageDays, double v) {
        int n = (stageDays == null) ? 0 : stageDays.length;
        if (n == 0) return 0;
        double scaled = Math.max(0.0, Math.min(n, v * n));
        int idx = Math.min(n - 1, (int) Math.floor(scaled));
        double f = Math.max(0.0, Math.min(1.0, scaled - idx));
        int cum = 0;
        for (int i = 0; i < idx; i++) cum += Math.max(0, stageDays[i]);
        return cum + (int) Math.round(f * Math.max(0, stageDays[idx]));
    }

    /** Inverse of {@link #bioForSliderValue}: biological age in days → slider position in {@code [0,1]}. */
    public static double sliderValueForBio(int[] stageDays, int bioAge) {
        int n = (stageDays == null) ? 0 : stageDays.length;
        if (n == 0) return 0.0;
        int idx = Math.max(0, stageIndexForBioAge(stageDays, bioAge));
        int cum = 0;
        for (int i = 0; i < idx; i++) cum += Math.max(0, stageDays[i]);
        double f = Math.max(0.0, Math.min(1.0, (bioAge - cum) / (double) Math.max(1, stageDays[idx])));
        return (idx + f) / n;
    }
}
