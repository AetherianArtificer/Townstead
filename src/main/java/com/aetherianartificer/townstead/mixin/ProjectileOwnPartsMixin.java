package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.attachment.AttachmentPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A shot passes through its own shooter's parts, as it does their body. Parity: SRG name on Forge. */
@Mixin(Projectile.class)
public abstract class ProjectileOwnPartsMixin {

    //? if neoforge {
    @Inject(method = "canHitEntity", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_5603_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$passOwnParts(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && target instanceof AttachmentPart part
                && part.getParent() == ((Projectile) (Object) this).getOwner()) {
            cir.setReturnValue(false);
        }
    }
}
