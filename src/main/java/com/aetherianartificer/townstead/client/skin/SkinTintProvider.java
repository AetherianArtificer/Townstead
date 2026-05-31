package com.aetherianartificer.townstead.client.skin;

import net.minecraft.world.entity.LivingEntity;

import java.util.OptionalInt;

/**
 * Resolves an entity's skin tint as a packed RGB (no alpha), or empty to leave
 * MCA's native skin untouched. Works for any genetics-rendered entity: MCA
 * villagers and players using the villager player-model both reach MCA's
 * {@code SkinLayer}. Implementations must read only client-visible, server-synced
 * data and be deterministic, so every client computes the same colour for the
 * same entity. Register via {@link SkinTintRegistry}.
 */
public interface SkinTintProvider {

    OptionalInt resolve(LivingEntity entity);
}
