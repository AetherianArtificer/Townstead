package com.aetherianartificer.townstead.api.v1.model;

import java.util.Map;
import java.util.Optional;

/**
 * Needs aggregated over a village's residents. Loaded residents contribute live readings; the
 * rest contribute their last known ones. {@link #residentCount} is MCA's roll,
 * {@link #knownCount} how many of them Townstead has any record for, {@link #loadedCount} how many
 * were read live. {@link #oldestSampleWorldDay} is the age of the stalest reading used.
 */
public record VillageNeedsSummary(
        VillageId village,
        int residentCount,
        int knownCount,
        int loadedCount,
        Map<String, NeedStat> byNeed,
        int collapsedCount,
        long oldestSampleWorldDay,
        long worldDay
) {
    public VillageNeedsSummary {
        byNeed = byNeed == null ? Map.of() : Map.copyOf(byNeed);
    }

    public Optional<NeedStat> stat(String needId) {
        return Optional.ofNullable(byNeed.get(needId));
    }

    /** Share of the roll the summary actually covers. */
    public double coverage() {
        return residentCount <= 0 ? 0.0 : Math.min(1.0, knownCount / (double) residentCount);
    }

    /** Per-need aggregate. {@code mean} and the extremes are on the need's own scale. */
    public record NeedStat(
            String needId,
            int sampled,
            double mean,
            int min,
            int max,
            int inCrisis,
            NeedBand band
    ) {
    }
}
