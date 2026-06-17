package com.aetherianartificer.townstead.pheno.capability;

import java.util.List;

/**
 * The resolved state of one capability: its effective {@code value}, the contributions that
 * actually shaped it ({@code applied}), and those that were present but did not affect it
 * ({@code ignored}: inactive, lost an exclusivity/stacking tie, or overridden by a DENY). The
 * ignored list is what makes {@code /pheno explain} able to say why something did not count.
 */
public record Resolved(
        CapabilityKey key,
        double value,
        List<CapabilityContribution> applied,
        List<CapabilityContribution> ignored) {

    public boolean flag() {
        return value >= 0.5d;
    }

    public double number() {
        return value;
    }
}
