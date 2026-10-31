package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.rest.RestEvents;
import com.aetherianartificer.townstead.rest.RestSpot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player in a rest spot that does not skip the night (a hammock, a nap) never counts as sleeping through it. */
@Mixin(Player.class)
public abstract class RestSpotNightSkipMixin {
    //? if neoforge {
    @Inject(method = "isSleepingLongEnough", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_36317_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$restSpotSkipsNight(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Player self = (Player) (Object) this;
        if (self.level().isClientSide) return;
        RestSpot spot = RestEvents.sleepingIn(self);
        if (spot != null && !spot.skipsNight()) cir.setReturnValue(false);
    }
}
