package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A mix of ancestries with its own nomenclature (e.g. Half-Elf → "half-elves").
 * When an origin references a heritage, the composed genome starts from the
 * union of the listed ancestries' genomes, then {@link #genomeOverrides()} is
 * layered on top.
 *
 * <p>Loaded from {@code data/<ns>/heritage/<path>.json}. None ship built-in;
 * heritages are authored by data packs.</p>
 */
public record Heritage(
        ResourceLocation id,
        Component displayName,
        List<ResourceLocation> ancestries,
        @Nullable Demonym demonym,
        @Nullable Component backstory,
        Genome genomeOverrides
) {
    public Heritage {
        ancestries = ancestries == null ? List.of() : List.copyOf(ancestries);
        genomeOverrides = genomeOverrides == null ? Genome.EMPTY : genomeOverrides;
    }
}
