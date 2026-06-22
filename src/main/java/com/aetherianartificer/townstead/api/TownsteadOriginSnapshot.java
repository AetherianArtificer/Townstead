package com.aetherianartificer.townstead.api;

import java.util.List;

/** Serializable read-only origin catalog entry. */
public record TownsteadOriginSnapshot(
        String id,
        String displayName,
        String species,
        String ancestry,
        String lineage,
        String effectiveSpecies,
        List<String> defaultGenes,
        List<TownsteadLifeStageSnapshot> lifeStages
) {}
