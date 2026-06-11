package com.aetherianartificer.townstead.pheno.power;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * A provider of an entity's currently-granted {@link Power}s. The genetics system
 * registers one ({@code GenePowerSource}); the professions system will register
 * another. {@link Powers} unions every registered source.
 */
public interface PowerSource {

    /** Append the powers this source grants {@code entity} right now to {@code out}. */
    void collect(LivingEntity entity, List<Power> out);
}
