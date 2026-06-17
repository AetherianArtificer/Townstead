package com.aetherianartificer.townstead.origin.ability;

import com.aetherianartificer.townstead.origin.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side query: is a given {@link Ability} currently active on an entity?
 * Passive abilities count whenever expressed; toggle abilities count only while
 * toggled on. Used by mixins that need to know an ability's live state (e.g. the
 * elytra-flight glide check).
 */
public final class Abilities {

    private Abilities() {}

    public static boolean isActive(LivingEntity entity, Ability ability) {
        for (Power gene : Powers.active(entity)) {
            if (!(gene.component() instanceof AbilityGeneType.Instance instance) || instance.ability() != ability) {
                continue;
            }
            if (instance.mode() == AbilityGeneType.Mode.TOGGLE) {
                if (AbilityToggles.isOn(entity, gene.id())) return true;
            } else {
                return true;
            }
        }
        return false;
    }
}
