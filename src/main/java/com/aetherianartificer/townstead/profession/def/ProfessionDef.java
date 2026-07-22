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
        @Nullable ResourceLocation icon,
        List<LevelDef> levels) {

    /** A root career: practiced directly, never acquired through a route. */
    public boolean isRoot() {
        return parents.isEmpty();
    }

    public boolean eligible(LivingEntity entity) {
        return requirements.test(new ConditionContext(entity));
    }

    /**
     * The rank name for a level: the level's own {@code name} when authored, the shared
     * Novice-to-Master keys through level 5, and beyond that the level-5 name with a numeral
     * ("Master II"), so unnamed long tracks never break.
     */
    public Component levelName(int tier) {
        int clamped = Math.max(1, tier);
        if (clamped <= levels.size() && levels.get(clamped - 1).name() != null) {
            return levels.get(clamped - 1).name();
        }
        if (clamped <= 5) {
            return Component.translatable("townstead.profession.level." + clamped);
        }
        return Component.translatable("townstead.profession.level.5").copy()
                .append(" " + roman(clamped - 4));
    }

    /** Skill points earned through the given tier; v1 defs fall back to points_per_tier. */
    public int skillPointsThrough(int tier) {
        if (levels.isEmpty()) return Math.max(0, pointsPerTier * Math.max(0, tier));
        int total = 0;
        for (int i = 0; i < Math.min(tier, levels.size()); i++) {
            total += Math.max(0, levels.get(i).skillPoints());
        }
        return total;
    }

    static String roman(int n) {
        String[] tens = {"", "X", "XX", "XXX", "XL", "L"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return n <= 0 || n >= 60 ? String.valueOf(n) : tens[n / 10] + ones[n % 10];
    }

    /** Compatibility constructor predating per-level {@code levels}. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills,
                         List<String> historyCounters, List<ResourceLocation> parents, boolean hidden,
                         Condition requirements, List<String> acquisitionRoutes,
                         List<JobSiteProvider> jobSites, List<ResourceLocation> aliases,
                         java.util.Map<Integer, List<TradeDef>> trades,
                         List<RequirementHint> requirementHints, @Nullable ResourceLocation icon) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, historyCounters, parents, hidden, requirements, acquisitionRoutes, jobSites,
                aliases, trades, requirementHints, icon, List.of());
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
