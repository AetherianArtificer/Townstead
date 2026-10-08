package com.aetherianartificer.townstead.profession.def;

import java.util.List;

/**
 * A profession's XP progression: the cumulative XP thresholds that begin each tier, the
 * per-Minecraft-day XP cap, the absolute XP ceiling, how many Insight checkpoints sit inside each
 * rank span, and the share of XP still earned past the daily cap. Tier 1 is the first entry; the
 * highest reachable tier is the list size.
 */
public record ProgressionTrack(List<Integer> tierThresholds, int dailyCap, int maxXp,
                               int checkpointsPerRank, int overCapPercent) {

    /** Default XP ceiling when a track omits {@code max_xp}; XP keeps counting past the top rank. */
    public static final int DEFAULT_MAX_XP = 200000;
    public static final int DEFAULT_CHECKPOINTS = 3;
    public static final int DEFAULT_OVER_CAP_PERCENT = 25;

    public ProgressionTrack(List<Integer> tierThresholds, int dailyCap, int maxXp) {
        this(tierThresholds, dailyCap, maxXp, DEFAULT_CHECKPOINTS, DEFAULT_OVER_CAP_PERCENT);
    }

    /** Back-compat constructor for callers that do not specify a ceiling. */
    public ProgressionTrack(List<Integer> tierThresholds, int dailyCap) {
        this(tierThresholds, dailyCap, DEFAULT_MAX_XP);
    }

    public int maxTier() {
        return Math.max(1, tierThresholds.size());
    }
}
