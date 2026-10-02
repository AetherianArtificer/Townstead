package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.pheno.state.StateEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays the gene overlays of an entity's active states over its expressed alleles. The
 * genotype is never touched, so leaving the state restores the carrier exactly. Overlays
 * apply in priority order, each its suppressions and then its grants, so a higher tier can
 * take back what a lower one gave; a grant replaces whatever the carrier expresses at the
 * granted gene's locus.
 */
public final class AspectOverlay {
    // Resolving a state can evaluate conditions that read expressed genes again.
    private static final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);

    private AspectOverlay() {}

    public interface GeneLookup {
        /** The canonical id of a loaded gene, or null when no loaded gene owns the id. */
        @Nullable ResourceLocation canonical(ResourceLocation id);

        @Nullable ResourceLocation locus(ResourceLocation canonical);

        /** A weighted variant of a multi-variant gene picked by {@code seed}, or null for none. */
        default @Nullable String roll(ResourceLocation canonical, long seed) {
            return null;
        }
    }

    private static final GeneLookup REGISTRY = new GeneLookup() {
        @Override
        public @Nullable ResourceLocation canonical(ResourceLocation id) {
            return GeneRegistry.canonicalId(id);
        }

        @Override
        public @Nullable ResourceLocation locus(ResourceLocation canonical) {
            Gene gene = GeneRegistry.byId(canonical);
            return gene == null ? null : gene.locus();
        }

        @Override
        public @Nullable String roll(ResourceLocation canonical, long seed) {
            Gene gene = GeneRegistry.byId(canonical);
            if (gene == null || !gene.hasVariants()) return null;
            int total = 0;
            for (GeneVariant variant : gene.variants()) total += Math.max(1, variant.weight());
            int pick = (int) Math.floorMod(seed, (long) total);
            for (GeneVariant variant : gene.variants()) {
                pick -= Math.max(1, variant.weight());
                if (pick < 0) return variant.id();
            }
            return null;
        }
    };

    public static List<Allele> apply(LivingEntity entity, List<Allele> expressed) {
        if (applying.get()) return expressed;
        applying.set(true);
        try {
            List<StateEffect> effects = EntityStates.activeGeneEffects(entity);
            if (effects.isEmpty()) return expressed;
            List<StateEffect.Genes> overlays = new ArrayList<>(effects.size());
            for (StateEffect effect : effects) overlays.add(effect.genes());
            return apply(expressed, overlays, REGISTRY, entity.getUUID().getLeastSignificantBits());
        } finally {
            applying.remove();
        }
    }

    /**
     * A grant naming no variant rolls one per carrier, seeded by {@code seed} (the carrier's id)
     * alone, so every vampire keeps its own eyes without anything being stored, and genes sharing
     * a style list (eyes, then glowing eyes) keep the same pick.
     */
    static List<Allele> apply(List<Allele> expressed, List<StateEffect.Genes> overlays, GeneLookup genes, long seed) {
        List<Allele> out = new ArrayList<>(expressed);
        for (StateEffect.Genes overlay : overlays) {
            for (ResourceLocation id : overlay.suppress()) {
                ResourceLocation canonical = genes.canonical(id);
                if (canonical != null) out.removeIf(allele -> canonical.equals(canonicalOf(allele, genes)));
            }
            for (StateEffect.Grant grant : overlay.grant()) {
                ResourceLocation canonical = genes.canonical(grant.gene());
                if (canonical == null) continue;
                ResourceLocation locus = genes.locus(canonical);
                out.removeIf(allele -> {
                    ResourceLocation carried = canonicalOf(allele, genes);
                    return canonical.equals(carried) || locus != null && carried != null && locus.equals(genes.locus(carried));
                });
                String variant = grant.variant() != null ? grant.variant()
                        : genes.roll(canonical, mix(seed));
                out.add(Allele.of(canonical, variant));
            }
        }
        return List.copyOf(out);
    }

    private static long mix(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        return h ^ (h >>> 33);
    }

    private static @Nullable ResourceLocation canonicalOf(Allele allele, GeneLookup genes) {
        return allele.geneId() == null ? null : genes.canonical(allele.geneId());
    }
}
