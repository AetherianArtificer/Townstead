package com.aetherianartificer.townstead.origin;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The bag of genes an origin contributes: per-gene {@link GeneRange}s over MCA's
 * float genes (keyed by normalized lowercase gene key, see
 * {@link OriginGenes}) plus free-form gene tags for future visual/gameplay hooks.
 *
 * <p>Composed bottom-up (ancestry → heritage → origin) via {@link #mergedWith}:
 * a later layer's gene entries replace the same key; tag lists union.</p>
 */
public record Genome(Map<String, GeneRange> genes, List<ResourceLocation> tags) {
    public static final Genome EMPTY = new Genome(Map.of(), List.of());

    public Genome {
        genes = Map.copyOf(genes);
        tags = List.copyOf(tags);
    }

    public boolean isEmpty() {
        return genes.isEmpty() && tags.isEmpty();
    }

    /** Layer {@code override} on top of this genome (override wins per gene; tags union). */
    public Genome mergedWith(Genome override) {
        if (override == null || override.isEmpty()) return this;
        if (this.isEmpty()) return override;
        Map<String, GeneRange> mergedGenes = new LinkedHashMap<>(this.genes);
        mergedGenes.putAll(override.genes);
        LinkedHashSet<ResourceLocation> mergedTags = new LinkedHashSet<>(this.tags);
        mergedTags.addAll(override.tags);
        return new Genome(mergedGenes, new ArrayList<>(mergedTags));
    }
}
