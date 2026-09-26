package com.aetherianartificer.townstead.api.v1.model;

/**
 * One need's reading with its own scale.
 *
 * @param value   the current reading
 * @param min     the scale floor
 * @param max     the scale ceiling
 * @param neutral the reading Townstead considers "fine" for this need
 * @param band    Townstead's own word for where the reading sits, for example {@code hungry}
 * @param crisis  true when the band is the one Townstead treats as an emergency
 * @param enabled false when the need is switched off or not simulated for this villager
 */
public record NeedLevel(
        String needId,
        int value,
        int min,
        int max,
        int neutral,
        String band,
        boolean crisis,
        boolean enabled
) {
    /** How far the reading sits below neutral, as a fraction of the scale; zero at or above it. */
    public double shortfall() {
        int range = max - min;
        if (range <= 0) return 0.0;
        return Math.max(0.0, (neutral - value) / (double) range);
    }

    /** The reading as a fraction of the scale, 0 at {@link #min} and 1 at {@link #max}. */
    public double fraction() {
        int range = max - min;
        if (range <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (value - min) / (double) range));
    }
}
