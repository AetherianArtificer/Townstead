package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only state of one Townstead-aware villager or player at the moment it was taken.
 * Players carry identity, root and personality; their other fields are empty or zero.
 */
public record VillagerSnapshot(
        UUID uuid,
        String name,
        String entityType,
        boolean player,
        Optional<VillageId> homeVillage,
        String rootId,
        String personalityId,
        String lifeStage,
        long biologicalAgeDays,
        int apparentAgeYears,
        boolean immortal,
        boolean ageless,
        boolean senior,
        float fertility,
        String professionId,
        String professionPathId,
        int professionTier,
        int professionXp,
        NeedsSnapshot needs,
        ScheduleSnapshot schedule,
        Map<String, String> carriedVariants,
        List<String> expressedAlleles,
        Map<String, Float> heritage
) {
    public VillagerSnapshot {
        homeVillage = homeVillage == null ? Optional.empty() : homeVillage;
        carriedVariants = carriedVariants == null ? Map.of() : Map.copyOf(carriedVariants);
        expressedAlleles = expressedAlleles == null ? List.of() : List.copyOf(expressedAlleles);
        heritage = heritage == null ? Map.of() : Map.copyOf(heritage);
    }

    public boolean hasProfession() {
        return professionId != null && !professionId.isEmpty();
    }
}
