package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * What a race contributes to its genome: a single ordered list of the genes (from
 * the {@code GeneRegistry}) it carries. This is the unified gene array — body
 * metrics (size, melanin via {@code body_metric} genes), appearance, diet, life
 * cycle, traits and variant genes all live here, referenced by id. There is no
 * separate MCA-float map anymore; a body-metric gene carries its own range and is
 * resolved from the registry when needed.
 *
 * <p>For founder assignment, composed from species, ancestry and lineage, then
 * optional assignment-profile overrides via {@link #mergedWith}. A later
 * contribution for the same gene id replaces the earlier one. The assignment
 * profile is not itself a biological tier. Cross-gene collapsing at a shared
 * locus is done later, in
 * {@link OriginRegistry#effectiveInheritedGenes}.</p>
 */
public record Genome(List<InheritedGene> genes) {

    public static final Genome EMPTY = new Genome(List.of());

    public Genome {
        genes = List.copyOf(genes);
    }

    public boolean isEmpty() {
        return genes.isEmpty();
    }

    /** Layer {@code override} on top: its entries replace same-id genes; new ones append. */
    public Genome mergedWith(Genome override) {
        if (override == null || override.isEmpty()) return this;
        if (this.isEmpty()) return override;
        LinkedHashMap<ResourceLocation, InheritedGene> merged = new LinkedHashMap<>();
        for (InheritedGene gene : this.genes) merged.put(gene.geneId(), gene);
        for (InheritedGene gene : override.genes) merged.put(gene.geneId(), gene);
        return new Genome(new ArrayList<>(merged.values()));
    }
}
