package com.aetherianartificer.townstead.client.animation.nativeclip;

/** Stable per-performance variation, never per-frame randomness. */
final class SocialPlaybackTiming {
    private SocialPlaybackTiming() {}
    static boolean varies(String channel) {
        return "social".equals(channel) || "hospitality".equals(channel);
    }
    static float speed(int seed) { return 0.92F + Math.floorMod(seed, 161) / 1000F; }
    static int delay(int seed, int duration) {
        return Math.min(Math.floorMod(seed / 161, 25), Math.max(0, duration / 5));
    }
}
