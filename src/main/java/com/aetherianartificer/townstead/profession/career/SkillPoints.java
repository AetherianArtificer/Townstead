package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
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

/**
 * The skill-point economy, fully derived so it can never drift from the save: points earned are
 * the sum of {@code skill_points} across levels reached (v1 defs fall back to
 * {@code points_per_tier}), points spent are the summed costs of the learned skills belonging to
 * the profession, and the balance is the difference. Banking is implicit; there is no ledger.
 */
public final class SkillPoints {

    private SkillPoints() {}

    public static int earned(LivingEntity entity, ProfessionDef def) {
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (store == null) return 0;
        return def.skillPointsThrough(ProfessionProgress.getTier(store, def.id()));
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

    public static int available(LivingEntity entity, ProfessionDef def) {
        return Math.max(0, earned(entity, def) - spent(entity, def));
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
        if (Math.max(0, skill.cost()) > available(entity, def)) {
            return "needs " + skill.cost() + " skill point" + (skill.cost() == 1 ? "" : "s");
        }
        return null;
    }

    public static boolean canLearn(LivingEntity entity, ProfessionDef def, SkillDef skill) {
        return blocker(entity, def, skill) == null;
    }

    /**
     * Villagers spend their own points: after a tier-up, learn random affordable skills from the
     * newly available pools until nothing is learnable. Players always choose for themselves.
     */
    public static void autoSpend(LivingEntity villager, Collection<ResourceLocation> careers) {
        if (!(villager instanceof VillagerEntityMCA)) return;
        for (ResourceLocation careerId : careers) {
            ProfessionDef def =
                    com.aetherianartificer.townstead.profession.def.ProfessionDefs.byId(careerId);
            if (def == null) continue;
            for (int guard = 0; guard < 64; guard++) {
                List<ResourceLocation> candidates = new ArrayList<>();
                for (ResourceLocation skillId : def.skills()) {
                    SkillDef skill = SkillDefs.byId(skillId);
                    if (skill == null || LearnedSkills.has(villager, skillId)) continue;
                    if (canLearn(villager, def, skill)) candidates.add(skillId);
                }
                if (candidates.isEmpty()) break;
                ResourceLocation pick = candidates.get(
                        villager.getRandom().nextInt(candidates.size()));
                if (!CareerChoices.learn(villager, pick).ok()) break;
            }
        }
    }
}
