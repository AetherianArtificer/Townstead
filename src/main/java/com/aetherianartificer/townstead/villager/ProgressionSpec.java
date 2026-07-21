package com.aetherianartificer.townstead.villager;

/**
 * The runtime shape the {@link ProfessionProgress} engine operates on, built from a career's
 * data-driven {@link com.aetherianartificer.townstead.profession.def.ProgressionTrack}.
 * Generalised to an arbitrary number of tiers; the shipped built-in defs are five-tier and
 * reproduce the legacy hardcoded behaviour exactly.
 */
public record ProgressionSpec(int[] tierThresholds, int dailyXpCap, int maxXp) {

    public int maxTier() {
        return Math.max(1, tierThresholds.length);
    }

    /** XP required to advance out of {@code tier} (1-based) into the next, clamped at the top. */
    public int thresholdForTier(int tier) {
        if (tierThresholds.length == 0) return 0;
        if (tier < 0) tier = 0;
        if (tier >= tierThresholds.length) return tierThresholds[tierThresholds.length - 1];
        return tierThresholds[tier];
    }

    /** The 1-based tier an XP total stands in. */
    public int tierForXp(int xp) {
        int tier = 1;
        for (int i = 0; i < tierThresholds.length; i++) {
            if (xp >= tierThresholds[i]) tier = i + 1;
        }
        return Math.max(1, tier);
    }
}
