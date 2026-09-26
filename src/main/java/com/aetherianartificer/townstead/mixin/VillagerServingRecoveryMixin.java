package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Newer MCA builds heal by eating; they must not consume a reserved beverage between sips. */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerServingRecoveryMixin {
    @Inject(method = "canRecoverHealthNow", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void townstead$holdServing(CallbackInfoReturnable<Boolean> cir) {
        if (VillagerConsumptionManager.isHoldingServing((VillagerEntityMCA) (Object) this)) cir.setReturnValue(false);
    }
}
