package com.aetherianartificer.townstead.mixin.compat.mcacrime;

import com.aetherianartificer.townstead.compat.otectus.CaptureAmnesty;
import com.aetherianartificer.townstead.compat.otectus.CrimeGateCheck;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * A Vampirism capture is a war, not a crime spree. While a capture runs over a village, and for
 * half a minute after, MCA: Crime names no offender for a blow against a villager there, so no
 * Heat, Karma or case follows. Heat a player already holds stays. Only a charge Crime was about
 * to make is dropped, so everything else it judges is untouched.
 */
@Pseudo
@Mixin(targets = CrimeGateCheck.GATE, remap = false)
public abstract class CrimeGateCaptureAmnestyMixin {
    @Inject(method = CrimeGateCheck.OFFENDER, at = @At("RETURN"), cancellable = true, require = 0)
    private static void townstead$captureAmnesty(LivingEntity victim, DamageSource source, ServerLevel level,
            boolean lethal, CallbackInfoReturnable<Optional<ServerPlayer>> cir) {
        if (cir.getReturnValue().isPresent() && CaptureAmnesty.covers(level, victim.blockPosition())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = CrimeGateCheck.NON_DAMAGE_OFFENDER, at = @At("RETURN"), cancellable = true, require = 0)
    private static void townstead$captureAmnestyNonDamage(LivingEntity victim, Entity actor, ServerLevel level,
            CallbackInfoReturnable<Optional<ServerPlayer>> cir) {
        if (cir.getReturnValue().isPresent() && CaptureAmnesty.covers(level, victim.blockPosition())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
