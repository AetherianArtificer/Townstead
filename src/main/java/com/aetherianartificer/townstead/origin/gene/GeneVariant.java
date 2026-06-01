package com.aetherianartificer.townstead.origin.gene;

import net.minecraft.network.chat.Component;

/**
 * One option of a {@link Gene}. A gene is a self-contained block whose
 * {@code variants} are the mutually-exclusive forms it can manifest as; exactly
 * one is rolled at birth (weighted by {@link #weight}) and carried on the
 * villager. Simple genes have a single implicit variant.
 *
 * <p>The framework owns {@code id}/{@code label}/{@code weight}; the gene's
 * {@link GeneType} owns {@link #instance} (the type-specific config it reads to
 * manifest the variant).</p>
 */
public record GeneVariant(String id, Component label, int weight, GeneInstance instance) {
    public GeneVariant {
        weight = Math.max(1, weight);
    }
}
