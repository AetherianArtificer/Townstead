package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.Allele;
import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.GeneVariant;
import com.aetherianartificer.townstead.origin.gene.Genotype;
import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import com.aetherianartificer.townstead.origin.gene.types.BodyMetricGeneType;
import com.aetherianartificer.townstead.origin.gene.types.LifeCycleGeneType;
import com.aetherianartificer.townstead.origin.gene.types.TraitOccurrenceGeneType;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The diploid genetics engine: seeds a founder's two-allele genotype from its
 * origin, migrates legacy single-variant villagers, draws a child's alleles from
 * its parents, and projects the genotype to the expressed phenotype by dominance.
 *
 * <p>Scope: discrete genes participate as alleles (chronotype, skin, ears, diet,
 * …). Continuous body floats (MCA genetics, blended at breeding), MCA traits (MCA
 * inherits them) and the life-cycle gene (origin-driven in this version) are
 * handled outside the genotype.</p>
 */
public final class Heredity {

    private Heredity() {}

    /** A gene's locus: its declared {@code locus}, else its own id (a private slot). */
    public static ResourceLocation locusOf(Gene gene) {
        return gene.locus() != null ? gene.locus() : gene.id();
    }

    /** Whether a gene is carried as diploid alleles (vs handled by MCA / origin). */
    public static boolean isDiploid(Gene gene) {
        GeneInstance instance = gene.instance();
        return !(instance instanceof BodyMetricGeneType.Instance)
                && !(instance instanceof com.aetherianartificer.townstead.origin.gene.types.ProportionsGeneType.Instance)
                && !(instance instanceof TraitOccurrenceGeneType.Instance)
                && !(instance instanceof LifeCycleGeneType.Instance);
    }

    /** Roll a fresh founder: two independent alleles per diploid gene the origin grants. */
    public static void seedFounder(TownsteadVillager.Life life, ResourceLocation originId, RandomSource random) {
        life.setGenotype(seedGenotype(originId, random));
        life.setHeritage(OriginRegistry.seedHeritage(originId));
        recomputeExpressed(life);
    }

    /** A fresh diploid genotype rolled from an origin's grant list (two alleles per diploid gene). */
    public static Genotype seedGenotype(ResourceLocation originId, RandomSource random) {
        Genotype genotype = new Genotype();
        for (InheritedGene ref : OriginRegistry.effectiveInheritedGenes(originId)) {
            Gene gene = GeneRegistry.byId(ref.geneId());
            if (gene == null || !isDiploid(gene)) continue;
            genotype.set(locusOf(gene), rollAllele(gene, ref, random), rollAllele(gene, ref, random));
        }
        return genotype;
    }

    /**
     * Seed a mixed-ancestry founder by admixing several same-species origins at the
     * given fractions: heritage is the fraction-weighted sum of each origin's seed
     * heritage, and each of the two allele copies at a locus is drawn from a
     * fraction-weighted contributing origin's freshly-seeded genotype (true
     * admixture). The stored origin id is the dominant (largest-share) contributor,
     * which drives the life cycle, traits and body metrics; the genotype and
     * heritage carry the real blend. Falls back to a plain founder for a single share.
     */
    public static void seedMixedFounder(TownsteadVillager.Life life,
                                        List<OriginSelector.Weighted> mix, RandomSource random) {
        if (mix.size() < 2) {
            ResourceLocation only = mix.isEmpty() ? OriginRegistry.DEFAULT_ID
                    : ResourceLocation.tryParse(mix.get(0).originId().toString());
            seedFounder(life, only == null ? OriginRegistry.DEFAULT_ID : only, random);
            return;
        }

        java.util.List<Genotype> genotypes = new java.util.ArrayList<>(mix.size());
        float[] fractions = new float[mix.size()];
        java.util.Map<ResourceLocation, Float> heritage = new java.util.LinkedHashMap<>();
        for (int i = 0; i < mix.size(); i++) {
            OriginSelector.Weighted w = mix.get(i);
            fractions[i] = w.fraction();
            genotypes.add(seedGenotype(w.originId(), random));
            OriginRegistry.seedHeritage(w.originId()).fractions()
                    .forEach((ancestry, share) -> heritage.merge(ancestry, share * w.fraction(), Float::sum));
        }

        Set<ResourceLocation> loci = new LinkedHashSet<>();
        for (Genotype g : genotypes) loci.addAll(g.loci());
        Genotype childGenes = new Genotype();
        for (ResourceLocation locus : loci) {
            childGenes.set(locus, drawAdmixed(genotypes, fractions, locus, random),
                    drawAdmixed(genotypes, fractions, locus, random));
        }

        life.setGenotype(childGenes);
        life.setHeritage(new Heritage(heritage));
        life.setOrigin(dominantOrigin(mix));
        recomputeExpressed(life);
    }

    /**
     * Fill in any diploid genes a loaded villager lacks without disturbing what it
     * already carries: an existing locus is kept; a legacy expressed variant becomes
     * a homozygous pair; everything else is rolled. Self-heals saves from before the
     * genotype existed.
     */
    public static void migrateFounder(TownsteadVillager.Life life, ResourceLocation originId, RandomSource random) {
        Genotype genotype = life.genotype();
        for (InheritedGene ref : OriginRegistry.effectiveInheritedGenes(originId)) {
            Gene gene = GeneRegistry.byId(ref.geneId());
            if (gene == null || !isDiploid(gene)) continue;
            ResourceLocation locus = locusOf(gene);
            if (genotype.has(locus)) continue;
            if (gene.hasVariants() && life.hasCarriedVariant(gene.id().toString())) {
                Allele legacy = Allele.of(gene.id(), life.carriedVariant(gene.id().toString()));
                genotype.set(locus, legacy, legacy);
            } else {
                genotype.set(locus, rollAllele(gene, ref, random), rollAllele(gene, ref, random));
            }
        }
        life.setGenotype(genotype);
        if (!life.hasHeritage()) life.setHeritage(OriginRegistry.seedHeritage(originId));
        recomputeExpressed(life);
    }

    /**
     * A parent's contribution to a child: its origin (for the child's life-cycle
     * template), heritage vector, and genotype to draw an allele from. Derived from
     * a villager's stored state or, for a player/other parent, seeded from its origin.
     */
    public record Parent(String originId, Heritage heritage, Genotype genotype) {}

    /**
     * Resolve any parent entity to a gamete source. A villager uses its stored
     * (seeded) state; a player uses its {@link PlayerOrigin} (default origin if it
     * has none), with a genotype freshly rolled from that origin since players carry
     * no diploid genotype of their own. {@code null} for entities that can't parent.
     */
    @Nullable
    public static Parent parentOf(Entity entity, RandomSource random) {
        if (entity instanceof VillagerEntityMCA villager) {
            TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
            ensureSeeded(life, random);
            return new Parent(life.originId(), life.heritage(), life.genotype());
        }
        if (entity instanceof Player player) {
            ResourceLocation originId = ResourceLocation.tryParse(PlayerOrigin.getOriginId(player));
            if (originId == null) originId = OriginRegistry.DEFAULT_ID;
            return new Parent(originId.toString(), OriginRegistry.seedHeritage(originId),
                    seedGenotype(originId, random));
        }
        return null;
    }

    /**
     * Breed a child from two gamete sources: one allele per locus drawn from each
     * (a parent missing the locus contributes a wild allele), heritage averaged, and
     * the life-cycle origin taken from whichever parent shares the child's dominant
     * ancestry.
     */
    public static void inherit(TownsteadVillager.Life child, Parent mother, Parent father, RandomSource random) {
        Set<ResourceLocation> loci = new LinkedHashSet<>(mother.genotype().loci());
        loci.addAll(father.genotype().loci());

        Genotype childGenes = new Genotype();
        for (ResourceLocation locus : loci) {
            childGenes.set(locus, draw(mother.genotype(), locus, random), draw(father.genotype(), locus, random));
        }
        child.setGenotype(childGenes);
        child.setHeritage(Heritage.blend(mother.heritage(), father.heritage()));
        child.setOrigin(chooseChildOrigin(child.heritage(), mother.originId(), father.originId()));
        recomputeExpressed(child);
    }

    /**
     * Breed a child from the (already resolved) parent entities of the breeding hook.
     * Two parents inherit normally; one resolvable parent makes the child a copy of
     * it; none leaves the founder seeding in place. Used by the baby-item birth path
     * (player + villager) where parents arrive as live entities.
     */
    public static void inheritFromEntities(TownsteadVillager.Life child, List<Entity> parents, RandomSource random) {
        List<Parent> sources = new java.util.ArrayList<>(2);
        for (Entity parent : parents) {
            Parent source = parentOf(parent, random);
            if (source != null) sources.add(source);
            if (sources.size() == 2) break;
        }
        if (sources.isEmpty()) return;
        Parent a = sources.get(0);
        Parent b = sources.size() > 1 ? sources.get(1) : a;
        inherit(child, a, b, random);
    }

    /** Seed a villager's genotype/heritage from its origin if it has neither yet (idempotent). */
    public static void ensureSeeded(TownsteadVillager.Life life, RandomSource random) {
        if (life.hasGenotype() || life.hasHeritage()) return;
        ResourceLocation originId = ResourceLocation.tryParse(life.originId());
        if (originId == null) originId = OriginRegistry.DEFAULT_ID;
        migrateFounder(life, originId, random);
    }

    /** Project the genotype onto the expressed phenotype map (variant genes only). */
    public static void recomputeExpressed(TownsteadVillager.Life life) {
        Genotype genotype = life.genotype();
        for (ResourceLocation locus : genotype.loci()) {
            Allele[] pair = genotype.at(locus);
            if (pair == null) continue;
            Allele expressed = express(pair[0], pair[1]);
            if (expressed.isWild() || expressed.variantId() == null) continue;
            Gene gene = GeneRegistry.byId(expressed.geneId());
            if (gene != null && gene.hasVariants()) {
                life.setCarriedVariant(gene.id().toString(), expressed.variantId());
            }
        }
    }

    // --- internals -------------------------------------------------------------

    private static Allele draw(Genotype parent, ResourceLocation locus, RandomSource random) {
        Allele[] pair = parent.at(locus);
        if (pair == null) return Allele.WILD;
        return pair[random.nextInt(2)];
    }

    /** One admixed allele: pick a contributor by fraction, take one of its alleles at the locus. */
    private static Allele drawAdmixed(List<Genotype> genotypes, float[] fractions,
                                      ResourceLocation locus, RandomSource random) {
        return draw(genotypes.get(weightedIndex(fractions, random)), locus, random);
    }

    private static int weightedIndex(float[] weights, RandomSource random) {
        float total = 0f;
        for (float w : weights) total += Math.max(0f, w);
        if (total <= 0f) return 0;
        float roll = random.nextFloat() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= Math.max(0f, weights[i]);
            if (roll < 0f) return i;
        }
        return weights.length - 1;
    }

    private static String dominantOrigin(List<OriginSelector.Weighted> mix) {
        OriginSelector.Weighted best = mix.get(0);
        for (OriginSelector.Weighted w : mix) if (w.fraction() > best.fraction()) best = w;
        return best.originId().toString();
    }

    private static Allele rollAllele(Gene gene, InheritedGene ref, RandomSource random) {
        if (gene.hasVariants()) {
            GeneVariant variant = rollVariant(gene, random);
            return Allele.of(gene.id(), variant.id());
        }
        float occurrence = ref.occurrence();
        if (occurrence >= 1f || random.nextFloat() < occurrence) {
            return Allele.of(gene.id(), null);
        }
        return Allele.WILD;
    }

    private static GeneVariant rollVariant(Gene gene, RandomSource random) {
        List<GeneVariant> variants = gene.variants();
        int total = 0;
        for (GeneVariant v : variants) total += Math.max(0, v.weight());
        if (total <= 0) return variants.get(0);
        int roll = random.nextInt(total);
        for (GeneVariant v : variants) {
            roll -= Math.max(0, v.weight());
            if (roll < 0) return v;
        }
        return variants.get(variants.size() - 1);
    }

    /**
     * The expressed allele at a locus. A wild allele always loses; between two real
     * alleles, a dominant gene beats a recessive one. On a tie, two variants of the
     * same gene resolve to the heavier-weighted (common-dominant) variant, and two
     * different genes at one locus resolve to the heavier gene; remaining ties break
     * deterministically by id so expression is stable.
     */
    public static Allele express(Allele a, Allele b) {
        if (a.isWild() && b.isWild()) return Allele.WILD;
        if (a.isWild()) return b;
        if (b.isWild()) return a;

        Gene ga = GeneRegistry.byId(a.geneId());
        Gene gb = GeneRegistry.byId(b.geneId());
        if (ga == null) return b;
        if (gb == null) return a;

        boolean aDominant = ga.dominance() == com.aetherianartificer.townstead.origin.gene.Dominance.DOMINANT;
        boolean bDominant = gb.dominance() == com.aetherianartificer.townstead.origin.gene.Dominance.DOMINANT;
        if (aDominant != bDominant) return aDominant ? a : b;

        if (a.geneId().equals(b.geneId())) {
            int wa = variantWeight(ga, a.variantId());
            int wb = variantWeight(gb, b.variantId());
            if (wa != wb) return wa > wb ? a : b;
            String va = a.variantId() == null ? "" : a.variantId();
            String vb = b.variantId() == null ? "" : b.variantId();
            return va.compareTo(vb) <= 0 ? a : b;
        }
        if (ga.weight() != gb.weight()) return ga.weight() > gb.weight() ? a : b;
        return a.geneId().compareTo(b.geneId()) <= 0 ? a : b;
    }

    private static int variantWeight(Gene gene, String variantId) {
        if (variantId == null) return gene.weight();
        for (GeneVariant v : gene.variants()) {
            if (v.id().equals(variantId)) return v.weight();
        }
        return 1;
    }

    /** Give the child the founder origin of whichever parent shares its dominant ancestry (mother on a tie). */
    private static String chooseChildOrigin(Heritage childHeritage, String motherOrigin, String fatherOrigin) {
        ResourceLocation dominant = childHeritage.dominant();
        if (dominant != null) {
            ResourceLocation fatherId = ResourceLocation.tryParse(fatherOrigin);
            if (fatherId != null && dominant.equals(OriginRegistry.seedHeritage(fatherId).dominant())) {
                ResourceLocation motherId = ResourceLocation.tryParse(motherOrigin);
                if (motherId == null || !dominant.equals(OriginRegistry.seedHeritage(motherId).dominant())) {
                    return fatherOrigin == null ? "" : fatherOrigin;
                }
            }
        }
        return motherOrigin == null ? "" : motherOrigin;
    }
}
