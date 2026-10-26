package com.aetherianartificer.townstead.root.locomotion.fly;

import net.minecraft.world.level.Level;

/** Caps route plans per server tick, so a crowd of fliers can't all plan on the same tick. */
public final class PlanThrottle {

    private static final int PLANS_PER_TICK = 4;

    private static long tick = Long.MIN_VALUE;
    private static int used;

    private PlanThrottle() {}

    /** Server thread only. */
    public static boolean tryAcquire(Level level) {
        long now = level.getGameTime();
        if (now != tick) {
            tick = now;
            used = 0;
        }
        if (used >= PLANS_PER_TICK) return false;
        used++;
        return true;
    }
}
