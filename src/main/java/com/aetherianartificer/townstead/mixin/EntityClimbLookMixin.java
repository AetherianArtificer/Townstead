package com.aetherianartificer.townstead.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spider gravity (input): intercepts the local player's mouse-look ({@link Entity#turn}) so turning stays
 * intuitive while reoriented onto a surface. Vanilla applies the raw delta straight into world-frame
 * yaw/pitch, which feels rotated once the view is tipped; {@link
 * com.aetherianartificer.townstead.client.species.ClimbState} re-applies it in the surface frame instead.
 * Client-only (the only caller of {@code turn} is the local player's mouse), so the client-only helper is
 * never linked server-side. 1.20.1 Forge SRG: {@code m_19884_} turn.
 */
@Mixin(Entity.class)
public abstract class EntityClimbLookMixin {

    //? if neoforge {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_19884_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$reorientLook(double yaw, double pitch, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide) return;
        if (!(self instanceof LivingEntity living)) return;
        if (com.aetherianartificer.townstead.client.species.ClimbState.tryTurn(living, yaw, pitch)) {
            ci.cancel();
        }
    }
}
