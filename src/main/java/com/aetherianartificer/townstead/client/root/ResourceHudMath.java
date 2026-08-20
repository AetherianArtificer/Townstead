package com.aetherianartificer.townstead.client.root;

/** Pure resource-HUD math, kept separate so range and visibility edge cases are unit-testable. */
public final class ResourceHudMath {
    private ResourceHudMath() {}

    public static float normalized(int value, int min, int max) {
        if (max <= min) return 0f;
        return Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
    }

    public static int filledUnits(float normalized, int units) {
        if (units <= 0) return 0;
        return Math.max(0, Math.min(units, Math.round(normalized * units)));
    }

    public static float contextualAlpha(long ageMillis, int holdTicks, int fadeTicks) {
        long holdMillis = Math.max(0, holdTicks) * 50L;
        long fadeMillis = Math.max(0, fadeTicks) * 50L;
        if (ageMillis <= holdMillis) return 1f;
        if (fadeMillis == 0L || ageMillis >= holdMillis + fadeMillis) return 0f;
        return 1f - (ageMillis - holdMillis) / (float) fadeMillis;
    }
}
