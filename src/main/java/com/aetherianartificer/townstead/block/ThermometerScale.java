package com.aetherianartificer.townstead.block;

/** Physical column scale, independent of comfort bands and the selected display unit. */
public final class ThermometerScale {
    public static final int MAX_LEVEL = 18;
    private ThermometerScale() {}

    public static int at(float celsius) {
        if (!Float.isFinite(celsius)) return 12;
        return Math.max(0, Math.min(MAX_LEVEL, Math.round((celsius + 40f) / 5f)));
    }

    public static int update(int current, float celsius) {
        if (!Float.isFinite(celsius)) return current;
        float center = current * 5f - 40f;
        return Math.abs(celsius - center) < 3f ? current : at(celsius);
    }
}
