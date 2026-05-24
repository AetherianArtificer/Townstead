package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a race contributes to its genome:
 * <ul>
 *   <li>{@code genes} — per-gene {@link GeneRange}s over MCA's standard float genes
 *       (size, melanin, …), used only to clamp those on assignment. Owned by the
 *       Body/Head editor tabs; not shown in the Origins viewer.</li>
 *   <li>{@code inheritedGenes} — the custom genes (from the {@code GeneRegistry}) this
 *       race passes down, each with a base occurrence ({@link InheritedGene}). The
 *       lego "blocks", shown in the Origins viewer.</li>
 * </ul>
 *
 * <p>Composed bottom-up (ancestry → heritage → origin) via {@link #mergedWith}:
 * a later layer's MCA gene entries replace the same key; inherited genes merge by
 * gene id with the later layer's entry (occurrence) winning.</p>
 */
public record Genome(Map<String, GeneRange> genes, List<InheritedGene> inheritedGenes) {
    public static final Genome EMPTY = new Genome(Map.of(), List.of());

    public Genome {
        genes = Map.copyOf(genes);
        inheritedGenes = List.copyOf(inheritedGenes);
    }

    public boolean isEmpty() {
        return genes.isEmpty() && inheritedGenes.isEmpty();
    }

    /** Layer {@code override} on top (override wins per MCA gene; inherited genes merge by id). */
    public Genome mergedWith(Genome override) {
        if (override == null || override.isEmpty()) return this;
        if (this.isEmpty()) return override;
        Map<String, GeneRange> mergedGenes = new LinkedHashMap<>(this.genes);
        mergedGenes.putAll(override.genes);
        LinkedHashMap<ResourceLocation, InheritedGene> merged = new LinkedHashMap<>();
        for (InheritedGene gene : this.inheritedGenes) merged.put(gene.geneId(), gene);
        for (InheritedGene gene : override.inheritedGenes) merged.put(gene.geneId(), gene);
        return new Genome(mergedGenes, new ArrayList<>(merged.values()));
    }
}
