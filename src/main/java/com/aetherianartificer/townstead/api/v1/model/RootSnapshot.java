package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;

/** A Root definition: the identity assigned to a villager or player, with its life cycle. */
public record RootSnapshot(
        String id,
        String displayName,
        String species,
        String ancestry,
        String lineage,
        String effectiveSpecies,
        List<String> defaultGenes,
        List<LifeStageInfo> lifeStages
) {
    public RootSnapshot {
        defaultGenes = defaultGenes == null ? List.of() : List.copyOf(defaultGenes);
        lifeStages = lifeStages == null ? List.of() : List.copyOf(lifeStages);
    }

    /** One life stage; {@code presentsAs} is the canonical stage it maps to: {@code baby}, {@code child}, {@code teen}, {@code adult}, {@code senior}. */
    public record LifeStageInfo(
            String id,
            String label,
            int days,
            float scale,
            String presentsAs,
            float narrativeStart,
            float narrativeEnd
    ) {
    }
}
