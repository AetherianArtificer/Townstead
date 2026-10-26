package com.aetherianartificer.townstead.root.disposition;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Whether one entity is a threat to another: a monster, a mob currently targeting it, or anything
 * the viewer's disposition calls hostile. The one test behind {@code pheno:area_of_effect}'s
 * {@code target:"hostile"} and the {@code pheno:hostile} condition, so both read the same way.
 */
public final class Hostility {

    private Hostility() {}

    /** True when {@code other} is hostile to {@code viewer}. */
    public static boolean toward(LivingEntity viewer, LivingEntity other) {
        if (viewer == null || other == null || viewer == other) return false;
        return other instanceof Enemy
                || (other instanceof Mob mob && mob.getTarget() == viewer)
                || Dispositions.between(viewer, other) == Disposition.HOSTILE;
    }
}
