package com.aetherianartificer.townstead.origin.ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Drives the {@code phasing} (noclip) ability. Player-only: {@code noPhysics} removes
 * collision, which would strand AI pathfinding. Set in {@code aiStep} HEAD so the
 * tick's {@code move} skips collision, then the controlling client supplies vertical
 * flight at TAIL (gravity would otherwise drop a noclipping player through the floor).
 * Side-aware via {@link MovementAbilities}, so a toggle flips in sync on both sides.
 * Spectators are left alone (they noclip and fly natively).
 */
public final class Phasing {

    private Phasing() {}

    public static void preTick(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isSpectator()) return;
        boolean active = MovementAbilities.isActive(entity, Ability.PHASING);
        if (entity.noPhysics != active) entity.noPhysics = active;
    }

    public static void postTick(LivingEntity entity) {
        if (!(entity instanceof Player player) || player.isSpectator() || !entity.noPhysics) return;
        if (entity.level().isClientSide) {
            com.aetherianartificer.townstead.client.origin.ClientPhasing.fly(entity);
        }
    }
}
