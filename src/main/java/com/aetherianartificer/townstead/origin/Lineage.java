package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A lineage: a named specialization of an ancestry (e.g. Dark Elf under Elf), with its
 * own nomenclature. When an origin references a lineage, the composed genome starts from
 * the union of the lineage's listed ancestries' genomes, then {@link #genomeOverrides()}
 * is layered on top.
 *
 * <p>(A future Heritage tier will sit above this for cross-ancestry hybrids like
 * half-elves; a lineage is a branch of a single ancestry.)</p>
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
        Genome genomeOverrides
) {
    public Lineage {
        ancestries = ancestries == null ? List.of() : List.copyOf(ancestries);
        genomeOverrides = genomeOverrides == null ? Genome.EMPTY : genomeOverrides;
    }
}
