package com.aetherianartificer.townstead.compat.thirst;

/** Townstead core stress adapted to player-only thirst integrations; never ambient Celsius. */
public record ThermalHydrationContext(boolean coldSweat, float signedBodyStress, boolean lsoHeatStroke) {
    public static final ThermalHydrationContext NONE = new ThermalHydrationContext(false, 0, false);

    /** Map the villager's six-band crisis boundary to Cold Sweat's signed +/-100 body scale.
     * This is an adapter for Townstead physiology, not a native Cold Sweat capability reading.
     */
    public static float signedStress(float core, float neutral, float band) {
        return Math.max(-100, Math.min(100, (core - neutral) / Math.max(0.1f, band) / 6 * 100));
    }

    public static float thirstModifier(float temperature, float humidity, float depletion, float harshness) {
        float modifier = depletion * temperature / Math.max(0.001f, humidity);
        if (modifier < 1) modifier = 1 - (1 - modifier) * harshness;
        return Float.isFinite(modifier) ? Math.max(0, modifier) : 1;
    }

    public static float heatExhaustion(boolean heatStroke, boolean enabled, double perFiftyTicks) {
        return heatStroke && enabled && Double.isFinite(perFiftyTicks)
                ? (float) Math.max(0, perFiftyTicks / 50) : 0;
    }
}
