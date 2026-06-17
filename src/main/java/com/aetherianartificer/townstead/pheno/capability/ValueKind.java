package com.aetherianartificer.townstead.pheno.capability;

/**
 * The shape of a {@link CapabilityKey}'s value. {@link #FLAG} is an on/off capability
 * (can-climb, is-fireproof); {@link #NUMERIC} is a scalar rating folded from contributions
 * (a damage multiplier, a mining-speed bonus). {@code PROFILE} (a structured, registered
 * value) is planned for a later stage.
 */
public enum ValueKind {
    FLAG,
    NUMERIC;
}
