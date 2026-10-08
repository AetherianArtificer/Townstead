package com.aetherianartificer.townstead.profession.skill;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.PowerSource;
import com.aetherianartificer.townstead.profession.career.CareerChoices;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.def.ProfessionPaths;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.ProfessionIdentity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The professions {@link PowerSource} the power layer anticipated: an entity's learned skills
 * become powers keyed by skill id, exactly as expressed genes do through {@code GenePowerSource}.
 * Every applier (abilities, modifiers, immunities, triggers, ...) resolves through
 * {@code Powers}, so a skill power needs no applier changes and no Root involvement.
 *
 * <p>Only the active option in a skill group expresses its power; learning stays permanent
 * while expression follows the equipped choice, mirroring the capability-side gating in
 * {@link ProfessionCapabilitySource}.</p>
 *
 * <p>A committed Path also contributes its shared powers once. This is the place for Path-wide
 * machinery such as Chef Flow: each chosen Skill can spend the same meter without having to
 * duplicate its producer.</p>
 */
public final class SkillPowerSource implements PowerSource {

    @Override
    public void collect(LivingEntity entity, List<Power> out) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.CAREERS)) return;
        Set<ResourceLocation> learned = LearnedSkills.learned(entity);
        Set<ResourceLocation> professions = new LinkedHashSet<>();
        for (ResourceLocation skillId : learned) {
            SkillDef skill = SkillDefs.byId(skillId);
            if (skill == null) continue;
            professions.add(skill.profession());
            if (!CareerChoices.isActive(entity, skillId)) continue;
            if (skill.power() != null) out.add(new Power(skillId, skill.power()));
            // Companions ride the skill, as a gene's companions ride the gene.
            for (var companion : skill.companions().entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(skillId + "/" + companion.getKey());
                if (id != null) out.add(new Power(id, companion.getValue()));
            }
        }
        // Combo skills held by career history express their power like a learned skill.
        for (var combo : com.aetherianartificer.townstead.profession.def.ComboSkills.unlockedFor(entity)) {
            if (combo.power() != null) out.add(new Power(combo.id(), combo.power()));
            for (var companion : combo.companions().entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(combo.id() + "/" + companion.getKey());
                if (id != null) out.add(new Power(id, companion.getValue()));
            }
        }
        ResourceLocation rawProfession = ProfessionIdentity.rawId(entity);
        if (rawProfession != null) professions.add(ProfessionDefs.canonicalId(rawProfession));
        for (ResourceLocation profession : professions) {
            ProfessionPaths.Path path = ProfessionIdentity.path(entity, profession);
            if (path == null) continue;
            for (int i = 0; i < path.powers().size(); i++) {
                ResourceLocation id = ResourceLocation.tryParse(path.professionId().getNamespace() + ":"
                        + path.professionId().getPath() + "/path/" + path.id() + "/power/" + i);
                if (id != null) out.add(new Power(id, path.powers().get(i)));
            }
        }
    }
}
