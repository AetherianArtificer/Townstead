package com.aetherianartificer.townstead.api;

import java.util.List;
import java.util.Map;

/** Serializable read-only entity state for one Townstead-aware villager or player. */
public record TownsteadVillagerSnapshot(
        String uuid,
        String name,
        String entityType,
        String originId,
        String lifeStage,
        long biologicalAgeDays,
        int apparentAgeYears,
        boolean immortal,
        boolean ageless,
        boolean senior,
        String personalityId,
        String professionId,
        int professionLevel,
        int professionXp,
        float fertility,
        TownsteadAgeSnapshot age,
        TownsteadScheduleSnapshot schedule,
        TownsteadNeedsSnapshot needs,
        Map<String, String> carriedVariants,
        List<String> expressedAlleles,
        Map<String, Float> heritage
) {}
