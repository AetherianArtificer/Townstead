package com.aetherianartificer.townstead.work.producer;

public enum ProducerBlockedReason {
    NONE,
    NO_WORKSITE,
    NO_INGREDIENTS,
    NO_RECIPE,
    NO_STORAGE,
    UNREACHABLE,
    NO_FUEL,
    NO_DRIVER,
    OUTPUT_BLOCKED,
    UNSUPPORTED_RECIPE,
    /** Told to work the list only, and the list has nothing workable. Rest, not a fault. */
    STANDING_DOWN
}
