package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A named, selectable assignment profile in the Origins system. It is not a
 * biological tier after lineage: it points to a species and either an ancestry
 * or lineage, then supplies founder defaults and optional presentation/genome
 * overrides. The individual's realised inherited identity is {@link Heritage}.
 * Its effective founder genome is resolved by
 * {@link OriginRegistry#effectiveGenome}.
 *
 * <p>Loaded from {@code data/<ns>/origin/<path>.json}. The built-in
 * {@code townstead_origins:overworlder} is Humanoid / Human with default ranges.</p>
 */
public record Origin(
        ResourceLocation id,
        Component displayName,
        @Nullable ResourceLocation species,
        @Nullable ResourceLocation ancestry,
        @Nullable ResourceLocation lineage,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genome,
        SpawnBias spawnBias
) {
    public Origin {
        genome = genome == null ? Genome.EMPTY : genome;
        spawnBias = spawnBias == null ? SpawnBias.EMPTY : spawnBias;
    }
}
