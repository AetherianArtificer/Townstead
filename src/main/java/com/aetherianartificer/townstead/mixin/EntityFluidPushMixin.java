package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.ability.Ability;
import com.aetherianartificer.townstead.origin.ability.MovementAbilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes an {@code ignore_water} entity immune to fluid currents. The no-arg
 * {@code isPushedByFluid} gate survives on both branches (NeoForge keeps it in
 * {@code updateFluidHeightAndDoFluidPushing}; Forge is vanilla), so this is a uniform
 * hook (SRG name on Forge). Targets {@code Entity} since the method is declared there,
 * gating to living entities (only they carry genes). Side-aware so the local player's
 * own prediction matches the server. Removes the push, not the swim slowdown; pair
 * with {@code swimming} for full water freedom.
 */
@Mixin(Entity.class)
public abstract class EntityFluidPushMixin {

    //? if neoforge {
    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6063_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$ignoreWater(CallbackInfoReturnable<Boolean> cir) {
        if (((Object) this) instanceof LivingEntity self
                && MovementAbilities.isActive(self, Ability.IGNORE_WATER)) {
            cir.setReturnValue(false);
        }
    }
}
