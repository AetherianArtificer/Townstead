package com.aetherianartificer.townstead.root;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A lineage: a named specialization of an ancestry (e.g. Moon Elf under Elf),
 * with its own nomenclature. When a root references a lineage, the founder
 * genome starts from its ancestry's genome, then this lineage's own
 * {@link #genome()} is layered on top.
 *
 * <p>Heritage records an individual's realised ancestry composition and can
 * name cross-ancestry identities such as Half-Elves. Lineage is not the
 * mixed-ancestry layer.</p>
 *
 * <p>Loaded from {@code data/<ns>/lineage/<path>.json}. None ship built-in; lineages are
 * authored by data packs.</p>
 */
public record Lineage(
        ResourceLocation id,
        Component displayName,
        @Nullable ResourceLocation ancestry,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genome,
        SpawnBias spawnBias,
        com.aetherianartificer.townstead.culture.CulturalSpawnBias culturalSpawnBias
) {
    public Lineage {
        genome = genome == null ? Genome.EMPTY : genome;
        spawnBias = spawnBias == null ? SpawnBias.EMPTY : spawnBias;
        culturalSpawnBias = culturalSpawnBias == null ? com.aetherianartificer.townstead.culture.CulturalSpawnBias.EMPTY : culturalSpawnBias;
    }
}
