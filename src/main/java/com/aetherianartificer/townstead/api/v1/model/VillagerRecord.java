package com.aetherianartificer.townstead.api.v1.model;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The last state Townstead saw for a villager, kept whether or not the villager is loaded. The
 * readings are as of {@link #lastSeenWorldDay}; nothing here advances while a villager is
 * unloaded.
 */
public record VillagerRecord(
        UUID uuid,
        String name,
        Optional<VillageId> village,
        String professionId,
        int professionTier,
        Map<String, NeedLevel> levels,
        boolean collapsed,
        boolean loaded,
        long lastSeenGameTime,
        long lastSeenWorldDay,
        boolean alive
) {
    public VillagerRecord {
        village = village == null ? Optional.empty() : village;
        levels = levels == null ? Map.of() : Map.copyOf(levels);
    }
}
