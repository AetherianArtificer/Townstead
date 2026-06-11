package com.aetherianartificer.townstead.profession.def;

import java.util.List;

/**
 * A profession's XP progression: the cumulative XP thresholds that begin each tier and the
 * per-Minecraft-day XP cap. Tier 1 is the first entry; the highest reachable tier is the list
 * size. Mirrors the shape of the existing hardcoded {@code ProfessionXpType} so those can be
 * expressed as data later.
 */
public record ProgressionTrack(List<Integer> tierThresholds, int dailyCap) {

    public int maxTier() {
        return Math.max(1, tierThresholds.size());
    }
}
