package com.aetherianartificer.townstead.downed;

/** How long a downed villager stays down when nobody helps them up. */
public enum DownedRecovery {
    /** About five minutes. */
    MINUTES,
    /** Until the next dawn, and at least half a day. */
    DAWN,
    /** A full day. */
    DAY
}
