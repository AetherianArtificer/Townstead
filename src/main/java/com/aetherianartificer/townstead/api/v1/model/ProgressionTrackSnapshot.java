package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;
import java.util.OptionalInt;

/**
 * A profession's progression, exactly as Townstead evaluates it. Tiers are 1-based.
 * {@link #tierThresholds} has one entry per tier: the XP at which that tier begins, ascending,
 * so the first entry is normally {@code 0} and a track with N entries has N tiers.
 */
public record ProgressionTrackSnapshot(
        String professionId,
        List<Integer> tierThresholds,
        int maxTier,
        int maxXp,
        int dailyXpCap
) {
    public ProgressionTrackSnapshot {
        tierThresholds = tierThresholds == null ? List.of() : List.copyOf(tierThresholds);
    }

    /** The tier an XP total stands in; the same arithmetic Townstead uses. */
    public int tierForXp(int xp) {
        int tier = 1;
        for (int i = 0; i < tierThresholds.size(); i++) {
            if (xp >= tierThresholds.get(i)) tier = i + 1;
        }
        return Math.max(1, tier);
    }

    /** The XP at which {@code tier} begins, or empty when the track has no such tier. */
    public OptionalInt thresholdFor(int tier) {
        int index = tier - 1;
        return index >= 0 && index < tierThresholds.size()
                ? OptionalInt.of(tierThresholds.get(index)) : OptionalInt.empty();
    }

    public boolean supportsTier(int tier) {
        return tier >= 1 && tier <= maxTier;
    }

    public int remainingXp(int currentXp) {
        return Math.max(0, maxXp - currentXp);
    }
}
