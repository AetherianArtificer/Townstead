package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.ability.MovementAbilities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Fish-style players under water: free 3D swimming in the direction they look, with no
 * sinking when still, in the swimming pose. The controlling client moves the body
 * ({@code ClientSwim}); both sides hold the swimming pose so they agree on the hitbox in
 * a tight tunnel. Villagers swim through {@code SwimPilot} instead.
 */
public final class SwimPhysics {

    private SwimPhysics() {}

    /** After the physics tick, for players. */
    public static void afterTravel(LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.isUnderWater() || player.isPassenger()
                || player.isSpectator() || player.getAbilities().flying || !swimsLikeFish(player)) {
            return;
        }
        player.setSwimming(true);
        if (player.level().isClientSide) com.aetherianartificer.townstead.client.root.ClientSwim.afterTravel(player);
    }

    /** A water gene in the fish style. Side-aware, so the controlling client predicts the server. */
    public static boolean swimsLikeFish(LivingEntity entity) {
        if (!ExpressedGenes.canCarry(entity)) return false;
        if (!MovementAbilities.isActive(entity, Ability.WATER_BREATHING) && !MovementAbilities.isActive(entity, Ability.SWIMMING)) {
            return false;
        }
        if (entity.level().isClientSide) {
            return com.aetherianartificer.townstead.client.root.ClientAbilities.swimsLikeFish(entity);
        }
        SwimProfile profile = SwimProfile.of(entity);
        return profile != null && profile.style() == SwimStyle.FISH;
    }
}
