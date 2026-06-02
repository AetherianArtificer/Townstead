package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.Dominance;
import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import com.aetherianartificer.townstead.origin.gene.types.LifeCycleGeneType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-side registry of data-pack-loaded {@link Origin}, plus the genome
 * resolution that composes species/ancestry/lineage into the effective genome
 * a villager is given. Populated by {@link OriginJsonLoader}.
 */
public final class OriginRegistry {

    /** Namespace shared by all built-in origin data ({@code townstead_origins}). */
    public static final String NAMESPACE = "townstead_origins";

    /** The always-present built-in origin used as the fallback everywhere. */
    public static final ResourceLocation DEFAULT_ID =
            Objects.requireNonNull(DataPackLang.parseId(NAMESPACE + ":overworlder"));

    private static volatile Map<ResourceLocation, Origin> ENTRIES = Map.of();

    private OriginRegistry() {}

    static void replaceAll(Map<ResourceLocation, Origin> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static Origin byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<Origin> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** The given origin, or the default ({@link #DEFAULT_ID}) if it is missing. */
    @Nullable
    public static Origin resolveOrDefault(@Nullable ResourceLocation id) {
        if (id != null) {
            Origin direct = ENTRIES.get(id);
            if (direct != null) return direct;
        }
        return ENTRIES.get(DEFAULT_ID);
    }

    /**
     * The ancestry-fraction vector a founder of this origin is seeded with: a single
     * ancestry at 1.0 for an ancestry-based origin, or an even split across a
     * lineage's listed ancestries. Heritage tracks ancestry, not lineage, so a
     * pure Dark Elf seeds {@code {elf:1}} (its "Dark Elf" name comes from the origin).
     */
    public static Heritage seedHeritage(@Nullable ResourceLocation id) {
        Origin origin = resolveOrDefault(id);
        if (origin == null) return Heritage.EMPTY;
        if (origin.ancestry() != null) return Heritage.pure(origin.ancestry());
        if (origin.lineage() != null) {
            Lineage lineage = LineageRegistry.byId(origin.lineage());
            if (lineage != null && !lineage.ancestries().isEmpty()) {
                Map<ResourceLocation, Float> shares = new LinkedHashMap<>();
                float share = 1f / lineage.ancestries().size();
                for (ResourceLocation ancestry : lineage.ancestries()) shares.merge(ancestry, share, Float::sum);
                return new Heritage(shares);
            }
        }
        return Heritage.EMPTY;
    }

    /** Effective genome for an origin id, falling back to the default origin. */
    public static Genome effectiveGenome(@Nullable ResourceLocation id) {
        Origin origin = resolveOrDefault(id);
        return origin == null ? Genome.EMPTY : effectiveGenome(origin);
    }

    /**
     * Compose the genome bottom-up: ancestry (or, for a lineage-based origin,
     * the union of the lineage's ancestries then its overrides) → the origin's
     * own overrides. Per-gene entries replace; tag lists union. Missing refs are
     * skipped (they leave the base un-narrowed).
     */
    public static Genome effectiveGenome(Origin origin) {
        Genome base = Genome.EMPTY;
        if (origin.lineage() != null) {
            Lineage lineage = LineageRegistry.byId(origin.lineage());
            if (lineage != null) {
                for (ResourceLocation ancestryId : lineage.ancestries()) {
                    Ancestry ancestry = AncestryRegistry.byId(ancestryId);
                    if (ancestry != null) base = base.mergedWith(ancestry.genome());
                }
                base = base.mergedWith(lineage.genome());
            }
        } else if (origin.ancestry() != null) {
            Ancestry ancestry = AncestryRegistry.byId(origin.ancestry());
            if (ancestry != null) base = ancestry.genome();
        }
        return base.mergedWith(origin.genome());
    }

    /**
     * The origin's inherited genes with same-locus alleles collapsed to the last-declared
     * (most specific) one, so a lineage's chronotype gene replaces the ancestry's rather
     * than both being rolled and shown. Locus-less genes are all kept, in order. This is the
     * effective grant list for rolling and for the picker display.
     */
    public static List<InheritedGene> effectiveInheritedGenes(@Nullable ResourceLocation id) {
        List<InheritedGene> raw = effectiveGenome(id).genes();
        LinkedHashMap<Object, InheritedGene> byKey = new LinkedHashMap<>();
        int i = 0;
        for (InheritedGene ig : raw) {
            Gene gene = GeneRegistry.byId(ig.geneId());
            Object key = gene != null && gene.locus() != null ? gene.locus() : "#" + i + ":" + ig.geneId();
            byKey.put(key, ig);
            i++;
        }
        return new ArrayList<>(byKey.values());
    }

    /**
     * Effective life cycle for an origin id, drawn from the Life Cycle gene the
     * origin's genome expresses. Falls back to {@link LifeCycle#defaultHumanLike()}
     * when no cycle gene is inherited.
     */
    public static LifeCycle effectiveLifeCycle(@Nullable ResourceLocation id) {
        LifeCycleGeneType.Instance gene = effectiveCycleGene(id);
        return gene == null || gene.cycle().isEmpty() ? LifeCycle.defaultHumanLike() : gene.cycle();
    }

    /**
     * The Life Cycle gene an origin expresses, resolved across its inherited
     * genes at the shared life-cycle locus: dominant beats recessive, ties break
     * by weight then by inheritance order. {@code null} if no cycle gene is
     * inherited (callers fall back to {@link LifeCycle#defaultHumanLike()}).
     */
    @Nullable
    public static LifeCycleGeneType.Instance effectiveCycleGene(@Nullable ResourceLocation id) {
        Genome genome = effectiveGenome(id);
        Gene best = null;
        for (InheritedGene inherited : genome.genes()) {
            Gene gene = GeneRegistry.byId(inherited.geneId());
            if (gene == null || !(gene.instance() instanceof LifeCycleGeneType.Instance)) continue;
            if (best == null || cycleAlleleWins(gene, best)) best = gene;
        }
        return best == null ? null : (LifeCycleGeneType.Instance) best.instance();
    }

    /** The trait-granting genes an origin expresses (each carries its trait id + occurrence). */
    public static java.util.List<com.aetherianartificer.townstead.origin.gene.types.TraitOccurrenceGeneType.Instance>
            traitGenes(@Nullable ResourceLocation id) {
        Genome genome = effectiveGenome(id);
        java.util.List<com.aetherianartificer.townstead.origin.gene.types.TraitOccurrenceGeneType.Instance> out =
                new java.util.ArrayList<>();
        for (InheritedGene inherited : genome.genes()) {
            Gene gene = GeneRegistry.byId(inherited.geneId());
            if (gene != null && gene.instance()
                    instanceof com.aetherianartificer.townstead.origin.gene.types.TraitOccurrenceGeneType.Instance t) {
                out.add(t);
            }
        }
        return out;
    }

    /** Locus resolution for cycle alleles: dominant beats recessive, then higher weight; otherwise the incumbent holds. */
    private static boolean cycleAlleleWins(Gene challenger, Gene incumbent) {
        boolean challengerDominant = challenger.dominance() == Dominance.DOMINANT;
        boolean incumbentDominant = incumbent.dominance() == Dominance.DOMINANT;
        if (challengerDominant != incumbentDominant) return challengerDominant;
        return challenger.weight() > incumbent.weight();
    }

    /** The origin's demonym, falling back to its lineage's then its ancestry's. */
    @Nullable
    public static Demonym resolveDemonym(Origin origin) {
        if (origin.demonym() != null) return origin.demonym();
        if (origin.lineage() != null) {
            Lineage lineage = LineageRegistry.byId(origin.lineage());
            if (lineage != null && lineage.demonym() != null) return lineage.demonym();
        }
        if (origin.ancestry() != null) {
            Ancestry ancestry = AncestryRegistry.byId(origin.ancestry());
            if (ancestry != null && ancestry.demonym() != null) return ancestry.demonym();
        }
        return null;
    }

    /** The origin's backstory, falling back to its lineage's then its ancestry's. */
    @Nullable
    public static Component resolveBackstory(Origin origin) {
        if (origin.backstory() != null) return origin.backstory();
        if (origin.lineage() != null) {
            Lineage lineage = LineageRegistry.byId(origin.lineage());
            if (lineage != null && lineage.backstory() != null) return lineage.backstory();
        }
        if (origin.ancestry() != null) {
            Ancestry ancestry = AncestryRegistry.byId(origin.ancestry());
            if (ancestry != null && ancestry.backstory() != null) return ancestry.backstory();
        }
        return null;
    }
}
