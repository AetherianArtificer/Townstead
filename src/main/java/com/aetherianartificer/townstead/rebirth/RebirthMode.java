package com.aetherianartificer.townstead.rebirth;

/** Whether a player who dies may come back as a new person. */
public enum RebirthMode {
    /** Death works as usual. */
    OFF,
    /** The death screen offers rebirth next to Respawn. */
    OPTIONAL,
    /** Every death is a rebirth. */
    FORCED
}
