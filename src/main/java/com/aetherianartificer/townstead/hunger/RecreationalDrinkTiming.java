package com.aetherianartificer.townstead.hunger;

/** Short, non-finishing sips while holding one serving; only the final use consumes it. */
final class RecreationalDrinkTiming {
    static final int HOLD_TICKS = 1200;
    private static final int SIP_INTERVAL = 240;
    private static final int SIP_START = 200;

    private RecreationalDrinkTiming() {}

    static boolean sipping(long elapsed, int useDuration) {
        if (elapsed < 0 || elapsed >= HOLD_TICKS) return false;
        int length = Math.min(16, Math.max(0, useDuration - 1));
        long phase = elapsed % SIP_INTERVAL;
        return phase >= SIP_START && phase < SIP_START + length;
    }
}
