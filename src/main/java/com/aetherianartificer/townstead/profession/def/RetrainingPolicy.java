package com.aetherianartificer.townstead.profession.def;

import java.util.Locale;

/**
 * Whether learned skills can be unlearned. {@link #FREE}: retrain at will. {@link #COSTLY}:
 * retraining will have a cost (resources/time, Townstead-owned); the payment mechanism is not
 * built yet, so it is treated as unavailable rather than free until then. {@link #LOCKED}:
 * choices are permanent.
 */
public enum RetrainingPolicy {
    FREE,
    COSTLY,
    LOCKED;

    public static RetrainingPolicy fromString(String raw) {
        if (raw == null) return FREE;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "costly" -> COSTLY;
            case "locked" -> LOCKED;
            default -> FREE;
        };
    }
}
