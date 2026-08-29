package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.TownsteadConfig;

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

    public static float exitAlpha(float transitionAlpha, TownsteadConfig.ResourceHudExitStyle style,
                                  long nowMillis, int seed) {
        float alpha = Math.max(0f, Math.min(1f, transitionAlpha));
        if (style == TownsteadConfig.ResourceHudExitStyle.INSTANT) return alpha > 0f ? 1f : 0f;
        if (style != TownsteadConfig.ResourceHudExitStyle.FLICKER || alpha >= 0.999f || alpha <= 0f) {
            return alpha;
        }
        int frame = (int) (nowMillis / 75L);
        int hash = seed ^ frame * 0x9E3779B9;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        float sample = (hash & 0xFFFF) / 65535f;
        return sample < alpha ? Math.min(1f, 0.55f + alpha * 0.45f) : 0f;
    }

    public static int exitSlide(float transitionAlpha, TownsteadConfig.ResourceHudExitStyle style,
                                int distance) {
        if (style != TownsteadConfig.ResourceHudExitStyle.SLIDE) return 0;
        float progress = 1f - Math.max(0f, Math.min(1f, transitionAlpha));
        return Math.round(Math.max(0, distance) * progress * progress);
    }
}
