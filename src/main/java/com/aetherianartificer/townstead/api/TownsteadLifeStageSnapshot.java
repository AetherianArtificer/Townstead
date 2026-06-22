package com.aetherianartificer.townstead.api;

/** Serializable read-only life-stage definition. */
public record TownsteadLifeStageSnapshot(
        String id,
        String label,
        int days,
        float scale,
        String presentsAs,
        float narrativeStart,
        float narrativeEnd
) {}
