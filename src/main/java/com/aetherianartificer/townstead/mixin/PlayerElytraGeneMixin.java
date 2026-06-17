package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ability.Abilities;
import com.aetherianartificer.townstead.origin.ability.Ability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets an {@code elytra_flight} race start gliding without wearing an elytra: when
 * {@code tryToStartFallFlying} would fail, allow it (and start the glide) if the
 * player is airborne and the ability is active. The {@code LivingEntityElytraKeepMixin}
 * keeps the glide going. Parity: same hook on both branches (SRG name on Forge).
 */
@Mixin(net.minecraft.world.entity.player.Player.class)
public abstract class PlayerElytraGeneMixin {

    //? if neoforge {
    @Shadow public abstract void startFallFlying();
    //?} else {
    /*@Shadow(remap = false) public abstract void m_36320_();
    *///?}

    //? if neoforge {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_36319_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$elytraGene(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.world.entity.player.Player self = (net.minecraft.world.entity.player.Player) (Object) this;
        if (self.level().isClientSide) return;
        if (self.onGround() || self.isFallFlying() || self.isInWater()) return;
        if (!Abilities.isActive(self, Ability.ELYTRA_FLIGHT)) return;
        //? if neoforge {
        this.startFallFlying();
        //?} else {
        /*this.m_36320_();
        *///?}
        cir.setReturnValue(true);
    }
}
