package com.aetherianartificer.townstead.root;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A broad inherited population within a species (Human, Elven, Dwarven, …).
 * Ancestry is where a pure founder genome lives; an assignment profile
 * referencing this ancestry uses its {@link #genome()} as a base.
 *
 * <p>Loaded from {@code data/<ns>/ancestry/<path>.json}. A race's life cycle is
 * not stored here — it rides the genome as a Life Cycle gene (see
 * {@link com.aetherianartificer.townstead.root.gene.types.LifeCycleGeneType}).</p>
 */
public record Ancestry(
        ResourceLocation id,
        Component displayName,
        @Nullable ResourceLocation species,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genome,
        SpawnBias spawnBias,
        com.aetherianartificer.townstead.culture.CulturalSpawnBias culturalSpawnBias
) {
    public Ancestry {
        genome = genome == null ? Genome.EMPTY : genome;
        spawnBias = spawnBias == null ? SpawnBias.EMPTY : spawnBias;
        culturalSpawnBias = culturalSpawnBias == null ? com.aetherianartificer.townstead.culture.CulturalSpawnBias.EMPTY : culturalSpawnBias;
    }
}
