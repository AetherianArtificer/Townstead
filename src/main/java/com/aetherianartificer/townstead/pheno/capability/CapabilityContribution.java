package com.aetherianartificer.townstead.pheno.capability;

import org.jetbrains.annotations.Nullable;

/**
 * One source's contribution to a capability: the {@link Op} and value, a priority (higher wins
 * inside an exclusivity or stacking group), optional group ids, the {@link Provenance}, and
 * whether it is currently {@code active} (a gated contribution whose condition does not hold is
 * recorded but excluded from the resolved value, so {@code /pheno explain} can still show it).
 *
 * <p>{@code exclusivityGroup}: at most one active contribution in the group applies (the rest
 * are mutually-exclusive alternatives, highest priority wins, ties break on the provenance
 * source id). {@code stackingGroup}: a label for contributions that combine together through
 * the normal fold; it does not collapse them (a per-group stack cap is a planned addition).
 */
public record CapabilityContribution(
        CapabilityKey key,
        Op op,
        double value,
        int priority,
        @Nullable String stackingGroup,
        @Nullable String exclusivityGroup,
        Provenance provenance,
        boolean active) {

    public static CapabilityContribution flag(CapabilityKey key, Provenance provenance, boolean active) {
        return new CapabilityContribution(key, Op.OR, 1d, 0, null, null, provenance, active);
    }

    public static CapabilityContribution numeric(CapabilityKey key, Op op, double value,
                                                 Provenance provenance, boolean active) {
        return new CapabilityContribution(key, op, value, 0, null, null, provenance, active);
    }
}
