package com.aetherianartificer.townstead.api;

/** Serializable read-only gene variant catalog entry. */
public record TownsteadGeneVariantSnapshot(
        String id,
        String displayName,
        int weight,
        String type
) {}
