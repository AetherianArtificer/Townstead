package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Insight is one shared, derived budget. Advancing any registered career earns it; learning a
 * skill in any registered career spends it. Because both sides are rebuilt from career progress
 * and learned skills, the balance cannot drift from the save and needs no separate ledger.
 */
public final class SkillPoints {

    private SkillPoints() {}

    public static int earned(LivingEntity entity, ProfessionDef def) {
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (store == null) return 0;
        return def.skillPointsThrough(ProfessionProgress.getTier(store, def.id()));
    }

    /** Insight earned across every career in which the character has actual standing. */
    public static int earned(LivingEntity entity) {
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        CareerProfile profile = CareerProfiles.of(entity);
        return store == null || profile == null ? 0 : earned(profile, store);
    }

    static int earned(CareerProfile profile, ProfessionXpStore store) {
        int total = 0;
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            if (!hasStanding(profile, store, def)) continue;
            total += Math.max(0,
                    def.skillPointsThrough(ProfessionProgress.getTier(store, def.id())));
        }
        return total;
    }

    /**
     * Tier one is the progression engine's baseline even for untouched careers, so registry
     * membership—not tier alone—decides whether a career contributes Insight.
     */
    static boolean hasStanding(CareerProfile profile, ProfessionXpStore store, ProfessionDef def) {
        int xp = ProfessionProgress.getXp(store, def.id());
        if (def.isRoot()) {
            return xp > 0 || def.id().equals(profile.primaryVocation())
                    || profile.careerHistory().contains(def.id());
        }
        return xp > 0 || profile.acquiredCareers().contains(def.id());
    }

    public static int spent(LivingEntity entity, ProfessionDef def) {
        Set<ResourceLocation> learned = LearnedSkills.learned(entity);
        int total = 0;
        for (ResourceLocation skillId : def.skills()) {
            if (!learned.contains(skillId)) continue;
            SkillDef skill = SkillDefs.byId(skillId);
            if (skill != null) total += Math.max(0, skill.cost());
        }
        return total;
    }

    /** Insight spent across the complete learned-skill set, regardless of owning career. */
    public static int spent(LivingEntity entity) {
        return spent(LearnedSkills.learned(entity));
    }

    static int spent(Set<ResourceLocation> learned) {
        int total = 0;
        for (ResourceLocation skillId : learned) {
            SkillDef skill = SkillDefs.byId(skillId);
            if (skill != null) total += Math.max(0, skill.cost());
        }
        return total;
    }

    public static int available(LivingEntity entity) {
        return Math.max(0, earned(entity) - spent(entity));
    }

    static int available(CareerProfile profile, ProfessionXpStore store,
                         Set<ResourceLocation> learned) {
        return Math.max(0, earned(profile, store) - spent(learned));
    }

    public static int available(LivingEntity entity, ProfessionDef def) {
        return available(entity);
    }

    /** Null when the skill is learnable now; otherwise a short human-readable reason. */
    @Nullable
    public static String blocker(LivingEntity entity, ProfessionDef def, SkillDef skill) {
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (store == null) return "no progression state";
        if (ProfessionProgress.getTier(store, def.id()) < skill.tier()) {
            return "requires " + def.levelName(skill.tier()).getString();
        }
        Set<ResourceLocation> learned = LearnedSkills.learned(entity);
        String relationshipBlocker = relationshipBlocker(learned, skill);
        if (relationshipBlocker != null) return relationshipBlocker;
        if (!skill.evidence().isEmpty()) {
            var server = entity.getServer();
            if (server == null) return "evidence unavailable";
            String evidenceBlocker = evidenceBlocker(skill,
                    key -> Chronicles.count(server, entity.getUUID(), key));
            if (evidenceBlocker != null) return evidenceBlocker;
        }
        if (Math.max(0, skill.cost()) > available(entity)) {
            return "needs " + skill.cost() + " Insight";
        }
        return null;
    }

    @Nullable
    static String evidenceBlocker(SkillDef skill, ToIntFunction<String> counts) {
        for (var requirement : skill.evidence()) {
            if (counts.applyAsInt(requirement.key()) < requirement.target()) {
                return "needs " + requirement.target() + " recorded '"
                        + requirement.key() + "'";
            }
        }
        return null;
    }

    /**
     * The authored graph owns progression order. Rank decides whether a node is eligible, but
     * neither a matching rank nor membership in another path creates a relationship.
     */
    @Nullable
    static String relationshipBlocker(Set<ResourceLocation> learned, SkillDef skill) {
        for (ResourceLocation required : skill.requires()) {
            if (!learned.contains(required)) {
                return "missing prerequisite '" + required + "'";
            }
        }
        for (ResourceLocation other : skill.exclusiveWith()) {
            if (learned.contains(other)) return "exclusive with '" + other + "'";
        }
        for (ResourceLocation learnedId : learned) {
            SkillDef learnedSkill = SkillDefs.byId(learnedId);
            if (learnedSkill != null && learnedSkill.exclusiveWith().contains(skill.id())) {
                return "exclusive with '" + learnedId + "'";
            }
        }
        return null;
    }

    public static boolean canLearn(LivingEntity entity, ProfessionDef def, SkillDef skill) {
        return blocker(entity, def, skill) == null;
    }

    /**
     * Villagers spend their own points: after a tier-up, learn random affordable skills from the
     * newly available pools until nothing is learnable. Players always choose for themselves.
     * Candidates are weighted by {@link PathAffinity}: specialization-path skills only enter
     * the pool when the villager's worksite justifies them, and a specced villager leans into
     * finishing the build.
     */
    public static void autoSpend(LivingEntity villager, Collection<ResourceLocation> careers) {
        if (!(villager instanceof VillagerEntityMCA)) return;
        for (ResourceLocation careerId : careers) {
            ProfessionDef def =
                    com.aetherianartificer.townstead.profession.def.ProfessionDefs.byId(careerId);
            if (def == null) continue;
            for (int guard = 0; guard < 64; guard++) {
                List<ResourceLocation> candidates = new ArrayList<>();
                List<Integer> weights = new ArrayList<>();
                int totalWeight = 0;
                for (ResourceLocation skillId : def.skills()) {
                    SkillDef skill = SkillDefs.byId(skillId);
                    if (skill == null || LearnedSkills.has(villager, skillId)) continue;
                    if (!canLearn(villager, def, skill)) continue;
                    int weight = PathAffinity.autoSpendWeight(villager, def, skill);
                    if (weight <= 0) continue;
                    candidates.add(skillId);
                    weights.add(weight);
                    totalWeight += weight;
                }
                if (candidates.isEmpty()) break;
                int roll = villager.getRandom().nextInt(totalWeight);
                ResourceLocation pick = candidates.get(candidates.size() - 1);
                for (int i = 0; i < candidates.size(); i++) {
                    roll -= weights.get(i);
                    if (roll < 0) {
                        pick = candidates.get(i);
                        break;
                    }
                }
                if (!CareerChoices.learn(villager, pick).ok()) break;
            }
        }
    }
}
