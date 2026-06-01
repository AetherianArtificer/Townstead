package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import com.aetherianartificer.townstead.origin.gene.types.LifeCycleGeneType;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;

/**
 * Applies origins to villagers at runtime.
 *
 * <p>{@link #onTrueSpawn} runs only for genuinely spawned villagers (driven by
 * the FinalizeSpawn event), so it is safe to roll genes and life-stage durations
 * there. Loaded/legacy villagers are handled by {@link #backfillIfMissing}, which
 * assigns the default origin id and rolls stage durations once without touching
 * already-rolled genes.</p>
 */
public final class OriginSpawnHandler {

    private OriginSpawnHandler() {}

    /** Choose the villager's origin, stamp the id, and constrain genes into its ranges. */
    public static void onTrueSpawn(VillagerEntityMCA villager) {
        // Origin choice is fixed to the default for now; per-world/village biases
        // will resolve the origin id here in a later phase.
        ResourceLocation originId = OriginRegistry.DEFAULT_ID;
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.life().setOrigin(originId.toString());
        OriginGenes.clamp(villager, OriginRegistry.effectiveGenome(originId));
        rollTraitGenes(villager, state, originId);
        rollVariantGenes(villager, state, originId);
        rollAndStoreStageDays(villager, state, originId);
    }

    /**
     * Assign the default origin id to a villager that has none, leaving genes
     * untouched. Returns {@code true} if stage durations were (re-)rolled, so the
     * caller can re-broadcast the life sync.
     */
    public static boolean backfillIfMissing(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        if (!state.life().hasOrigin()) {
            state.life().setOrigin(OriginRegistry.DEFAULT_ID.toString());
        }
        ResourceLocation originId = ResourceLocation.tryParse(state.life().originId());
        if (originId == null) originId = OriginRegistry.DEFAULT_ID;
        // Legacy villagers predate the variant-gene system: roll any chronotype (or other
        // variant gene) they're missing once, on load, so expression is purely gene-driven.
        rollVariantGenes(villager, state, originId);
        LifeCycle cycle = OriginRegistry.effectiveLifeCycle(originId);
        // Re-roll when the stored stageDays don't match the current cycle — either a
        // different length (origin reassigned), a re-authored shape, or a changed
        // aging mode/scale (fingerprint folds the scale in). 0 on pre-fingerprint
        // villagers also forces a re-roll. Self-heals existing saves.
        if (state.life().stageDaysLength() != cycle.size()
                || state.life().cycleFingerprint() != townstead$fingerprint(villager, cycle)) {
            rollAndStoreStageDays(villager, state, originId);
            return true;
        }
        return false;
    }

    /**
     * Roll each trait-granting gene the origin expresses once at spawn and grant the matching
     * MCA trait. A gene's occurrence ({@code force}/{@code delta}) is the spawn probability;
     * ≥1.0 is guaranteed. MCA owns membership (persisted, synced, heritable); Townstead's effect
     * data rides alongside via {@link com.aetherianartificer.townstead.origin.trait.TraitRegistry}.
     */
    private static void rollTraitGenes(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation originId) {
        for (com.aetherianartificer.townstead.origin.gene.types.TraitOccurrenceGeneType.Instance gene
                : OriginRegistry.traitGenes(originId)) {
            net.conczin.mca.entity.ai.Traits.Trait trait =
                    net.conczin.mca.entity.ai.Traits.Trait.valueOf(gene.trait());
            if (trait == net.conczin.mca.entity.ai.Traits.UNKNOWN) continue; // not a registered trait
            if (gene.delta() >= 1.0f || villager.getRandom().nextFloat() < gene.delta()) {
                villager.getTraits().addTrait(trait);
            }
        }
    }

    /**
     * Roll each variant gene (a weighted pick-one) the origin expresses, storing the winning
     * variant id, but only where one isn't already carried — so this is safe to call both at
     * spawn (rolls all) and on load to backfill legacy villagers (fills the gaps). The grant
     * list is locus-collapsed, so at a shared slot (e.g. the chronotype locus) only the most
     * specific gene is rolled. The carried variant is the genotype; expression (sleep window, …)
     * is derived from it on read.
     */
    private static void rollVariantGenes(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation originId) {
        for (InheritedGene inherited : OriginRegistry.effectiveInheritedGenes(originId)) {
            Gene gene = GeneRegistry.byId(inherited.geneId());
            if (gene == null || !gene.hasVariants()) continue;
            if (state.life().hasCarriedVariant(gene.id().toString())) continue;
            com.aetherianartificer.townstead.origin.gene.GeneVariant chosen =
                    rollVariant(gene, villager.getRandom());
            if (chosen != null) {
                state.life().setCarriedVariant(gene.id().toString(), chosen.id());
            }
        }
    }

    @org.jetbrains.annotations.Nullable
    private static com.aetherianartificer.townstead.origin.gene.GeneVariant rollVariant(
            Gene gene, net.minecraft.util.RandomSource random) {
        java.util.List<com.aetherianartificer.townstead.origin.gene.GeneVariant> variants = gene.variants();
        int total = 0;
        for (com.aetherianartificer.townstead.origin.gene.GeneVariant v : variants) total += Math.max(0, v.weight());
        if (total <= 0) return variants.get(0);
        int roll = random.nextInt(total);
        for (com.aetherianartificer.townstead.origin.gene.GeneVariant v : variants) {
            roll -= Math.max(0, v.weight());
            if (roll < 0) return v;
        }
        return variants.get(variants.size() - 1);
    }

    private static void rollAndStoreStageDays(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation originId) {
        LifeCycleGeneType.Instance gene = OriginRegistry.effectiveCycleGene(originId);
        LifeCycle cycle = gene == null || gene.cycle().isEmpty() ? LifeCycle.defaultHumanLike() : gene.cycle();
        float variance = gene == null ? 0f : gene.variance();
        int[] days = LifeStageRoller.roll(cycle, variance, townstead$agingScale(villager), villager.getRandom());
        state.life().setStageDays(days);
        state.life().setCycleFingerprint(townstead$fingerprint(villager, cycle));
    }

    /** Cycle shape hash folded with the active aging scale, so toggling brisk/lifelike re-rolls. */
    private static int townstead$fingerprint(VillagerEntityMCA villager, LifeCycle cycle) {
        return cycle.fingerprint() * 31 + Float.floatToIntBits(townstead$agingScale(villager));
    }

    /**
     * Game-days per narrative year — the species-neutral aging rate. A cycle authors its
     * stage days as narrative-year spans, so {@code livedDays = span * agingScale}; a
     * longer-lived race just authors more narrative years and so lives more game-days at
     * the same rate. Always on (both aging modes share it; the mode only governs played vs
     * real-world coupling). The inverse, {@code narrativeAge = bioAgeDays / agingScale}, is
     * computed in {@link LifeStageProgression}.
     */
    public static float townstead$agingScale(VillagerEntityMCA villager) {
        net.minecraft.server.MinecraftServer server = villager.getServer();
        return agingScale(server);
    }

    /**
     * Server-scoped aging scale (no per-villager state); see {@link #townstead$agingScale}.
     * Species-neutral and calendar-independent: it is simply game-days per narrative year,
     * read straight from config. A cycle's stage days are authored as narrative-year spans,
     * so {@code livedDays = narrativeSpan * agingScale} and {@code narrativeAge = bioDays / agingScale}.
     */
    public static float agingScale(net.minecraft.server.MinecraftServer server) {
        return (float) Math.max(0.01, com.aetherianartificer.townstead.TownsteadConfig.getAgingScale());
    }
}
