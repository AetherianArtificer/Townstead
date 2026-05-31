package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A lineage within a species (Human, Elven, Dwarven, …). Ancestry is where a
 * pure-blood genome lives; an origin referencing this ancestry inherits its
 * {@link #genome()} as the base of the composed genome.
 *
 * <p>Loaded from {@code data/<ns>/ancestry/<path>.json}. A race's life cycle is
 * not stored here — it rides the genome as a Life Cycle gene (see
 * {@link com.aetherianartificer.townstead.origin.gene.types.LifeCycleGeneType}).</p>
 */
public record Ancestry(
        ResourceLocation id,
        Component displayName,
        @Nullable ResourceLocation species,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genome
) {
    public Ancestry {
        genome = genome == null ? Genome.EMPTY : genome;
    }
}
