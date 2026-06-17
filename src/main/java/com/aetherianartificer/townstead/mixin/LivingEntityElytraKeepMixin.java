package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ability.Ability;
import com.aetherianartificer.townstead.origin.ability.MovementAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps an {@code elytra_flight} race gliding without an elytra: vanilla's
 * {@code updateFallFlying} re-checks the chest slot every tick and clears the glide
 * when there's no elytra, so for a gene-gliding entity (already airborne and
 * fall-flying) this cancels that re-check, leaving the flag set. Vanilla still clears
 * it on landing / in water (those conditions fail here). Parity: same hook both branches.
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class LivingEntityElytraKeepMixin {

    //? if neoforge {
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_21323_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$keepGliding(CallbackInfo ci) {
        net.minecraft.world.entity.LivingEntity self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        if (!self.isFallFlying()) return;
        if (self.onGround() || self.isInWater() || self.isPassenger()) return;
        if (!MovementAbilities.isActive(self, Ability.ELYTRA_FLIGHT)) return;
        ci.cancel();
    }
}
