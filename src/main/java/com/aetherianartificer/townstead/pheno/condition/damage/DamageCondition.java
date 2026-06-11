package com.aetherianartificer.townstead.pheno.condition.damage;

import net.minecraft.world.damagesource.DamageSource;

/**
 * A predicate over a damage event (Apoli's {@code damage_condition}): the
 * {@link DamageSource} plus the amount dealt. Used to gate {@code damage_modifier} genes
 * and {@code when_hurt}/{@code when_attack} triggers.
 */
@FunctionalInterface
public interface DamageCondition {

    boolean test(DamageSource source, float amount);

    default DamageCondition negate() {
        return (source, amount) -> !test(source, amount);
    }
}
