package com.aetherianartificer.townstead.root.locomotion;

import org.jetbrains.annotations.Nullable;

/**
 * Duck interface merged onto every {@link net.minecraft.world.entity.Mob} by
 * {@code MobLocomotionMixin}. A null state means the mob is on the ground.
 */
public interface LocomotionCarrier {

    @Nullable
    Locomotion.State townstead$locomotion();

    void townstead$setLocomotion(@Nullable Locomotion.State state);
}
