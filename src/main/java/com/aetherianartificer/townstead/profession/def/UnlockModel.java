package com.aetherianartificer.townstead.profession.def;

import java.util.Locale;

/**
 * How a profession's skills are unlocked. {@link #POINTS}: spend earned skill points.
 * {@link #EXPERIENTIAL}: a skill unlocks by meeting its tier and prerequisites (mastery through
 * use). {@link #HYBRID}: tier and prerequisites gate availability, points pay to learn.
 */
public enum UnlockModel {
    POINTS,
    EXPERIENTIAL,
    HYBRID;

    public static UnlockModel fromString(String raw) {
        if (raw == null) return EXPERIENTIAL;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "points" -> POINTS;
            case "hybrid" -> HYBRID;
            default -> EXPERIENTIAL;
        };
    }
}
