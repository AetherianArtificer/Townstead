package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.types.LifeCycleGeneType;
import com.aetherianartificer.townstead.root.personality.PersonalityResolver;
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
import java.util.WeakHashMap;

/**
 * Applies origins to villagers at runtime.
 *
 * <p>{@link #onTrueSpawn} runs only for genuinely spawned villagers (driven by
 * the FinalizeSpawn event), so it is safe to roll genes and life-stage durations
 * there. Loaded/legacy villagers are handled by {@link #backfillIfMissing}, which
 * assigns the default origin id and rolls stage durations once without touching
 * already-rolled genes.</p>
 */
public final class RootSpawnHandler {

    // A founder only needs the expensive canonicalize/heal/recompute migration
    // once per loaded entity per gene-data generation. Weak keys disappear when
    // the entity unloads; a datapack reload advances GeneRegistry.revision().
    private static final Map<VillagerEntityMCA, Long> MIGRATED_GENE_REVISION = new WeakHashMap<>();

    private RootSpawnHandler() {}

    /**
     * Choose the villager's origin, stamp the id, and roll its genes within the origin's ranges.
     * Driven by {@code VillagerFinalizeSpawnMixin} at the TAIL of MCA's {@code finalizeSpawn} —
     * after MCA's {@code initialize()} re-randomizes genetics/traits (so our writes aren't
     * clobbered) and before the villager's first tick (so {@code backfillIfMissing} can't stamp
     * the default origin first).
     */
    public static void onTrueSpawn(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        assignSpawnRoot(villager, state);
        // Root + genotype are now resolved, so the fertility gene expresses; reflect it onto MCA's
        // infertile trait (when this MCA version defines one) before the flush below persists it.
        com.aetherianartificer.townstead.root.reproduction.Fertility.syncMcaInfertileTrait(villager);
        // Persist immediately: the root/heritage/genotype live in a data attachment that only persists
        // when the snapshot is written, and there is no reliable periodic flush before world save. Without
        // this the spawn-assigned root (and a bred child's inherited root) is dropped on reload, and
        // backfillIfMissing then re-stamps the default Overworlder, e.g. Websters reverting on reload.
        TownsteadVillagers.flush(villager);
    }

    private static void assignSpawnRoot(VillagerEntityMCA villager, TownsteadVillager state) {
        // A bred child already had its origin, heritage and genotype set by the breeding
        // hook before this fires. Keep all of that (its floats are MCA's parent blend);
        // only roll its stage durations against the inherited origin.
        if (state.life().hasGenotype() || state.life().hasHeritage()) {
            ResourceLocation childRoot = ResourceLocation.tryParse(state.life().rootId());
            if (childRoot == null) childRoot = RootRegistry.DEFAULT_ID;
            assignPersonality(villager, state, childRoot);
            rollAndStoreStageDays(villager, state, childRoot);
            return;
        }

        // Founder: pick an origin weighted by the biome/dimension spawn bias, with a
        // per-species chance of a mixed-ancestry blend instead. A mix seeds its own
        // heritage + genotype; its body metrics and traits are blended from the
        // contributors by share (the parentless analogue of MCA's blend).
        //
        // Inside an existing village, constrain the selection: drop origins whose disposition group
        // clashes with the resident majority (so a hostile species never spawns into a peaceful town)
        // and honor any spawner building's authored origin policy. Outside a village, no constraint.
        VillageSpawnContext village = VillageSpawnContext.resolve(villager);
        RootSelector.Selection selection = village.active()
                ? RootSelector.select(villager.level(), villager.blockPosition(), villager.getRandom(), village::allows)
                : RootSelector.select(villager.level(), villager.blockPosition(), villager.getRandom());
        if (selection.isMixed()) {
            List<RootSelector.Weighted> mix = selection.mix();
            Heredity.seedMixedFounder(state.life(), mix, villager.getRandom());
            ResourceLocation mixedRoot = ResourceLocation.tryParse(state.life().rootId());
            assignPersonality(villager, state, mixedRoot == null ? RootRegistry.DEFAULT_ID : mixedRoot);
            RootGenes.apply(villager, blendBodyMetrics(mix), villager.getRandom());
            rollBlendedTraitGenes(villager, mix);
            // Life cycle: roll once against the dominant root's cycle, exactly like a
            // bred child. A share-blended cycle can't be reconstructed on load, so it
            // can't survive backfillIfMissing's re-authored-cycle fingerprint check.
            ResourceLocation dominant = ResourceLocation.tryParse(state.life().rootId());
            if (dominant == null) dominant = RootRegistry.DEFAULT_ID;
            rollAndStoreStageDays(villager, state, dominant);
            return;
        }

        // Empty selection (everything filtered out in a village) falls back to the village's own
        // majority origin, which is compatible by construction; else the default.
        ResourceLocation rootId = selection.single() != null ? selection.single()
                : village.fallbackRoot() != null ? village.fallbackRoot() : RootRegistry.DEFAULT_ID;
        state.life().setRoot(rootId.toString());
        assignPersonality(villager, state, rootId);
        // Founder: re-roll within the origin's ranges (not clamp). MCA's centeredRandom roll sits at
        // ~0.5, so clamping a range that doesn't straddle 0.5 pins every villager at the nearer bound
        // (all dwarves at their max size, all elves at their min) — re-rolling distributes them across
        // the range and matches the editor preview (MCA randomize → apply). Children keep their parent
        // blend (early return above); only parentless founders roll here.
        RootGenes.apply(villager, RootGenes.resolveBodyMetrics(RootRegistry.effectiveInheritedGenes(rootId)),
                villager.getRandom());
        rollTraitGenes(villager, state, rootId);
        Heredity.seedFounder(state.life(), rootId, villager.getRandom());
        rollAndStoreStageDays(villager, state, rootId);
    }

    /**
     * Fraction-weighted blend of the contributors' body-metric ranges. A contributor
     * that doesn't constrain a metric counts as the full {@code [0,1]} range, so
     * partial ancestry of an unconstrained race widens the band rather than ignoring it.
     */
    private static Map<String, GeneRange> blendBodyMetrics(List<RootSelector.Weighted> mix) {
        List<Map<String, GeneRange>> perRoot = new ArrayList<>(mix.size());
        Set<String> targets = new LinkedHashSet<>();
        for (RootSelector.Weighted w : mix) {
            Map<String, GeneRange> m = RootGenes.resolveBodyMetrics(
                    RootRegistry.effectiveInheritedGenes(w.rootId()));
            perRoot.add(m);
            targets.addAll(m.keySet());
        }
        if (targets.isEmpty()) return Map.of();
        Map<String, GeneRange> out = new LinkedHashMap<>();
        for (String target : targets) {
            float min = 0f;
            float max = 0f;
            for (int i = 0; i < mix.size(); i++) {
                GeneRange r = perRoot.get(i).get(target);
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
    private static void rollBlendedTraitGenes(VillagerEntityMCA villager, List<RootSelector.Weighted> mix) {
        Map<String, Float> chance = new LinkedHashMap<>();
        for (RootSelector.Weighted w : mix) {
            for (com.aetherianartificer.townstead.root.gene.types.TraitOccurrenceGeneType.Instance gene
                    : RootRegistry.traitGenes(w.rootId())) {
                chance.merge(gene.trait(), w.fraction() * gene.delta(), Float::sum);
            }
        }
        for (Map.Entry<String, Float> entry : chance.entrySet()) {
            net.conczin.mca.entity.ai.Traits.Trait trait =
                    com.aetherianartificer.townstead.compat.mca.McaTraitCompat.resolve(entry.getKey()).orElse(null);
            if (trait == null) continue; // gene references a trait this MCA version lacks
            float p = Math.min(1f, entry.getValue());
            if (p >= 1f || villager.getRandom().nextFloat() < p) {
                villager.getTraits().addTrait(trait);
            }
        }
    }

    /**
     * Assign the default origin id to a villager that has none, leaving genes
     * untouched. Returns {@code true} if stage durations were (re-)rolled, so the
     * caller can re-broadcast the life sync.
     */
    public static boolean backfillIfMissing(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        if (!state.life().hasRoot()) {
            state.life().setRoot(RootRegistry.DEFAULT_ID.toString());
        }
        ResourceLocation rootId = ResourceLocation.tryParse(state.life().rootId());
        if (rootId == null) rootId = RootRegistry.DEFAULT_ID;
        // Legacy villagers predate the diploid genotype: fill in any genes they lack
        // (homozygous from a legacy expressed variant where present) without re-rolling
        // what they already carry, and seed their heritage from the origin.
        long geneRevision = com.aetherianartificer.townstead.root.gene.GeneRegistry.revision();
        Long migratedRevision = MIGRATED_GENE_REVISION.get(villager);
        if (migratedRevision == null || migratedRevision != geneRevision) {
            Heredity.migrateFounder(state.life(), rootId, villager.getRandom());
            MIGRATED_GENE_REVISION.put(villager, geneRevision);
        }
        LifeCycle cycle = RootRegistry.effectiveLifeCycle(rootId);
        // Re-roll when the stored stageDays don't match the current cycle — either a
        // different length (origin reassigned), a re-authored shape, or a changed
        // aging mode/scale (fingerprint folds the scale in). 0 on pre-fingerprint
        // villagers also forces a re-roll. Self-heals existing saves.
        if (state.life().stageDaysLength() != cycle.size()
                || state.life().cycleFingerprint() != townstead$fingerprint(villager, cycle)) {
            rollAndStoreStageDays(villager, state, rootId);
            return true;
        }
        return false;
    }

    /**
     * Re-push a late-inherited child's genetics to clients already tracking it. The
     * baby-item birth path spawns the child inside MCA's {@code birthChild}, so
     * StartTracking synced the founder-seeded placeholder state to nearby players
     * before the inheritance hook ran; without a re-push the watching player keeps
     * rendering the placeholder genes (no gnome ears on a half-gnome newborn) until
     * the entity is re-tracked. Mirrors onTrueSpawn's fertility reflection and flush,
     * which also ran against the placeholder genotype.
     */
    public static void broadcastLateInheritance(VillagerEntityMCA child) {
        com.aetherianartificer.townstead.root.reproduction.Fertility.syncMcaInfertileTrait(child);
        TownsteadVillagers.flush(child);
        TownsteadVillager state = TownsteadVillagers.get(child);
        RootSyncS2CPayload rootSync = new RootSyncS2CPayload(child.getId(), state.life().rootId());
        ExpressedGenesS2CPayload genes = ExpressedGenesS2CPayload.forEntity(child.getId(), child);
        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload lifeSync =
                com.aetherianartificer.townstead.Townstead.townstead$lifeSync(child);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(child, rootSync);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(child, genes);
        if (lifeSync != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(child, lifeSync);
        }
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(child, rootSync);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(child, genes);
        if (lifeSync != null) {
            com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(child, lifeSync);
        }
        *///?}
    }

    /**
     * Roll each trait-granting gene the origin expresses once at spawn and grant the matching
     * MCA trait. A gene's occurrence ({@code force}/{@code delta}) is the spawn probability;
     * ≥1.0 is guaranteed. MCA owns membership (persisted, synced, heritable); Townstead's effect
     * data rides alongside via {@link com.aetherianartificer.townstead.root.trait.TraitRegistry}.
     */
    /**
     * Roll a personality from the origin's allowlist and apply it: store the chosen ref on the Life
     * (drives display + voice) and set the MCA brain personality to the MCA type it extends (so MCA's
     * own mechanics apply). An origin with no personality policy yields a null ref, leaving MCA's own
     * rolled personality untouched (vanilla behaviour).
     */
    public static void assignPersonality(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation rootId) {
        String ref = PersonalityResolver.roll(rootId, villager.getRandom());
        if (ref == null) {
            // The new root declares no personality policy (vanilla MCA). If a previous root had
            // assigned a custom personality, drop it and re-roll a fresh vanilla one, so a villager
            // reassigned to a policy-less root doesn't keep a personality from its old root's set
            // (which the inspector/voice would still show). At spawn the ref is already empty, so
            // MCA's own freshly-rolled personality is left untouched (vanilla behaviour).
            if (!state.life().personalityId().isEmpty()) {
                state.life().setPersonalityId("");
                villager.getVillagerBrain().setPersonality(randomVanillaPersonality(villager));
            }
            return;
        }
        state.life().setPersonalityId(ref);
        Personality base = PersonalityResolver.baseOf(ref);
        if (base != null) villager.getVillagerBrain().setPersonality(base);
    }

    /** A random assignable base MCA personality (excludes the {@code UNASSIGNED} sentinel). */
    private static Personality randomVanillaPersonality(VillagerEntityMCA villager) {
        java.util.List<Personality> all = com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat.all();
        java.util.List<Personality> pick = new ArrayList<>(all.size());
        for (Personality p : all) {
            if (p != Personality.UNASSIGNED
                    && com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat.isBuiltIn(p)
                    && com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat
                            .isValidFor(p, villager.getAgeState())) pick.add(p);
        }
        return pick.isEmpty() ? Personality.UNASSIGNED : pick.get(villager.getRandom().nextInt(pick.size()));
    }

    /**
     * Re-roll the personality within the villager's root policy when its life stage changes, so a
     * villager's disposition shifts as it grows (matching MCA's own age-up randomization, but kept
     * inside the root's allowlist). Driven by {@code VillagerEntityMCALifeStageMixin} at the TAIL of a
     * genuine {@code setAgeState} transition. A root with no personality policy rolls a null ref and is
     * left as MCA set it. Older MCA never randomized on age-up, so this hook is what makes the drift
     * happen there too; on newer MCA it overwrites MCA's out-of-policy pick.
     */
    public static void rerollPersonalityForAgeChange(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        ResourceLocation rootId = ResourceLocation.tryParse(state.life().rootId());
        if (rootId == null) rootId = RootRegistry.DEFAULT_ID;
        assignPersonality(villager, state, rootId);
        TownsteadVillagers.flush(villager);
    }

    private static void rollTraitGenes(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation rootId) {
        for (com.aetherianartificer.townstead.root.gene.types.TraitOccurrenceGeneType.Instance gene
                : RootRegistry.traitGenes(rootId)) {
            net.conczin.mca.entity.ai.Traits.Trait trait =
                    com.aetherianartificer.townstead.compat.mca.McaTraitCompat.resolve(gene.trait()).orElse(null);
            if (trait == null) continue; // gene references a trait this MCA version lacks
            if (gene.delta() >= 1.0f || villager.getRandom().nextFloat() < gene.delta()) {
                villager.getTraits().addTrait(trait);
            }
        }
    }

    private static void rollAndStoreStageDays(VillagerEntityMCA villager, TownsteadVillager state, ResourceLocation rootId) {
        LifeCycleGeneType.Instance gene = RootRegistry.effectiveCycleGene(rootId);
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
