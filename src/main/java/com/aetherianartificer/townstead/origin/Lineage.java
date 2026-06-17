package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A lineage: a named specialization of an ancestry (e.g. Dark Elf under Elf),
 * with its own nomenclature. When an assignment profile references a lineage,
 * the founder genome starts from the union of its listed ancestries' genomes,
 * then this lineage's own {@link #genome()} is layered on top.
 *
 * <p>Heritage follows lineage in the conceptual Origins model. It records an
 * individual's realised ancestry composition and can name cross-ancestry
 * identities such as Half-Elves.</p>
 *
 * <p>Loaded from {@code data/<ns>/lineage/<path>.json}. None ship built-in; lineages are
 * authored by data packs.</p>
 */
public record Lineage(
        ResourceLocation id,
        Component displayName,
        List<ResourceLocation> ancestries,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genome,
        SpawnBias spawnBias
) {
    public Lineage {
        ancestries = ancestries == null ? List.of() : List.copyOf(ancestries);
        genome = genome == null ? Genome.EMPTY : genome;
        spawnBias = spawnBias == null ? SpawnBias.EMPTY : spawnBias;
    }
}
