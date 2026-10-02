package com.aetherianartificer.townstead.client.rebirth;

/** Whether Destiny should offer "Where you died", on MCA builds whose Destiny list comes from config. */
public final class RebirthDestinyClient {
    private RebirthDestinyClient() {}

    private static volatile boolean offered;

    public static void offer() {
        offered = true;
    }

    public static boolean offered() {
        return offered;
    }

    public static void clear() {
        offered = false;
    }
}
