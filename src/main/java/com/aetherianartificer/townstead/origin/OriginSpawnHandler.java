package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.types.LifeCycleGeneType;
import com.aetherianartificer.townstead.origin.personality.PersonalityResolver;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Choose the villager's origin, stamp the id, and roll its genes within the origin's ranges.
     * Driven by {@code VillagerFinalizeSpawnMixin} at the TAIL of MCA's {@code finalizeSpawn} —
     * after MCA's {@code initialize()} re-randomizes genetics/traits (so our writes aren't
     * clobbered) and before the villager's first tick (so {@code backfillIfMissing} can't stamp
     * the default origin first).
     */
    public static void onTrueSpawn(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);

        // A bred child already had its origin, heritage and genotype set by the breeding
        // hook before this fires. Keep all of that (its floats are MCA's parent blend);
        // only roll its stage durations against the inherited origin.
        if (state.life().hasGenotype() || state.life().hasHeritage()) {
            ResourceLocation childOrigin = ResourceLocation.tryParse(state.life().originId());
            if (childOrigin == null) childOrigin = OriginRegistry.DEFAULT_ID;
            assignPersonality(villager, state, childOrigin);
            rollAndStoreStageDays(villager, state, childOrigin);
            return;
        }

        // Founder: pick an origin weighted by the biome/dimension spawn bias, with a
        // per-species chance of a mixed-ancestry blend instead. A mix seeds its own
        // heritage + genotype; its body metrics, traits and life cycle are blended
        // from the contributors by share (the parentless analogue of MCA's blend).
        //
        // Inside an existing village, constrain the selection: drop origins whose disposition group
        // clashes with the resident majority (so a hostile species never spawns into a peaceful town)
        // and honor any spawner building's authored origin policy. Outside a village, no constraint.
        VillageSpawnContext village = VillageSpawnContext.resolve(villager);
        OriginSelector.Selection selection = village.active()
                ? OriginSelector.select(villager.level(), villager.blockPosition(), villager.getRandom(), village::allows)
                : OriginSelector.select(villager.level(), villager.blockPosition(), villager.getRandom());
        if (selection.isMixed()) {
            List<OriginSelector.Weighted> mix = selection.mix();
            Heredity.seedMixedFounder(state.life(), mix, villager.getRandom());
            ResourceLocation mixedOrigin = ResourceLocation.tryParse(state.life().originId());
            assignPersonality(villager, state, mixedOrigin == null ? OriginRegistry.DEFAULT_ID : mixedOrigin);
            OriginGenes.apply(villager, blendBodyMetrics(mix), villager.getRandom());
            rollBlendedTraitGenes(villager, mix);
            BlendedCycle blended = blendCycle(mix);
            if (blended != null) {
                rollAndStoreStageDays(villager, state, blended.cycle(), blended.variance());
            } else {
                ResourceLocation dominant = ResourceLocation.tryParse(state.life().originId());
                rollAndStoreStageDays(villager, state, dominant == null ? OriginRegistry.DEFAULT_ID : dominant);
            }
            return;
        }

        // Empty selection (everything filtered out in a village) falls back to the village's own
        // majority origin, which is compatible by construction; else the default.
        ResourceLocation originId = selection.single() != null ? selection.single()
                : village.fallbackOrigin() != null ? village.fallbackOrigin() : OriginRegistry.DEFAULT_ID;
        state.life().setOrigin(originId.toString());
        assignPersonality(villager, state, originId);
        // Founder: re-roll within the origin's ranges (not clamp). MCA's centeredRandom roll sits at
        // ~0.5, so clamping a range that doesn't straddle 0.5 pins every villager at the nearer bound
        // (all dwarves at their max size, all elves at their min) — re-rolling distributes them across
        // the range and matches the editor preview (MCA randomize → apply). Children keep their parent
        // blend (early return above); only parentless founders roll here.
        OriginGenes.apply(villager, OriginGenes.resolveBodyMetrics(OriginRegistry.effectiveInheritedGenes(originId)),
                villager.getRandom());
        rollTraitGenes(villager, state, originId);
        Heredity.seedFounder(state.life(), originId, villager.getRandom());
        rollAndStoreStageDays(villager, state, originId);
    }

    /**
     * Fraction-weighted blend of the contributors' body-metric ranges. A contributor
     * that doesn't constrain a metric counts as the full {@code [0,1]} range, so
     * partial ancestry of an unconstrained race widens the band rather than ignoring it.
     */
    private static Map<String, GeneRange> blendBodyMetrics(List<OriginSelector.Weighted> mix) {
        List<Map<String, GeneRange>> perOrigin = new ArrayList<>(mix.size());
        Set<String> targets = new LinkedHashSet<>();
        for (OriginSelector.Weighted w : mix) {
            Map<String, GeneRange> m = OriginGenes.resolveBodyMetrics(
                    OriginRegistry.effectiveInheritedGenes(w.originId()));
            perOrigin.add(m);
            targets.addAll(m.keySet());
        }
        if (targets.isEmpty()) return Map.of();
        Map<String, GeneRange> out = new LinkedHashMap<>();
        for (String target : targets) {
            float min = 0f;
            float max = 0f;
            for (int i = 0; i < mix.size(); i++) {
                GeneRange r = perOrigin.get(i).get(target);
                float f = mix.get(i).fraction();
                min += f * (r != null ? r.min() : 0f);
                max += f * (r != null ? r.max() : 1f);
            }
            out.put(target, new GeneRange(min, max));
        }
        return out;
    }

    /**
     * Roll MCA traits for a mixed founder: each trait's spawn chance is the
     * fraction-weighted sum of its occurrence across the contributing origins
     * (a half-elf gets half the elf-trait chance plus half the human-trait chance).
     */
    private static void rollBlendedTraitGenes(VillagerEntityMCA villager, List<OriginSelector.Weighted> mix) {
        Map<String, Float> chance = new LinkedHashMap<>();
        for (OriginSelector.Weighted w : mix) {
            for (com.aetherianartificer.townstead.origin.gene.types.TraitOccurrenceGeneType.Instance gene
                    : OriginRegistry.traitGenes(w.originId())) {
                chance.merge(gene.trait(), w.fraction() * gene.delta(), Float::sum);
            }
        }
        for (Map.Entry<String, Float> entry : chance.entrySet()) {
            net.conczin.mca.entity.ai.Traits.Trait trait = net.conczin.mca.entity.ai.Traits.Trait.valueOf(entry.getKey());
            if (trait == net.conczin.mca.entity.ai.Traits.UNKNOWN) continue;
            float p = Math.min(1f, entry.getValue());
            if (p >= 1f || villager.getRandom().nextFloat() < p) {
                villager.getTraits().addTrait(trait);
            }
        }
    }

    private record BlendedCycle(LifeCycle cycle, float variance) {}

    /**
     * Blend the contributors' life cycles when they share a stage structure (same
     * stage ids/order/canonical kinds), interpolating each stage's day count and the
     * variance by share. Returns {@code null} when the structures differ (e.g. a
     * butterfly crossed with a human), so the caller falls back to the dominant cycle.
     */
    @org.jetbrains.annotations.Nullable
    private static BlendedCycle blendCycle(List<OriginSelector.Weighted> mix) {
        List<LifeCycle> cycles = new ArrayList<>(mix.size());
        float[] fraction = new float[mix.size()];
        float[] variance = new float[mix.size()];
        for (int i = 0; i < mix.size(); i++) {
            com.aetherianartificer.townstead.origin.gene.types.LifeCycleGeneType.Instance gene =
                    OriginRegistry.effectiveCycleGene(mix.get(i).originId());
            cycles.add(gene == null || gene.cycle().isEmpty() ? LifeCycle.defaultHumanLike() : gene.cycle());
            variance[i] = gene == null ? 0f : gene.variance();
            fraction[i] = mix.get(i).fraction();
        }
        LifeCycle ref = cycles.get(0);
        for (LifeCycle c : cycles) {
            if (!sameStructure(ref, c)) return null;
        }
        List<LifeStage> stages = new ArrayList<>(ref.size());
        for (int s = 0; s < ref.size(); s++) {
            float days = 0f;
            for (int i = 0; i < cycles.size(); i++) days += fraction[i] * cycles.get(i).stageAt(s).days();
            LifeStage base = ref.stageAt(s);
            stages.add(new LifeStage(base.id(), base.label(), base.presentsAs(), Math.max(1, Math.round(days)),
                    base.narrativeStart(), base.narrativeEnd(), base.onEnd(), base.scale(), base.explicitNarrative()));
        }
        float blendedVariance = 0f;
        for (int i = 0; i < mix.size(); i++) blendedVariance += fraction[i] * variance[i];
        return new BlendedCycle(new LifeCycle(stages), blendedVariance);
    }

    private static boolean sameStructure(LifeCycle a, LifeCycle b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            LifeStage sa = a.stageAt(i);
            LifeStage sb = b.stageAt(i);
            if (!sa.id().equals(sb.id()) || sa.presentsAs() != sb.presentsAs()) return false;
        }
        return true;
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
        // Legacy villagers predate the diploid genotype: fill in any genes they lack
        // (homozygous from a legacy expressed variant where present) without re-rolling
        // what they already carry, and seed their heritage from the origin.
        Heredity.migrateFounder(state.life(), originId, villager.getRandom());
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
    /**
     * Roll a personality from the origin's allowlist and apply it: store the chosen ref on the Life
     * (drives display + voice) and set the MCA brain personality to the base enum it extends (so MCA's
     * own mechanics apply). An origin with no personality policy yields a null ref, leaving MCA's own
     * rolled personality untouched (vanilla behaviour).
     */
    public static void assignPersonality(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation originId) {
        String ref = PersonalityResolver.roll(originId, villager.getRandom());
        if (ref == null) return;
        state.life().setPersonalityId(ref);
        Personality base = PersonalityResolver.baseOf(ref);
        if (base != null) villager.getVillagerBrain().setPersonality(base);
    }

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

    private static void rollAndStoreStageDays(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation originId) {
        LifeCycleGeneType.Instance gene = OriginRegistry.effectiveCycleGene(originId);
        LifeCycle cycle = gene == null || gene.cycle().isEmpty() ? LifeCycle.defaultHumanLike() : gene.cycle();
        float variance = gene == null ? 0f : gene.variance();
        rollAndStoreStageDays(villager, state, cycle, variance);
    }

    private static void rollAndStoreStageDays(VillagerEntityMCA villager, TownsteadVillager state,
                                              LifeCycle cycle, float variance) {
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
