package com.aetherianartificer.townstead.block;

import net.minecraft.util.StringRepresentable;

/** Ambient Celsius bands, independent of any particular villager's thermal tolerance. */
public enum ThermometerBand implements StringRepresentable {
    FREEZING("freezing"), COLD("cold"), MILD("mild"), WARM("warm"), HOT("hot");

    private static final float[] BOUNDARIES = {0f, 15f, 25f, 35f};
    private static final float HYSTERESIS = 0.5f;
    private final String name;

    ThermometerBand(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }

    public static ThermometerBand at(float celsius) {
        if (!Float.isFinite(celsius)) return MILD;
        int index = 0;
        while (index < BOUNDARIES.length && celsius >= BOUNDARIES[index]) index++;
        return values()[index];
    }

    /** Keep the current display within half a degree of its boundaries. */
    public ThermometerBand update(float celsius) {
        if (!Float.isFinite(celsius)) return this;
        int index = ordinal();
        if (index > 0 && celsius < BOUNDARIES[index - 1] - HYSTERESIS) return at(celsius);
        if (index < BOUNDARIES.length && celsius >= BOUNDARIES[index] + HYSTERESIS) return at(celsius);
        return this;
    }
}
