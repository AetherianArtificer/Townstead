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
        com.aetherianartificer.townstead.culture.CulturalSpawnBias culturalSpawnBias,
        /**
         * False ({@code "ancestry_genes": false}) when the lineage belongs to its ancestry by name and
         * category only: its founders don't inherit the ancestry's genes. For a pack that files its
         * lineages under another pack's ancestry (Runic dwarves under Classic Fantasy's Dwarf) without
         * taking on that pack's ears, build or powers.
         */
        boolean ancestryGenes
) {
    public Lineage(ResourceLocation id, Component displayName, @Nullable ResourceLocation ancestry,
                   @Nullable Demonym demonym, @Nullable Component backstory, Genome genome, SpawnBias spawnBias,
                   com.aetherianartificer.townstead.culture.CulturalSpawnBias culturalSpawnBias) {
        this(id, displayName, ancestry, demonym, backstory, genome, spawnBias, culturalSpawnBias, true);
    }

    public Lineage {
        genome = genome == null ? Genome.EMPTY : genome;
        spawnBias = spawnBias == null ? SpawnBias.EMPTY : spawnBias;
        culturalSpawnBias = culturalSpawnBias == null ? com.aetherianartificer.townstead.culture.CulturalSpawnBias.EMPTY : culturalSpawnBias;
    }
}
