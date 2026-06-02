package com.aetherianartificer.townstead.origin.gene;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One of the two copies a villager carries at a locus. An allele names the gene
 * that fills the slot and, for a multi-variant gene, which variant; a
 * single-variant gene has a null variant. {@link #WILD} is the empty slot (the
 * gene is absent on this copy), letting presence/absence genes be carried
 * heterozygously and a recessive resurface a generation later.
 */
public record Allele(@Nullable ResourceLocation geneId, @Nullable String variantId) {

    /** The empty slot: no gene on this copy. */
    public static final Allele WILD = new Allele(null, null);

    private static final String WILD_TOKEN = "~";

    public static Allele of(ResourceLocation geneId, @Nullable String variantId) {
        if (geneId == null) return WILD;
        return new Allele(geneId, variantId == null || variantId.isEmpty() ? null : variantId);
    }

    public boolean isWild() {
        return geneId == null;
    }

    /** {@code geneId#variant}, {@code geneId}, or {@code ~} for wild. */
    public String encode() {
        if (isWild()) return WILD_TOKEN;
        return variantId == null ? geneId.toString() : geneId + "#" + variantId;
    }

    public static Allele decode(@Nullable String s) {
        if (s == null || s.isEmpty() || s.equals(WILD_TOKEN)) return WILD;
        int hash = s.indexOf('#');
        if (hash < 0) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            return id == null ? WILD : new Allele(id, null);
        }
        ResourceLocation id = ResourceLocation.tryParse(s.substring(0, hash));
        return id == null ? WILD : new Allele(id, s.substring(hash + 1));
    }
}
