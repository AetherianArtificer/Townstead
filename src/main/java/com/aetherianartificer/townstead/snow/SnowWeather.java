package com.aetherianartificer.townstead.snow;

/** Climate input independent of the mod supplying it. */
public record SnowWeather(boolean enabled, boolean cold, boolean snowing) {
    public static final SnowWeather DISABLED = new SnowWeather(false, false, false);
}
