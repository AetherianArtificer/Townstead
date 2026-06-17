package com.aetherianartificer.townstead.pheno.capability;

/**
 * How a {@link CapabilityContribution} combines into the resolved value. The numeric fold
 * order is REPLACE, then ADD, then MULTIPLY, then MIN, then MAX. {@link #OR} sets a FLAG on.
 * {@link #DENY} dominates everything: an active DENY forces the capability off (FLAG) or to
 * its identity (NUMERIC), regardless of priority.
 */
public enum Op {
    ADD,
    MULTIPLY,
    MIN,
    MAX,
    REPLACE,
    OR,
    DENY;
}
