package com.aetherianartificer.townstead.hunger;

/** Short, non-finishing sips while holding one serving; only the final use consumes it. */
final class RecreationalDrinkTiming {
    static final int HOLD_TICKS = 1800;
    private static final int SIP_INTERVAL = 400;
    private static final int SIP_START = 200;

    private RecreationalDrinkTiming() {}

    static boolean startsSip(long elapsed, int seed) {
        return sipping(elapsed, 32, seed) && !sipping(elapsed - 1, 32, seed);
    }

    static boolean sipping(long elapsed, int useDuration) {
        return sipping(elapsed, useDuration, 0);
    }

    static boolean sipping(long elapsed, int useDuration, int seed) {
        if (elapsed < 0 || elapsed >= HOLD_TICKS) return false;
        int interval = SIP_INTERVAL + Math.floorMod(seed, 201);
        int start = SIP_START + Math.floorMod(seed / 201, 141);
        int length = Math.min(16 + Math.floorMod(seed / 8601, 5), Math.max(0, useDuration - 1));
        long phase = elapsed % interval;
        return phase >= start && phase < start + length;
    }
}
