package com.aetherianartificer.townstead.origin.reproduction;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.FertilityGeneType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Server-side enforcement of the {@code townstead_origins:fertility} gene. An entity is sterile when
 * any expressed fertility gene reports {@code fertility <= 0}; the breeding mixins block MCA's
 * procreation and gestation when either prospective parent is sterile. Entities with no fertility
 * gene (most villagers, players) are fertile by default.
 */
public final class Fertility {

    private Fertility() {}

    /** Whether this entity can reproduce at all (no sterile fertility gene expressed). */
    public static boolean isFertile(LivingEntity entity) {
        if (entity == null) return true;
        List<FertilityGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, FertilityGeneType.Instance.class);
        for (FertilityGeneType.Instance gene : genes) {
            if (!gene.fertile()) return false;
        }
        return true;
    }

    /** Whether two prospective parents can produce offspring (both fertile). */
    public static boolean canBreed(LivingEntity a, LivingEntity b) {
        return isFertile(a) && isFertile(b);
    }
}
