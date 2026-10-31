package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.tick.GuardLeash;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCA picks and keeps a guard's foe through this test; a foe past the village leash fails it. */
@Mixin(GuardEnemiesSensor.class)
public abstract class GuardEnemyLeashMixin {
    @Inject(method = "isGuardEnemy", at = @At("RETURN"), cancellable = true, remap = false)
    private static void townstead$leash(LivingEntity entity, LivingEntity guard, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && guard != null && GuardLeash.beyond(guard, entity)) cir.setReturnValue(false);
    }
}
