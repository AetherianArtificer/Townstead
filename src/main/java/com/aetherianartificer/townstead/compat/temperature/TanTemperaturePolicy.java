package com.aetherianartificer.townstead.compat.temperature;

/** TAN's discrete shifts applied to Townstead's continuous room reading. */
public final class TanTemperaturePolicy {
    private static final float[] CELSIUS = {-10, 5, 20, 30, 40};
    private TanTemperaturePolicy() {}
    public static float regulate(float ambient, int heating, int cooling, int neutral) {
        if (heating == 0 && cooling == 0 && neutral > 0) return 20;
        if (heating == cooling) return ambient;
        return shift(ambient, heating > cooling ? 2 : -2);
    }
    public static float shift(float ambient, int steps) {
        int index = ambient < -2.5 ? 0 : ambient < 12.5 ? 1 : ambient < 25 ? 2 : ambient < 35 ? 3 : 4;
        return CELSIUS[Math.max(0, Math.min(4, index + steps))];
    }
    public static float internal(float ambient, boolean warmth, boolean chill) {
        int steps = (warmth ? 1 : 0) - (chill ? 1 : 0);
        if (steps == 0) return ambient;
        float adjusted = shift(ambient, steps);
        if (adjusted == 40 && ambient < 35) return 30;
        if (adjusted == -10 && ambient >= -2.5) return 5;
        return adjusted;
    }
}
