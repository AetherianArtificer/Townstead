package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.gene.GeneVariant;
import com.aetherianartificer.townstead.root.gene.Genotype;
import com.aetherianartificer.townstead.root.gene.InheritedGene;
import com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType;
import com.aetherianartificer.townstead.root.gene.types.BodyMetricGeneType;
import com.aetherianartificer.townstead.root.gene.types.LifeCycleGeneType;
import com.aetherianartificer.townstead.root.gene.types.TraitOccurrenceGeneType;
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
                && !(instance instanceof com.aetherianartificer.townstead.root.gene.types.ProportionsGeneType.Instance)
                && !(instance instanceof TraitOccurrenceGeneType.Instance)
                && !(instance instanceof LifeCycleGeneType.Instance);
    }

    /** Roll a fresh founder: two independent alleles per diploid gene the origin grants. */
    public static void seedFounder(TownsteadVillager.Life life, ResourceLocation rootId, RandomSource random) {
        life.setGenotype(seedGenotype(rootId, random));
        life.setHeritage(RootRegistry.seedHeritage(rootId));
        recomputeExpressed(life);
    }

    /** A fresh diploid genotype rolled from an origin's grant list (two alleles per diploid gene). */
    public static Genotype seedGenotype(ResourceLocation rootId, RandomSource random) {
        Genotype genotype = new Genotype();
        for (InheritedGene ref : RootRegistry.effectiveInheritedGenes(rootId)) {
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
                                        List<RootSelector.Weighted> mix, RandomSource random) {
        if (mix.size() < 2) {
            ResourceLocation only = mix.isEmpty() ? RootRegistry.DEFAULT_ID
                    : ResourceLocation.tryParse(mix.get(0).rootId().toString());
            seedFounder(life, only == null ? RootRegistry.DEFAULT_ID : only, random);
            return;
        }

        java.util.List<Genotype> genotypes = new java.util.ArrayList<>(mix.size());
        float[] fractions = new float[mix.size()];
        java.util.Map<ResourceLocation, Float> heritage = new java.util.LinkedHashMap<>();
        for (int i = 0; i < mix.size(); i++) {
            RootSelector.Weighted w = mix.get(i);
            fractions[i] = w.fraction();
            genotypes.add(seedGenotype(w.rootId(), random));
            RootRegistry.seedHeritage(w.rootId()).fractions()
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
        life.setRoot(dominantRoot(mix));
        recomputeExpressed(life);
    }

    /**
     * Fill in any diploid genes a loaded villager lacks without disturbing what it
     * already carries: an existing locus is kept; a legacy expressed variant becomes
     * a homozygous pair; everything else is rolled. Self-heals saves from before the
     * genotype existed.
     */
    public static void migrateFounder(TownsteadVillager.Life life, ResourceLocation rootId, RandomSource random) {
        Genotype genotype = life.genotype();
        canonicalizeLoci(genotype);
        for (InheritedGene ref : RootRegistry.effectiveInheritedGenes(rootId)) {
            Gene gene = GeneRegistry.byId(ref.geneId());
            if (gene == null || !isDiploid(gene)) continue;
            ResourceLocation locus = locusOf(gene);
            if (genotype.has(locus)) {
                healSizedPayloads(genotype, locus, gene, random);
                continue;
            }
            if (gene.hasVariants() && life.hasCarriedVariant(gene.id().toString())) {
                Allele legacy = Allele.of(gene.id(), life.carriedVariant(gene.id().toString()));
                genotype.set(locus, legacy, legacy);
            } else {
                genotype.set(locus, rollAllele(gene, ref, random), rollAllele(gene, ref, random));
            }
        }
        life.setGenotype(genotype);
        if (!life.hasHeritage()) life.setHeritage(RootRegistry.seedHeritage(rootId));
        recomputeExpressed(life);
    }

    /**
     * Relocate stale private-slot entries into the shared locus their gene now declares.
     * A save written before a gene gained a {@code locus} keys the pair by the gene's own
     * id; left alone, that entry would keep expressing beside a freshly rolled pair at the
     * shared slot (double ears). When two stale pairs land on one slot (an old hybrid
     * carrying both parents' morphs), one non-wild allele is kept from each, collapsing
     * them into the true heterozygous pair the shared locus would have produced at birth.
     * Returns whether anything moved.
     */
    public static boolean canonicalizeLoci(Genotype genotype) {
        if (genotype == null || genotype.isEmpty()) return false;
        boolean changed = false;
        for (ResourceLocation key : genotype.loci()) {
            Allele[] stale = genotype.at(key);
            Allele a = canonicalAllele(stale[0]);
            Allele b = canonicalAllele(stale[1]);
            Gene gene = GeneRegistry.byId(key);
            if (gene == null) {
                // Shared loci are not themselves gene ids. Their alleles still need their old
                // flat ids rewritten after the source files move into organizational folders.
                if (a != stale[0] || b != stale[1]) {
                    genotype.set(key, a, b);
                    changed = true;
                }
                continue;
            }
            // Private loci used to be keyed by the flat id. If the backing file moves into an
            // organizational folder, relocate that saved pair to the canonical nested id too.
            ResourceLocation target = gene.locus() != null
                    ? LegacyNamespace.canonical(gene.locus()) : gene.id();
            if (target.equals(key)) {
                if (a != stale[0] || b != stale[1]) {
                    genotype.set(key, a, b);
                    changed = true;
                }
                continue;
            }
            genotype.remove(key);
            Allele[] existing = genotype.at(target);
            if (existing == null) {
                genotype.set(target, a, b);
            } else {
                Allele existingA = canonicalAllele(existing[0]);
                Allele existingB = canonicalAllele(existing[1]);
                genotype.set(target, firstNonWild(new Allele[]{existingA, existingB}),
                        firstNonWild(new Allele[]{a, b}));
            }
            changed = true;
        }
        return changed;
    }

    private static Allele canonicalAllele(Allele allele) {
        if (allele == null || allele.isWild()) return allele;
        ResourceLocation canonical = GeneRegistry.canonicalId(allele.geneId());
        return canonical == null || canonical.equals(allele.geneId())
                ? allele : Allele.of(canonical, allele.variantId());
    }

    private static Allele firstNonWild(Allele[] pair) {
        return !pair[0].isWild() ? pair[0] : pair[1];
    }

    /**
     * A parent's contribution to a child: its origin (for the child's life-cycle
     * template), heritage vector, and genotype to draw an allele from. Derived from
     * a villager's stored state or, for a player/other parent, seeded from its origin.
     */
    public record Parent(String rootId, Heritage heritage, Genotype genotype) {}

    /**
     * Resolve any parent entity to a gamete source. A villager uses its stored
     * (seeded) state; a player uses its {@link PlayerRoot} (default origin if it
     * has none), with a genotype freshly rolled from that origin since players carry
     * no diploid genotype of their own. {@code null} for entities that can't parent.
     */
    @Nullable
    public static Parent parentOf(Entity entity, RandomSource random) {
        if (entity instanceof VillagerEntityMCA villager) {
            TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
            ensureSeeded(life, random);
            return new Parent(life.rootId(), life.heritage(), life.genotype());
        }
        if (entity instanceof Player player) {
            ResourceLocation rootId = ResourceLocation.tryParse(PlayerRoot.getRootId(player));
            if (rootId == null) rootId = RootRegistry.DEFAULT_ID;
            return new Parent(rootId.toString(), RootRegistry.seedHeritage(rootId),
                    PlayerRoot.getOrSeedGenotype(player, random));
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
        child.setRoot(chooseChildRoot(child.heritage(), mother.rootId(), father.rootId()));
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
        ResourceLocation rootId = ResourceLocation.tryParse(life.rootId());
        if (rootId == null) rootId = RootRegistry.DEFAULT_ID;
        migrateFounder(life, rootId, random);
    }

    /**
     * The expressed (dominant-resolved) non-wild allele at each locus of a genotype.
     * Generalizes {@link #recomputeExpressed} beyond variant genes, so single-variant
     * ability/attribute/attachment genes surface too. Used by the ability ticker, the
     * attribute applier and the per-entity render sync.
     */
    public static List<Allele> expressedAlleles(Genotype genotype) {
        List<Allele> out = new java.util.ArrayList<>();
        if (genotype == null) return out;
        for (ResourceLocation locus : genotype.loci()) {
            Allele[] pair = genotype.at(locus);
            if (pair == null) continue;
            Allele expressed = express(pair[0], pair[1]);
            if (!expressed.isWild()) out.add(expressed);
        }
        return out;
    }

    public static List<Allele> expressedAlleles(TownsteadVillager.Life life) {
        return life == null ? List.of() : expressedAlleles(life.genotype());
    }

    /** The genes a genotype expresses, resolved from the registry (skips unknown ids). */
    public static List<Gene> expressedGenes(Genotype genotype) {
        List<Gene> out = new java.util.ArrayList<>();
        for (Allele allele : expressedAlleles(genotype)) {
            Gene gene = GeneRegistry.byId(allele.geneId());
            if (gene != null) out.add(gene);
        }
        return out;
    }

    /**
     * Project the genotype onto the expressed phenotype: the variant-gene map (skin tint, chronotype,
     * etc.) plus the full expressed-allele encoding list (every locus, persisted so a reconstructed
     * entity can render its real genetics without the live sync).
     */
    public static void recomputeExpressed(TownsteadVillager.Life life) {
        Genotype genotype = life.genotype();
        Heritage heritage = life.hasHeritage() ? life.heritage() : null;
        List<String> encodings = new java.util.ArrayList<>();
        for (Allele raw : expressedAlleles(genotype)) {
            Allele allele = scaleByHeritage(raw, heritage);
            encodings.add(allele.encode());
            if (allele.variantId() == null) continue;
            Gene gene = GeneRegistry.byId(allele.geneId());
            if (gene != null && gene.hasVariants()) {
                life.setCarriedVariant(gene.id().toString(), allele.variantId());
            }
        }
        life.setExpressedAlleles(encodings);
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

    private static String dominantRoot(List<RootSelector.Weighted> mix) {
        RootSelector.Weighted best = mix.get(0);
        for (RootSelector.Weighted w : mix) if (w.fraction() > best.fraction()) best = w;
        return best.rootId().toString();
    }

    /**
     * A freshly rolled allele for granting {@code gene} outright (the authoring
     * command path): a rolled variant for variant genes, rolled size channels for
     * sized attachment genes (per-variant channels when both apply), else a plain
     * present allele.
     */
    public static Allele grantAllele(Gene gene, RandomSource random) {
        if (gene.hasVariants()) {
            GeneVariant variant = rollVariant(gene, random);
            return Allele.of(gene.id(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                    variant.id(), rollChannels(channelsOf(variant.instance()),
                            paletteOf(variant.instance()), random)));
        }
        java.util.List<AttachmentGeneType.Channel> channels = channelsOf(gene);
        if (!channels.isEmpty()) {
            return Allele.of(gene.id(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                    "", rollChannels(channels, paletteOf(gene.instance()), random)));
        }
        return Allele.of(gene.id(), null);
    }

    private static Allele rollAllele(Gene gene, InheritedGene ref, RandomSource random) {
        if (gene.hasVariants()) {
            GeneVariant variant = rollVariant(gene, random);
            return Allele.of(gene.id(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                    variant.id(), rollChannels(channelsOf(variant.instance()),
                            paletteOf(variant.instance()), random)));
        }
        float occurrence = ref.occurrence();
        if (occurrence >= 1f || random.nextFloat() < occurrence) {
            java.util.List<AttachmentGeneType.Channel> channels = channelsOf(gene);
            if (!channels.isEmpty()) {
                return Allele.of(gene.id(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                        "", rollChannels(channels, paletteOf(gene.instance()), random)));
            }
            return Allele.of(gene.id(), null);
        }
        return Allele.WILD;
    }

    /** The gene's heritable size channels (empty when it carries none). */
    private static java.util.List<AttachmentGeneType.Channel> channelsOf(@Nullable Gene gene) {
        return gene == null ? java.util.List.of() : channelsOf(gene.instance());
    }

    private static java.util.List<AttachmentGeneType.Channel> channelsOf(
            @Nullable com.aetherianartificer.townstead.root.gene.GeneInstance instance) {
        return instance instanceof AttachmentGeneType.Instance att ? att.channels() : java.util.List.of();
    }

    /**
     * The size channels behind an allele: the gene's own, or — for a variant gene
     * whose options are attachments — the carried variant's.
     */
    private static java.util.List<AttachmentGeneType.Channel> channelsFor(@Nullable Gene gene, String variantId) {
        if (gene == null) return java.util.List.of();
        if (gene.hasVariants() && !variantId.isEmpty()) {
            for (GeneVariant variant : gene.variants()) {
                if (variant.id().equals(variantId)) return channelsOf(variant.instance());
            }
        }
        return channelsOf(gene);
    }

    /** An instance's heritable-tint preset colours (empty when its colour isn't heritable). */
    private static java.util.List<Integer> paletteOf(
            @Nullable com.aetherianartificer.townstead.root.gene.GeneInstance instance) {
        return instance instanceof AttachmentGeneType.Instance att ? att.palette() : java.util.List.of();
    }

    /** The tint palette behind an allele (the carried variant's, else the gene's). */
    private static java.util.List<Integer> paletteFor(@Nullable Gene gene, String variantId) {
        if (gene == null) return java.util.List.of();
        if (gene.hasVariants() && !variantId.isEmpty()) {
            for (GeneVariant variant : gene.variants()) {
                if (variant.id().equals(variantId)) return paletteOf(variant.instance());
            }
        }
        return paletteOf(gene.instance());
    }

    /**
     * Fresh rolls for every channel: size channels roll uniformly in their range;
     * the reserved tint channels roll ONE palette colour together (plus a little
     * per-component jitter, so littermates aren't clones), since three independent
     * uniform components would be mud, not a coat colour.
     */
    private static java.util.Map<String, Float> rollChannels(
            java.util.List<AttachmentGeneType.Channel> channels,
            java.util.List<Integer> palette, RandomSource random) {
        if (channels.isEmpty()) return java.util.Map.of();
        java.util.Map<String, Float> out = new java.util.LinkedHashMap<>();
        int tint = -1;
        for (AttachmentGeneType.Channel channel : channels) {
            if (AttachmentGeneType.isTintChannel(channel.name())) {
                if (tint < 0) tint = pickTint(palette, random);
                out.put(channel.name(), tintComponent(tint, channel.name(), random));
            } else {
                out.put(channel.name(), channel.min() + random.nextFloat() * (channel.max() - channel.min()));
            }
        }
        return out;
    }

    private static int pickTint(java.util.List<Integer> palette, RandomSource random) {
        return palette.isEmpty() ? 0xFFFFFF : palette.get(random.nextInt(palette.size()));
    }

    private static float tintComponent(int color, String channel, RandomSource random) {
        int shift = AttachmentGeneType.TINT_R.equals(channel) ? 16
                : AttachmentGeneType.TINT_G.equals(channel) ? 8 : 0;
        float base = ((color >> shift) & 0xFF) / 255f;
        float jitter = (random.nextFloat() - 0.5f) * 0.06f;
        return Math.max(0f, Math.min(1f, base + jitter));
    }

    /**
     * Self-heal an outdated save: alleles rolled before sizes (or before a channel
     * was added to the gene) carry no value for it, so roll the missing channels
     * now; a legacy anonymous single-value roll is renamed to the gene's first
     * declared channel. Runs on the migrate path, so a loaded villager picks up
     * its individual rolls once and keeps them.
     */
    private static void healSizedPayloads(Genotype genotype, ResourceLocation locus, Gene gene,
                                          RandomSource random) {
        Allele[] pair = genotype.at(locus);
        if (pair == null) return;
        Allele a = healSized(pair[0], gene, random);
        Allele b = healSized(pair[1], gene, random);
        if (a != pair[0] || b != pair[1]) genotype.set(locus, a, b);
    }

    private static Allele healSized(Allele allele, Gene gene, RandomSource random) {
        if (allele.isWild() || !gene.id().equals(allele.geneId())) return allele;
        var payload = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(allele.variantId());
        java.util.List<AttachmentGeneType.Channel> channels = channelsFor(gene, payload.variant());
        if (channels.isEmpty()) return allele;
        java.util.Map<String, Float> healed = new java.util.LinkedHashMap<>(payload.channels());
        Float anonymous = healed.remove(com.aetherianartificer.townstead.root.gene.AllelePayload.LEGACY_CHANNEL);
        if (anonymous != null && !channels.get(0).name().isEmpty()) {
            healed.putIfAbsent(channels.get(0).name(), anonymous);
        } else if (anonymous != null) {
            healed.put("", anonymous);
        }
        boolean changed = anonymous != null && !channels.get(0).name().isEmpty();
        java.util.List<Integer> palette = paletteFor(gene, payload.variant());
        int tint = -1;
        for (AttachmentGeneType.Channel channel : channels) {
            if (!healed.containsKey(channel.name())) {
                if (AttachmentGeneType.isTintChannel(channel.name())) {
                    if (tint < 0) tint = pickTint(palette, random);
                    healed.put(channel.name(), tintComponent(tint, channel.name(), random));
                } else {
                    healed.put(channel.name(), channel.min() + random.nextFloat() * (channel.max() - channel.min()));
                }
                changed = true;
            }
        }
        if (!changed) return allele;
        return Allele.of(gene.id(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                payload.variant(), healed));
    }

    /**
     * Fold a bearer's heritage into a sized attachment allele: each channel's rolled
     * value is multiplied by that channel's heritage factor (share of the coupled
     * ancestry mapped onto [floor, 1]), so a half-elf expresses smaller elven ears
     * than a full elf. The genotype keeps the raw rolls; only the expressed
     * projection is scaled. Anything without heritage-coupled channels passes through.
     */
    public static Allele scaleByHeritage(Allele allele, @Nullable Heritage heritage) {
        if (allele.isWild()) return allele;
        Gene gene = GeneRegistry.byId(allele.geneId());
        var payload = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(allele.variantId());
        java.util.List<AttachmentGeneType.Channel> channels = channelsFor(gene, payload.variant());
        if (channels.isEmpty() || payload.channels().isEmpty()) return allele;
        boolean scaled = false;
        java.util.Map<String, Float> out = new java.util.LinkedHashMap<>(payload.channels());
        for (AttachmentGeneType.Channel channel : channels) {
            if (channel.heritageAncestry() == null) continue;
            String key = out.containsKey(channel.name()) ? channel.name()
                    : (out.size() == 1 && out.containsKey("") ? "" : null);
            if (key == null) continue;
            float fraction = heritage == null ? 1f : heritage.fractionOf(channel.heritageAncestry());
            out.put(key, out.get(key) * channel.heritageFactor(fraction));
            scaled = true;
        }
        if (!scaled) return allele;
        return Allele.of(allele.geneId(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                payload.variant(), out));
    }

    /**
     * The inverse of {@link #scaleByHeritage}: recover the raw roll from an
     * expressed-space value. The editor's sliders read and preview the expressed
     * (heritage-scaled) payload, so its commits arrive in expressed space; storing
     * them verbatim would re-apply the factor on expression, shrinking a hybrid's
     * heritage-coupled channels a little more on every edit round trip. The raw
     * value is clamped back into the channel's declared range.
     */
    public static Allele unscaleByHeritage(Allele allele, @Nullable Heritage heritage) {
        if (allele.isWild()) return allele;
        Gene gene = GeneRegistry.byId(allele.geneId());
        var payload = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(allele.variantId());
        java.util.List<AttachmentGeneType.Channel> channels = channelsFor(gene, payload.variant());
        if (channels.isEmpty() || payload.channels().isEmpty()) return allele;
        boolean changed = false;
        java.util.Map<String, Float> out = new java.util.LinkedHashMap<>(payload.channels());
        for (AttachmentGeneType.Channel channel : channels) {
            if (channel.heritageAncestry() == null) continue;
            String key = out.containsKey(channel.name()) ? channel.name()
                    : (out.size() == 1 && out.containsKey("") ? "" : null);
            if (key == null) continue;
            float fraction = heritage == null ? 1f : heritage.fractionOf(channel.heritageAncestry());
            float factor = channel.heritageFactor(fraction);
            if (factor <= 0f || factor == 1f) continue;
            out.put(key, Math.max(channel.min(), Math.min(channel.max(), out.get(key) / factor)));
            changed = true;
        }
        if (!changed) return allele;
        return Allele.of(allele.geneId(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                payload.variant(), out));
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

        boolean aDominant = ga.dominance() == com.aetherianartificer.townstead.root.gene.Dominance.DOMINANT;
        boolean bDominant = gb.dominance() == com.aetherianartificer.townstead.root.gene.Dominance.DOMINANT;
        if (aDominant != bDominant) return aDominant ? a : b;

        if (a.geneId().equals(b.geneId())) {
            // Size channels are quantitative traits: when the two carried copies agree
            // on the variant (or carry none), each channel expresses the mean of the two
            // rolls rather than one masking the other, so ear size blends down the
            // generations instead of snapping between the parents' rolls. Copies carrying
            // different variants (two tail styles) stay Mendelian: the winner's channel
            // rolls ride along unblended, since channels are per-variant.
            var pa = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(a.variantId());
            var pb = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(b.variantId());
            if (pa.variant().equals(pb.variant()) && pa.hasChannels() && pb.hasChannels()) {
                java.util.Map<String, Float> mean = new java.util.LinkedHashMap<>(pa.channels());
                pb.channels().forEach((name, value) ->
                        mean.merge(name, value, (x, y) -> (x + y) / 2f));
                return Allele.of(a.geneId(), com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                        pa.variant(), mean));
            }
            int wa = variantWeight(ga, pa.variant());
            int wb = variantWeight(gb, pb.variant());
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
    private static String chooseChildRoot(Heritage childHeritage, String motherRoot, String fatherRoot) {
        ResourceLocation dominant = childHeritage.dominant();
        if (dominant != null) {
            ResourceLocation fatherId = ResourceLocation.tryParse(fatherRoot);
            if (fatherId != null && dominant.equals(RootRegistry.seedHeritage(fatherId).dominant())) {
                ResourceLocation motherId = ResourceLocation.tryParse(motherRoot);
                if (motherId == null || !dominant.equals(RootRegistry.seedHeritage(motherId).dominant())) {
                    return fatherRoot == null ? "" : fatherRoot;
                }
            }
        }
        return motherRoot == null ? "" : motherRoot;
    }
}
