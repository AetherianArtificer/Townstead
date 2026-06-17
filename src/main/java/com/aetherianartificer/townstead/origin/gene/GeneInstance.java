package com.aetherianartificer.townstead.origin.gene;

import com.aetherianartificer.townstead.pheno.power.PowerComponent;

/**
 * One parsed gene's type-specific config (like {@code TriggerInstance}). The genetics
 * flavor of a {@link PowerComponent}: it adds the gene-picker {@link GeneDisplay} on
 * top of the shared behavior identity, so the behavior is reusable by non-genetic
 * sources while the picker presentation stays here.
 */
public interface GeneInstance extends PowerComponent {

    /** How the picker should render this gene. */
    GeneDisplay display();
}
