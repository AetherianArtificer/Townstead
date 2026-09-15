package com.aetherianartificer.townstead.snow;

/** Deterministic accumulation/thaw rule, applied by ordinary server random ticks. */
public final class SnowCoatingPolicy {
    private SnowCoatingPolicy() {}

    public static boolean next(boolean coated, SnowWeather weather, boolean exposed,
                               boolean wet, int blockLight) {
        if (!weather.enabled() || !weather.cold() || !exposed || wet || blockLight >= 10) return false;
        return coated || weather.snowing();
    }

    public static boolean usesTownsteadCoating(boolean serene, boolean ecliptic) {
        return serene && !ecliptic;
    }
}
