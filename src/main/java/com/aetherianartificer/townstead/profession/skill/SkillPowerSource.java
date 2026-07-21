package com.aetherianartificer.townstead.profession.skill;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerSource;
import com.aetherianartificer.townstead.profession.career.CareerChoices;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The professions {@link PowerSource} the power layer anticipated: an entity's learned skills
 * become powers keyed by skill id, exactly as expressed genes do through {@code GenePowerSource}.
 * Every applier (abilities, modifiers, immunities, triggers, ...) resolves through
 * {@code Powers}, so a skill power needs no applier changes and no Root involvement.
 *
 * <p>Only the active option in a skill group expresses its power; learning stays permanent
 * while expression follows the equipped choice, mirroring the capability-side gating in
 * {@link ProfessionCapabilitySource}.</p>
 */
public final class SkillPowerSource implements PowerSource {

    @Override
    public void collect(LivingEntity entity, List<Power> out) {
        for (ResourceLocation skillId : LearnedSkills.learned(entity)) {
            SkillDef skill = SkillDefs.byId(skillId);
            if (skill == null || skill.power() == null) continue;
            if (!CareerChoices.isActive(entity, skillId)) continue;
            out.add(new Power(skillId, skill.power()));
        }
    }
}
