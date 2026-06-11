package com.aetherianartificer.townstead.pheno.condition.fluid;

import net.minecraft.world.level.material.FluidState;

/**
 * A predicate over a {@link FluidState} (Apoli's {@code fluid_condition}): which fluid is
 * present and its properties. Nested in the block {@code fluid} condition.
 */
@FunctionalInterface
public interface FluidCondition {

    boolean test(FluidState state);

    default FluidCondition negate() {
        return state -> !test(state);
    }
}
