package com.aetherianartificer.townstead.origin.gene;

/**
 * One parsed gene's type-specific config (like {@code TriggerInstance}). The
 * behavior (effect application, rendering) is owned by the {@link GeneType} and
 * is stubbed for now; this carries the data plus the UI {@link GeneDisplay}.
 */
public interface GeneInstance {

    /** The owning gene type's key (e.g. {@code townstead_origins:scaled_part}). */
    String typeKey();

    /** How the picker should render this gene. */
    GeneDisplay display();
}
