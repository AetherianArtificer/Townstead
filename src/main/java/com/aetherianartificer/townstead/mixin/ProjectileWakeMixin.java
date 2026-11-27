package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.attachment.BodyPoseSim;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A projectile in flight wakes the physics of anyone it could reach. Parity: SRG name on Forge. */
@Mixin(Projectile.class)
public abstract class ProjectileWakeMixin {

    //? if neoforge {
    @Inject(method = "tick", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_8119_", at = @At("HEAD"), remap = false)
    *///?}
    private void townstead$wakeNearby(CallbackInfo ci) {
        BodyPoseSim.onProjectileTick((Projectile) (Object) this);
    }
}
