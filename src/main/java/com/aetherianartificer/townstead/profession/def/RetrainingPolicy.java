package com.aetherianartificer.townstead.profession.def;

import java.util.Locale;

/**
 * Whether learned skills can be unlearned. {@link #FREE}: retrain at will. {@link #COSTLY}:
 * retraining is allowed but has a cost (resources/time, enforced by Townstead). {@link #LOCKED}:
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
