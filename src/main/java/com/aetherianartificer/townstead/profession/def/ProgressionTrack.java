package com.aetherianartificer.townstead.profession.def;

import java.util.List;

/**
 * A profession's XP progression: the cumulative XP thresholds that begin each tier, the
 * per-Minecraft-day XP cap, and the absolute XP ceiling. Tier 1 is the first entry; the highest
 * reachable tier is the list size. Mirrors the shape of the hardcoded {@code ProfessionXpType} so
 * those built-ins can be overridden by data.
 */
public record ProgressionTrack(List<Integer> tierThresholds, int dailyCap, int maxXp) {

    /** Default XP ceiling when a track omits {@code max_xp} (matches the open-ended built-ins). */
    public static final int DEFAULT_MAX_XP = 200000;

    /** Back-compat constructor for callers that do not specify a ceiling. */
    public ProgressionTrack(List<Integer> tierThresholds, int dailyCap) {
        this(tierThresholds, dailyCap, DEFAULT_MAX_XP);
    }

    public int maxTier() {
        return Math.max(1, tierThresholds.size());
    }
}
