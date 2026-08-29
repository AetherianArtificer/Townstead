package com.aetherianartificer.townstead.root;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A named, selectable assignment profile in the Roots system. It is not a
 * biological tier after lineage: it points to a species and either an ancestry
 * or lineage, then supplies founder defaults and optional presentation/genome
 * overrides. The individual's realised inherited identity is {@link Heritage}.
 * Its effective founder genome is resolved by
 * {@link RootRegistry#effectiveGenome}.
 *
 * <p>Loaded from {@code data/<ns>/origin/<path>.json}. The built-in
 * {@code townstead_roots:overworlder} is Humanoid / Human with default ranges.</p>
 */
public record Root(
        ResourceLocation id,
        Component displayName,
        @Nullable ResourceLocation species,
        @Nullable ResourceLocation ancestry,
        @Nullable ResourceLocation lineage,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genome,
        SpawnBias spawnBias,
        /**
         * The item tag this root's own body counts as ({@code kin_flesh}), so a predator root
         * never eats its own kind. Null means human, which is what every humanoid is without
         * writing anything.
         */
        @Nullable ResourceLocation kinFlesh,
        /**
         * Whether eating other sapients is ordinary predation for this root ({@code
         * eats_sapients}) — a spider-folk taking human meat is hunting, not transgression.
         * Only consulted when the cannibalism setting reaches the predators tier.
         */
        boolean eatsSapients
) {
    public Root {
        genome = genome == null ? Genome.EMPTY : genome;
        spawnBias = spawnBias == null ? SpawnBias.EMPTY : spawnBias;
    }
}
