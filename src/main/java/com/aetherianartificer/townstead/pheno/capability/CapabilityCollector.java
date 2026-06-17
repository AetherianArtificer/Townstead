package com.aetherianartificer.townstead.pheno.capability;

import java.util.ArrayList;
import java.util.List;

/**
 * Sink a {@link CapabilitySource} writes its contributions into. A thin wrapper over a list so
 * the source API stays additive and sources stay unaware of resolution order.
 */
public final class CapabilityCollector {

    private final List<CapabilityContribution> contributions = new ArrayList<>();

    public void add(CapabilityContribution contribution) {
        contributions.add(contribution);
    }

    public void flag(CapabilityKey key, Provenance provenance, boolean active) {
        contributions.add(CapabilityContribution.flag(key, provenance, active));
    }

    public void numeric(CapabilityKey key, Op op, double value, Provenance provenance, boolean active) {
        contributions.add(CapabilityContribution.numeric(key, op, value, provenance, active));
    }

    public List<CapabilityContribution> contributions() {
        return contributions;
    }
}
