package com.aetherianartificer.townstead.root.gene;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.Heredity;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Evaluates standard Pheno conditions for gene types whose entire presence is conditional. */
public final class GeneExpression {
    private static volatile Map<ResourceLocation, Condition> conditions = Map.of();
    // A condition querying powers must not recursively evaluate itself.
    private static final ThreadLocal<Boolean> evaluating = ThreadLocal.withInitial(() -> false);

    private GeneExpression() {}

    public static Condition parse(JsonObject gene, GeneType type) {
        // Existing behavior types own their conditions (often with event-specific context).
        // Do not evaluate those twice or change when they roll chance conditions.
        if (!type.conditionControlsExpression() || !gene.has("condition")) return Conditions.ALWAYS;
        Condition parsed = Conditions.parse(PhenoNormalizer.normalizeCondition(gene.getAsJsonObject("condition")));
        if (parsed == null) throw new IllegalArgumentException("Invalid gene condition");
        return parsed;
    }

    static void replaceAll(Map<ResourceLocation, Condition> next) { conditions = Map.copyOf(next); }

    public static Allele resolve(Allele allele, ConditionContext context) {
        ResourceLocation source = GeneRegistry.canonicalId(allele.geneId());
        Condition condition = source == null ? null : conditions.get(source);
        return condition == null || condition.test(context) ? allele : Allele.WILD;
    }

    /** Editing a derived style must update its inherited source rather than storing a companion allele. */
    public static Gene variantEditSource(Gene requested, List<Allele> inherited, String variant) {
        if (!GeneRegistry.isCompanion(requested.id())) return requested;
        if (requested.locus() == null || !requested.hasVariants()) return null;
        for (Allele allele : inherited) {
            Gene source = GeneRegistry.byId(allele.geneId());
            if (source == null || GeneRegistry.isCompanion(source.id())
                    || !requested.locus().equals(source.locus())) continue;
            if (source.variants().stream().anyMatch(v -> v.id().equals(variant))) return source;
        }
        // Never replace an inherited allele with a derived companion just to edit its style.
        return null;
    }

    /** A companion shares the carried style at its declared locus, even when that gene is dormant. */
    static Allele companionAllele(ResourceLocation id, List<Allele> inherited) {
        Gene companion = GeneRegistry.byId(id);
        if (companion != null && companion.hasVariants() && companion.locus() != null) {
            for (Allele allele : inherited) {
                Gene source = GeneRegistry.byId(allele.geneId());
                if (source == null || !companion.locus().equals(source.locus())) continue;
                String variant = AllelePayload.parse(allele.variantId()).variant();
                if (companion.variants().stream().anyMatch(v -> v.id().equals(variant)))
                    return Allele.of(id, allele.variantId());
            }
        }
        return Allele.of(id, null);
    }

    /** Parent conditions gate the complete companion bundle; children may add their own conditions. */
    static List<Allele> activeAlleles(List<Allele> inherited, ConditionContext context) {
        List<Allele> active = new ArrayList<>();
        for (Allele allele : inherited) {
            Allele expressed = resolve(allele, context);
            if (expressed.isWild()) continue;
            if (!active.contains(expressed)) active.add(expressed);
            for (ResourceLocation companion : GeneRegistry.companionsOf(expressed.geneId())) {
                Allele child = resolve(companionAllele(companion, inherited), context);
                if (!child.isWild() && !active.contains(child)) active.add(child);
            }
        }
        return List.copyOf(active);
    }

    public static List<Allele> activeAlleles(LivingEntity entity) {
        Genotype genotype = ExpressedGenes.genotypeOf(entity);
        // A villager's expression holds for the rest of the tick unless its genes or states change.
        boolean memoize = !evaluating.get() && entity instanceof net.conczin.mca.entity.VillagerEntityMCA
                && !entity.level().isClientSide;
        long now = memoize ? entity.level().getGameTime() : 0;
        if (memoize) {
            synchronized (MEMO) {
                Memo memo = MEMO.get(entity);
                if (memo != null && memo.time() == now && memo.genotype() == genotype
                        && memo.revision() == genotype.revision()) {
                    return memo.alleles();
                }
            }
        }
        List<Allele> inherited = AspectOverlay.apply(entity, Heredity.expressedAlleles(genotype));
        if (evaluating.get()) return inherited;
        evaluating.set(true);
        List<Allele> active;
        try {
            active = activeAlleles(inherited, new ConditionContext(entity));
        } finally {
            evaluating.remove();
        }
        if (memoize) {
            active = List.copyOf(active);
            synchronized (MEMO) {
                MEMO.put(entity, new Memo(now, genotype, genotype.revision(), active));
            }
        }
        return active;
    }

    /** Forget a cached expression, after something it depends on changed. */
    public static void invalidate(LivingEntity entity) {
        synchronized (MEMO) {
            MEMO.remove(entity);
        }
    }

    private record Memo(long time, Genotype genotype, int revision, List<Allele> alleles) {}

    private static final Map<LivingEntity, Memo> MEMO = new java.util.WeakHashMap<>();
}
