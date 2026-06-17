package com.aetherianartificer.townstead.profession.skill;

import com.aetherianartificer.townstead.pheno.capability.CapabilityCollector;
import com.aetherianartificer.townstead.pheno.capability.CapabilitySource;
import com.aetherianartificer.townstead.pheno.capability.Provenance;
import com.aetherianartificer.townstead.pheno.capability.SourceKind;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.def.SkillGrant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Feeds the capability layer from an entity's learned skills, each grant carrying the skill as
 * provenance. This is the second {@link CapabilitySource} the genetics source anticipated:
 * Townstead systems now query one resolved {@link com.aetherianartificer.townstead.pheno.capability.CapabilityView}
 * that blends genetics and professions, and {@code /pheno explain} shows both with their origin.
 */
public final class ProfessionCapabilitySource implements CapabilitySource {

    @Override
    public void contribute(LivingEntity entity, CapabilityCollector out) {
        for (ResourceLocation skillId : LearnedSkills.learned(entity)) {
            SkillDef skill = SkillDefs.byId(skillId);
            if (skill == null) continue;
            Provenance provenance = new Provenance(skillId, SourceKind.SKILL, skill.profession().getPath());
            for (SkillGrant grant : skill.grants()) {
                out.add(grant.toContribution(provenance, true));
            }
        }
    }
}
