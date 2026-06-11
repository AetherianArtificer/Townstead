package com.aetherianartificer.townstead.pheno.condition.bientity;

import net.minecraft.world.entity.LivingEntity;

/**
 * A predicate over an actor/target pair (Apoli's {@code bientity_condition}). Used to
 * gate a bi-entity action via the {@code if_bientity} meta action.
 */
@FunctionalInterface
public interface BiEntityCondition {

    boolean test(LivingEntity actor, LivingEntity target);

    default BiEntityCondition negate() {
        return (actor, target) -> !test(actor, target);
    }
}
