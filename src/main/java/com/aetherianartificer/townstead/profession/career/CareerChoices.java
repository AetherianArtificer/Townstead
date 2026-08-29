package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Learn a skill permanently, then equip at most one skill from a data-defined skill group. */
public final class CareerChoices {
    private CareerChoices() {}

    public static LearnedSkills.Result learn(LivingEntity entity, ResourceLocation choice) {
        SkillDef existing = SkillDefs.byId(choice);
        if (LearnedSkills.has(entity, choice)) {
            if (existing != null && existing.skillGroup() != null) {
                activate(entity, existing.skillGroup(), choice);
            }
            return new LearnedSkills.Result(true, null);
        }
        if (existing != null) {
            var owner = com.aetherianartificer.townstead.profession.def.ProfessionDefs
                    .byId(existing.profession());
            if (owner != null) {
                String blocker = SkillPoints.blocker(entity, owner, existing);
                if (blocker != null) return new LearnedSkills.Result(false, blocker);
            }
        }
        LearnedSkills.Result result = LearnedSkills.learn(entity, choice);
        if (!result.ok()) return result;
        SkillDef def = SkillDefs.byId(choice);
        if (def != null && def.skillGroup() != null) activate(entity, def.skillGroup(), choice);
        com.aetherianartificer.townstead.profession.DataDrivenTrades.onSkillLearned(entity, choice);
        return result;
    }

    public static boolean activate(LivingEntity entity, ResourceLocation skillGroup, ResourceLocation choice) {
        if (!LearnedSkills.has(entity, choice)) return false;
        if (entity instanceof VillagerEntityMCA villager) {
            var memory = TownsteadVillagers.get(villager).professionMemory();
            boolean changed = memory.careerProfile().activateSkill(skillGroup, choice);
            if (changed) memory.markCareerDirty();
            return changed;
        }
        if (entity instanceof Player player) {
            final boolean[] changed = {false};
            PlayerCareers.mutate(player, profile -> changed[0] = profile.activateSkill(skillGroup, choice));
            return changed[0];
        }
        return false;
    }

    /** The screen/command choose path: only skills of an acquired career may be equipped. */
    public static LearnedSkills.Result chooseFromAcquired(LivingEntity entity, ResourceLocation skillId) {
        CareerProfile profile = CareerProfiles.of(entity);
        if (profile == null || skillId == null) {
            return new LearnedSkills.Result(false, "no career profile");
        }
        if (!holdsCareerOffering(profile, skillId)) {
            return new LearnedSkills.Result(false, "not available from an acquired career");
        }
        return learn(entity, skillId);
    }

    /**
     * Whether the subject holds a career that offers this skill.
     *
     * <p>{@code acquiredCareers} alone is the WRONG test, and it is why the screen and the server
     * disagreed: that set records ADVANCED careers you have unlocked. A root career like Cook never
     * enters it, so a player whose primary vocation was Cook, holding a skill point, looking at a
     * Cook skill the board had drawn as ready, was refused with "not available from an acquired
     * career". This mirrors the rule {@code CareerGraphBuilder} uses to decide a root career is
     * acquired, so what the board offers and what the server accepts are the same thing.</p>
     */
    private static boolean holdsCareerOffering(CareerProfile profile, ResourceLocation skillId) {
        for (com.aetherianartificer.townstead.profession.def.ProfessionDef def
                : com.aetherianartificer.townstead.profession.def.ProfessionDefs.all().values()) {
            if (def == null || !def.skills().contains(skillId)) continue;
            boolean held = def.isRoot()
                    ? def.id().equals(profile.primaryVocation())
                            || profile.careerHistory().contains(def.id())
                            || profile.professionXp(Careers.resolve(def.id().toString())).xp() > 0
                    : profile.acquiredCareers().contains(def.id());
            if (held) return true;
        }
        return false;
    }

    public static boolean isActive(LivingEntity entity, ResourceLocation skill) {
        SkillDef def = SkillDefs.byId(skill);
        if (def == null || !LearnedSkills.has(entity, skill)) return false;
        if (def.skillGroup() == null) return true;
        CareerProfile profile = CareerProfiles.of(entity);
        return profile != null && skill.equals(profile.activeBySkillGroup().get(def.skillGroup()));
    }
}
