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
 * A data-driven profession: the one and only career concept, and careers are FLAT. Practiced
 * careers (Farmer, Cook) declare no {@code acquisition_routes} and are simply worked; gated
 * careers (Barista, Baker) declare routes plus pheno-condition {@code requirements}.
 * Specialization inside a profession is a {@code paths} branch (gateway skill, member skills,
 * favoured worksites), never a child profession. Each def carries its progression track, skill tree
 * membership, and the {@code poi} job-site providers that make the job available in the world.
 * Completed-work history is derived from those executable Jobs and task engines rather than
 * repeated here. {@code workTasks} declares which villager AI work behaviors the
 * profession's workers run; the engines live in code, the composition lives here. Pheno owns
 * capabilities and powers granted through skills; Townstead owns what counts as successful work
 * and emits the events that drive XP.
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
        boolean hidden,
        Condition requirements,
        List<String> acquisitionRoutes,
        List<JobSiteProvider> jobSites,
        List<ResourceLocation> aliases,
        List<ResourceLocation> clothing,
        java.util.Map<Integer, List<TradeDef>> trades,
        List<RequirementHint> requirementHints,
        @Nullable ResourceLocation icon,
        @Nullable ResourceLocation workSound,
        List<LevelDef> levels,
        List<WorkTaskDef> workTasks) {

    /**
     * A practiced career: taken up directly, never acquired through a route. Careers are flat;
     * declaring acquisition routes is the one thing that makes a career gated.
     */
    public boolean isRoot() {
        return acquisitionRoutes.isEmpty();
    }

    /**
     * The JSON-level mirror of {@link #isRoot()}, for the boot-time profession scan — the ONE
     * reader that cannot use the parsed def, because it runs during RegisterEvent, before
     * common setup registers the pheno condition types that {@code requirements} parsing
     * needs (a def with requirements would spuriously fail to parse and silently drop).
     * Keeping both forms of the practiced-vs-gated rule on this class is deliberate: change
     * one, change the other.
     */
    public static boolean declaresAcquisitionRoutes(com.google.gson.JsonObject json) {
        return json.has("acquisition_routes") && json.get("acquisition_routes").isJsonArray()
                && !json.getAsJsonArray("acquisition_routes").isEmpty();
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

    /** Small constructor for code-owned and test professions. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, false, Conditions.ALWAYS, List.of(), List.of(), List.of(),
                List.of(), java.util.Map.of(), List.of(), null, null, List.of(), List.of());
    }

    /** Constructor for definitions that only need eligibility and job sites. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills,
                         boolean hidden, Condition requirements,
                         List<String> acquisitionRoutes, List<JobSiteProvider> jobSites) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, hidden, requirements, acquisitionRoutes, jobSites, List.of(),
                List.of(), java.util.Map.of(), List.of(), null, null, List.of(), List.of());
    }

    /** Constructor for definitions that also declare aliases. */
    public ProfessionDef(ResourceLocation id, Component displayName, @Nullable Component description,
                         ProgressionTrack progression, UnlockModel unlockModel, int pointsPerTier,
                         RetrainingPolicy retraining, List<ResourceLocation> skills,
                         boolean hidden, Condition requirements, List<String> acquisitionRoutes,
                         List<JobSiteProvider> jobSites, List<ResourceLocation> aliases) {
        this(id, displayName, description, progression, unlockModel, pointsPerTier, retraining,
                skills, hidden, requirements, acquisitionRoutes, jobSites, aliases,
                List.of(), java.util.Map.of(), List.of(), null, null, List.of(), List.of());
    }
}
