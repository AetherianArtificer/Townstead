package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.pheno.state.StateForms;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Someone whose state tier says {@code "sleep": false} (a werewolf's beast) does not lie down. */
@Mixin(LivingEntity.class)
public abstract class StateFormSleepMixin {

    //? if neoforge {
    @Inject(method = "startSleeping", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_5802_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$noSleepInForm(BlockPos pos, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide && !StateForms.canSleep(self)) ci.cancel();
    }
}
