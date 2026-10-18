package com.aetherianartificer.townstead.mixin.compat.temperature;

import com.aetherianartificer.townstead.temperature.HearthAirRule;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the span in which a Cold Sweat hearth hands out its Warmth and Frigidness. */
@Pseudo
@Mixin(targets = "com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity", remap = false)
public class ColdSweatHearthEffectMixin {
    @Inject(method = "insulateEntity", at = @At("HEAD"), require = 0)
    private void townstead$enter(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        HearthAirRule.enter();
    }

    @Inject(method = "insulateEntity", at = @At("RETURN"), require = 0)
    private void townstead$leave(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        HearthAirRule.leave();
    }
}
