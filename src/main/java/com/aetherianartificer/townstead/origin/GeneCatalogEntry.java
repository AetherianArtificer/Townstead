package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;

/**
 * One gene's display data for the catalog sync / picker, flattened to primitives
 * so the client renders it without the server-only {@code GeneRegistry} or Java
 * gene types.
 *
 * <p>{@code displayKind} is {@link GeneDisplay.Kind#ordinal()}
 * (0 = RANGE, 1 = BOOLEAN, 2 = INFLUENCE). {@code min}/{@code max} apply to RANGE;
 * {@code targetId}/{@code amount} to INFLUENCE. {@code dominanceOrdinal} is
 * {@link com.aetherianartificer.townstead.origin.gene.Dominance#ordinal()}
 * (0 = DOMINANT, 1 = RECESSIVE); {@code alleleGroup} is empty when none.</p>
 */
public record GeneCatalogEntry(
        String id,
        String name,
        String description,
        String category,
        int displayKind,
        float min,
        float max,
        String targetId,
        float amount,
        int dominanceOrdinal,
        String alleleGroup,
        int weight
) {
    public boolean isRange() {
        return displayKind == GeneDisplay.Kind.RANGE.ordinal();
    }

    public boolean isInfluence() {
        return displayKind == GeneDisplay.Kind.INFLUENCE.ordinal();
    }

    public boolean isRecessive() {
        return dominanceOrdinal == com.aetherianartificer.townstead.origin.gene.Dominance.RECESSIVE.ordinal();
    }
}
