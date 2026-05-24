package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The named, selectable top-level entry a villager carries. References a species
 * and either an ancestry or a heritage, and supplies a demonym + backstory
 * (overriding the referenced ancestry/heritage when present). Its effective
 * genome is resolved by {@link OriginRegistry#effectiveGenome}.
 *
 * <p>Loaded from {@code data/<ns>/origin/<path>.json}. The built-in
 * {@code townstead_origins:overworlder} is Humanoid / Human with default ranges.</p>
 */
public record Origin(
        ResourceLocation id,
        Component displayName,
        @Nullable ResourceLocation species,
        @Nullable ResourceLocation ancestry,
        @Nullable ResourceLocation heritage,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genomeOverrides
) {
    public Origin {
        genomeOverrides = genomeOverrides == null ? Genome.EMPTY : genomeOverrides;
    }
}
