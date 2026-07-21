package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A data-driven profession: the one and only career concept. Root professions (Farmer, Cook)
 * have no {@code parents}; advanced professions (Pizzaiolo, Barista) declare parent careers,
 * pheno-condition {@code requirements}, and {@code acquisition_routes} — the career tree is the
 * parent graph, not a separate schema. Each def carries its progression track, skill tree
 * membership, chronicle evidence counters, and the {@code poi} job-site providers that make the
 * job available in the world. Pheno owns capabilities and powers granted through skills;
 * Townstead owns what counts as successful work and emits the events that drive XP.
 */
public record ProfessionDef(
        ResourceLocation id,
        Component displayName,
        @Nullable Component description,
        ProgressionTrack progression,
        UnlockModel unlockModel,
        int pointsPerTier,
        RetrainingPolicy retraining,
        List<ResourceLocation> skills,
        List<String> historyCounters,
        List<ResourceLocation> parents,
        boolean hidden,
        Condition requirements,
        List<String> acquisitionRoutes,
        List<JobSiteProvider> jobSites,
        List<ResourceLocation> aliases,
        java.util.Map<Integer, List<TradeDef>> trades,
        List<RequirementHint> requirementHints,
        @Nullable ResourceLocation icon) {

    /** A root career: practiced directly, never acquired through a route. */
    public boolean isRoot() {
        return parents.isEmpty();
    }

    public boolean eligible(LivingEntity entity) {
        return requirements.test(new ConditionContext(entity));
    }

    /** Compatibility constructor predating the advanced-class unification. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, List.of());
    }

    /** Compatibility constructor predating {@code parents}/{@code requirements}/{@code poi}. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills,
                         List<String> historyCounters) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, historyCounters, List.of(), false, Conditions.ALWAYS, List.of(), List.of(),
                List.of());
    }

    /** Compatibility constructor predating {@code aliases}. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills,
                         List<String> historyCounters, List<ResourceLocation> parents, boolean hidden,
                         Condition requirements, List<String> acquisitionRoutes,
                         List<JobSiteProvider> jobSites) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, historyCounters, parents, hidden, requirements, acquisitionRoutes, jobSites,
                List.of(), java.util.Map.of(), List.of(), null);
    }

    /** Compatibility constructor predating {@code trades}. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills,
                         List<String> historyCounters, List<ResourceLocation> parents, boolean hidden,
                         Condition requirements, List<String> acquisitionRoutes,
                         List<JobSiteProvider> jobSites, List<ResourceLocation> aliases) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, historyCounters, parents, hidden, requirements, acquisitionRoutes, jobSites,
                aliases, java.util.Map.of(), List.of(), null);
    }
}
