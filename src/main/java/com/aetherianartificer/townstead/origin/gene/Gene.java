package com.aetherianartificer.townstead.origin.gene;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A loaded, self-contained gene: shared metadata plus one or more
 * {@link GeneVariant}s. Races grant genes by id; the picker groups them by
 * {@link #category} and renders each via {@link #display}.
 *
 * <p>A gene with a single variant is a plain always-expressed gene (skin, diet,
 * life cycle, …). A gene with several variants is a weighted pick-one: one
 * variant is rolled at birth and carried on the villager. {@link #locus},
 * {@link #dominance} and {@link #weight} drive cross-gene allele resolution at a
 * shared slot (e.g. the life-cycle locus). Loaded from
 * {@code data/<ns>/gene/<path>.json}.</p>
 */
public record Gene(
        ResourceLocation id,
        Component displayName,
        @Nullable Component description,
        String category,
        Dominance dominance,
        @Nullable ResourceLocation locus,
        int weight,
        List<GeneVariant> variants
) {
    public Gene {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("gene " + id + " has no variants");
        }
        variants = List.copyOf(variants);
    }

    /** True when this gene is a weighted pick-one (more than one variant). */
    public boolean hasVariants() {
        return variants.size() > 1;
    }

    /** Convenience for single-variant genes: the sole variant's parsed config. */
    public GeneInstance instance() {
        return variants.get(0).instance();
    }

    public GeneDisplay display() {
        return hasVariants() ? GeneDisplay.VARIANTS : variants.get(0).instance().display();
    }
}
