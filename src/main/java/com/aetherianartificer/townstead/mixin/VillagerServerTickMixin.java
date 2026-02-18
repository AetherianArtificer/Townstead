package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.tick.VillagerServerTickDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityMCA.class)
public abstract class VillagerServerTickMixin {

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void townstead$serverTick(CallbackInfo ci) {
        VillagerServerTickDispatcher.tick((VillagerEntityMCA) (Object) this);
    }
}
