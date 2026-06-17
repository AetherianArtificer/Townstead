package com.aetherianartificer.townstead.pheno.capability;

import net.minecraft.world.entity.LivingEntity;

/**
 * A producer of capability contributions for an entity (genetics, professions, equipment,
 * mood, building context). Registered with {@link Capabilities}; queried whenever effective
 * capabilities are resolved. Mirrors {@code PowerSource} so the same source-agnostic pattern
 * applies: Townstead systems query the resolved {@link CapabilityView}, never a specific
 * source.
 */
@FunctionalInterface
public interface CapabilitySource {
    void contribute(LivingEntity entity, CapabilityCollector out);
}
