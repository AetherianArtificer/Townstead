package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;

/**
 * The feels-like temperatures a body is comfortable between. The active temperature mod sets it
 * for everyone, villagers included, since it enforces it on players and the pack tunes it; without
 * one, Townstead's own range applies. A Root's offset and acclimation move it per body.
 */
public record ComfortRange(float low, float high) {
    public float mid() { return (low + high) / 2f; }

    public ComfortRange offset(float degrees) {
        return degrees == 0f ? this : new ComfortRange(low + degrees, high + degrees);
    }

    /** How far {@code feelsLike} lies past either end, signed: negative below, positive above, zero inside. */
    public float excess(float feelsLike) {
        return feelsLike < low ? feelsLike - low : feelsLike > high ? feelsLike - high : 0f;
    }

    private static ComfortRange cached;
    private static long cachedAt = Long.MIN_VALUE;

    /** The active temperature mod's range, else Townstead's; re-read every few seconds for live config edits. */
    public static ComfortRange active() {
        long now = System.nanoTime();
        ComfortRange range = cached;
        if (range != null && now - cachedAt < 5_000_000_000L) return range;
        range = null;
        try {
            var bridge = TemperatureBridgeResolver.get();
            float[] own = bridge == null ? null : bridge.comfortRangeCelsius();
            if (own != null && own.length == 2 && Float.isFinite(own[0]) && Float.isFinite(own[1]) && own[1] > own[0])
                range = new ComfortRange(own[0], own[1]);
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (range == null) range = TemperatureSettings.get().comfortRange();
        cached = range;
        cachedAt = now;
        return range;
    }

    /** The range for this body: the active range, moved by its Root's offset and its acclimation. */
    public static ComfortRange of(ThermalProfile profile, float acclimation) {
        return ThermalAcclimation.shift(active().offset(profile.comfortOffset()), acclimation, profile.acclimationLimits());
    }
}
