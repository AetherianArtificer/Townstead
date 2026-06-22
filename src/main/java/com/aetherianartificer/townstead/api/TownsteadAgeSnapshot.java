package com.aetherianartificer.townstead.api;

/** Serializable read-only age and life-stage state for one Townstead villager. */
public record TownsteadAgeSnapshot(
        String lifeStage,
        long biologicalAgeDays,
        int apparentAgeYears,
        boolean immortal,
        boolean ageless,
        boolean senior
) {}
