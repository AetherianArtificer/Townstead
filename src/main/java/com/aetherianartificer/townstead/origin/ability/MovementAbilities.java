package com.aetherianartificer.townstead.origin.ability;

import net.minecraft.world.entity.LivingEntity;

/**
 * Side-aware ability check for movement abilities that run in the entity's physics
 * tick on both client and server. On the server it reads the authoritative gene
 * state ({@link Abilities}); on the client it reads the synced per-entity set
 * (client store), so the controlling client predicts the same result and the
 * movement does not rubber-band.
 *
 * <p>The client branch is only reached when {@code level().isClientSide}, so the
 * client-only class it calls is never linked on a dedicated server.</p>
 */
public final class MovementAbilities {

    private MovementAbilities() {}

    public static boolean isActive(LivingEntity entity, Ability ability) {
        if (entity.level().isClientSide) {
            return com.aetherianartificer.townstead.client.origin.ClientAbilities.isActive(entity, ability);
        }
        return Abilities.isActive(entity, ability);
    }
}
