package com.aetherianartificer.townstead.origin.gene;

import net.minecraft.resources.ResourceLocation;

/**
 * One gene a race passes down as part of its genome, with the per-race base
 * {@code occurrence} (presence probability for boolean trait genes; ignored for
 * range genes, which always express).
 *
 * <p>A variant gene's per-race distribution and naming live in the gene itself
 * (its {@code variants} weights and {@code display_name}), so a race references it
 * by bare id like any other gene — no per-reference overrides here.</p>
 */
public record InheritedGene(ResourceLocation geneId, float occurrence) {
    public InheritedGene {
        occurrence = Math.max(0f, Math.min(1f, occurrence));
    }

    public static InheritedGene of(ResourceLocation geneId) {
        return new InheritedGene(geneId, 1.0f);
    }
}
