package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.villager.DeathGuard;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA's {@code die} clears equipment and logs the death before calling the vanilla one, then drops
 * the inventory, tells the family and moves the villager out after it, whether or not the death
 * event was canceled. A villager Townstead saves never reaches any of that.
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerDeathGuardMixin {
    //? if neoforge {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6667_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$guardDeath(DamageSource source, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (self.level().isClientSide) return;
        if (DeathGuard.survives(self, source)) ci.cancel();
    }
}
