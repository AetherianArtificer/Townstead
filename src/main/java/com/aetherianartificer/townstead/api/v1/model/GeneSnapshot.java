package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;

/** A gene definition and its variants. */
public record GeneSnapshot(
        String id,
        String displayName,
        String description,
        String category,
        String dominance,
        String locus,
        int weight,
        String displayMode,
        List<VariantInfo> variants
) {
    public GeneSnapshot {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public record VariantInfo(String id, String displayName, int weight, String type) {
    }
}
