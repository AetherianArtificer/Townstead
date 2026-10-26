package com.aetherianartificer.townstead.root.gene;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * How often a gene shows when its bearer carries a single copy ({@code "penetrance"}, 0 to 1).
 * Two copies always show, and a gene without the field always shows from one copy, as before.
 * Against a different gene's copy at the same locus, a copy that fails its roll yields to one that
 * passes (a cat coat that doesn't assert itself leaves the human skin); when both fail, ordinary
 * dominance decides.
 * This is what lets a half-breed vary: a half-cat carries one copy of each feline gene, so its
 * ears (penetrance 1) always show while its muzzle (0.2, say) shows in one child in five.
 *
 * <p>Settled once per individual by its {@link Genotype#seed() expression seed}: stable for one
 * villager, different between siblings. A genotype from before expression seeds (seed 0) shows
 * every copy, so nobody loaded from an older save changes how they look.</p>
 */
public final class GenePenetrance {
    private static volatile Map<ResourceLocation, Float> values = Map.of();

    private GenePenetrance() {}

    static void replaceAll(Map<ResourceLocation, Float> next) { values = Map.copyOf(next); }

    /** The gene's single-copy show chance; 1 when it declares none. */
    public static float of(ResourceLocation geneId) {
        ResourceLocation canonical = GeneRegistry.canonicalId(geneId);
        Float value = canonical == null ? null : values.get(canonical);
        return value == null ? 1f : value;
    }

    /** Whether a lone copy of {@code allele} shows on the individual with this expression seed. */
    public static boolean shows(Allele allele, long seed, @Nullable ResourceLocation locus) {
        float chance = of(allele.geneId());
        if (chance >= 1f || seed == 0L) return true;
        if (chance <= 0f) return false;
        long h = seed ^ 0x5DEECE66DL
                ^ (locus == null ? 0L : locus.toString().hashCode() * 0x9E3779B97F4A7C15L)
                ^ ((long) allele.geneId().toString().hashCode() << 32);
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return ((h >>> 40) / (float) (1L << 24)) < chance;
    }
}
