package com.aetherianartificer.townstead.api;

import java.util.List;

/** Serializable read-only gene catalog entry. */
public record TownsteadGeneSnapshot(
        String id,
        String displayName,
        String description,
        String category,
        String dominance,
        String locus,
        int weight,
        String displayMode,
        List<TownsteadGeneVariantSnapshot> variants
) {}
