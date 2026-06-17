package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ability.Ability;
import com.aetherianartificer.townstead.origin.ability.MovementAbilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The {@code grounded} ability: the bearer is treated as standing on the ground even
 * midair, so {@code on_ground} conditions pass and they can keep jumping (Apoli's
 * {@code grounded}). The per-tick ability scan is cached into a flag once per tick so
 * the hot {@code onGround()} call stays cheap, and is limited to players (the ability
 * is player-only; forcing this on AI mobs would break their pathfinding). Side-aware
 * via {@code MovementAbilities} so the owning client predicts the same result.
 */
@Mixin(Entity.class)
public abstract class EntityGroundedMixin {

    @Unique
    private boolean townstead$grounded;

    //? if neoforge {
    @Inject(method = "tick", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_8119_", at = @At("HEAD"), remap = false)
    *///?}
    private void townstead$refreshGrounded(CallbackInfo ci) {
        townstead$grounded = ((Object) this) instanceof Player player
                && MovementAbilities.isActive(player, Ability.GROUNDED);
    }

    //? if neoforge {
    @Inject(method = "onGround", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_20096_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$forceGrounded(CallbackInfoReturnable<Boolean> cir) {
        if (townstead$grounded) cir.setReturnValue(true);
    }
}
