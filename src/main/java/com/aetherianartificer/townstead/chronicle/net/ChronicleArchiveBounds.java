package com.aetherianartificer.townstead.chronicle.net;

/** Inclusive box containment for archive buildings, tolerant of unordered corners. */
final class ChronicleArchiveBounds {
    private ChronicleArchiveBounds() {}

    static boolean within(int ax, int ay, int az, int bx, int by, int bz,
                          int px, int py, int pz) {
        return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
                && py >= Math.min(ay, by) && py <= Math.max(ay, by)
                && pz >= Math.min(az, bz) && pz <= Math.max(az, bz);
    }
}
