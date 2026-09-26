package com.aetherianartificer.townstead.needs;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.Switchboard;

/** The world's needs pace and whether empty needs hurt, read safely from anywhere. */
public final class NeedPace {
    private NeedPace() {}

    public static double pace() {
        try {
            return Switchboard.get(TownsteadConfig.NEEDS_PACE);
        } catch (IllegalStateException | LinkageError e) {
            return 1.0;
        }
    }

    public static boolean canKill() {
        try {
            return Switchboard.get(TownsteadConfig.NEEDS_CAN_KILL);
        } catch (IllegalStateException | LinkageError e) {
            return false;
        }
    }

    private static volatile long graceUntil = Long.MIN_VALUE;

    /**
     * Holds off need damage until {@code gameTime}. Set when "Needs can kill" is switched on, so
     * villagers who sat at zero while needs were harmless get a day to eat.
     */
    public static void graceUntil(long gameTime) {
        graceUntil = gameTime;
    }

    public static boolean inGrace(long gameTime) {
        return gameTime < graceUntil;
    }

    /** A drain interval shortened or lengthened by the pace, never below one tick. */
    public static int interval(int base) {
        return Math.max(1, (int) Math.round(base / pace()));
    }
}
