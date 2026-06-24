package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.ClimbState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A clung climber pressed against a ceiling has no room for the 1.8-tall standing pose, so vanilla forces it
 * into the crawl/SWIMMING pose. {@code PlayerRenderer.setupRotations} then lays the body flat along a -90
 * degree swim pitch (its whole swim branch is gated on {@code getSwimAmount > 0}, with the
 * {@code isVisuallySwimming} translate nested inside it). That pitch stacks on top of the climb tilt
 * ({@link com.aetherianartificer.townstead.client.species.ClimbRender}) and leaves the body perpendicular to
 * the ceiling (a "T"). While clung we own the body orientation, so {@code getSwimAmount} is forced to 0,
 * which skips the entire swim branch (pitch + offset). We do NOT touch {@code isVisuallySwimming}: it is
 * render-AND-physics state, and overriding it broke first-person climb control. {@code getSwimAmount} is
 * render-only here, so suppressing it changes nothing physical (and vanilla swim movement never runs while
 * clung anyway, since the climb controller cancels {@code travel}).
 *
 * <p>1.20.1 Forge SRG: {@code m_20998_} getSwimAmount.</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwimClimbMixin {

    //? if neoforge {
    @Inject(method = "getSwimAmount", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_20998_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$noSwimPitchWhileClung(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (townstead$clung()) cir.setReturnValue(0f);
    }

    private boolean townstead$clung() {
        LivingEntity self = (LivingEntity) (Object) this;
        return self.level().isClientSide && ClimbState.factor(self.getId()) > 0f;
    }
}
