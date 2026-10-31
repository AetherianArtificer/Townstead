package com.aetherianartificer.townstead.root.locomotion;

import net.minecraft.world.entity.Mob;

/**
 * Moves a body in one {@link MovementMode}. Ticked by {@link Locomotion#tick} after
 * vanilla travel, so whatever it sets (velocity, look angles) drives the next tick.
 */
public interface Pilot {

    /** One server tick. False ends the mode: {@link #stop} runs and MCA takes the body back. */
    boolean tick(Mob mob);

    /** Leave the body as MCA expects it (gravity on, wings folded). Runs once on any exit. */
    void stop(Mob mob);

    /** Whether a {@code when_flying} lift ability should fire now. */
    default boolean wantsLift(Mob mob) {
        return false;
    }

    /** Whether MCA's navigation sits out too (the pilot is following its own route). */
    default boolean ownsNavigation() {
        return true;
    }

    /** One line for debug reports. */
    String describe();
}
