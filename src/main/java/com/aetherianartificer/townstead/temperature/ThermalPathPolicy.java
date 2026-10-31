package com.aetherianartificer.townstead.temperature;

/** Uses the selected backend's seasonal climate, rather than assuming every winter biome is cold. */
public final class ThermalPathPolicy {
    private ThermalPathPolicy() {}
    public static boolean avoidColdWater(float ambient, float coldSensitivity, boolean aquatic, boolean emergency) {
        return Float.isFinite(ambient) && ambient < 18 && coldSensitivity > 0 && !aquatic && !emergency;
    }
    public static float waterCost(float original, boolean avoid, boolean alreadyInWater) {
        if (!avoid || original < 0) return original;
        // Forbid voluntary entry; keep water traversable while escaping it.
        return alreadyInWater ? Math.max(original, 32) : -1;
    }
}
