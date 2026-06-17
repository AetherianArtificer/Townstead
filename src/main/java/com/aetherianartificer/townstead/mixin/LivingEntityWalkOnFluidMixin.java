package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ability.Ability;
import com.aetherianartificer.townstead.origin.ability.FluidWalking;
import com.aetherianartificer.townstead.origin.ability.MovementAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a {@code walk_on_fluid} entity at the fluid surface every physics tick.
 * Runs on both sides of {@code aiStep} (server for villagers, owning client for the
 * local player); {@code MovementAbilities} resolves the ability per side so client
 * prediction and server stay in agreement. Parity: same hook both branches (SRG
 * name on Forge).
 */
@Mixin(net.minecraft.world.entity.LivingEntity.class)
public abstract class LivingEntityWalkOnFluidMixin {

    //? if neoforge {
    @Inject(method = "aiStep", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_8107_", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$walkOnFluid(CallbackInfo ci) {
        net.minecraft.world.entity.LivingEntity self = (net.minecraft.world.entity.LivingEntity) (Object) this;
        if (MovementAbilities.isActive(self, Ability.WALK_ON_FLUID)) {
            FluidWalking.clamp(self);
        }
    }
}
