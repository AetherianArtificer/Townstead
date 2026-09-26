package com.aetherianartificer.townstead.api.v1.model;

/** Where one entity stands in one profession. */
public record ProfessionProgressSnapshot(
        String professionId,
        int xp,
        int tier,
        int maxTier,
        int xpToday,
        int xpToNextTier,
        long lastTierUpTick
) {
    public boolean atMaxTier() {
        return tier >= maxTier;
    }
}
