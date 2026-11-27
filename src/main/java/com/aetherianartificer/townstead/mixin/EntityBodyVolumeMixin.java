package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.attachment.BodyVolumes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a person's attachment volumes cut a move short after the body itself has collided. This runs
 * on both sides, the client for its own player and the server for everyone. It hooks after vanilla's
 * step-up, so a box that clears a step doesn't stop the body climbing it. Parity: SRG name on Forge.
 */
@Mixin(Entity.class)
public abstract class EntityBodyVolumeMixin {

    //? if neoforge {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_20272_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$bodyVolumes(Vec3 requested, CallbackInfoReturnable<Vec3> cir) {
        Vec3 allowed = cir.getReturnValue();
        Vec3 limited = BodyVolumes.limit((Entity) (Object) this, allowed);
        if (limited != allowed) cir.setReturnValue(limited);
    }
}
