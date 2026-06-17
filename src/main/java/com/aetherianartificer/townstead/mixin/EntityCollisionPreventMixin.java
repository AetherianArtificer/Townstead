package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.gene.types.PreventGeneType.What;
import com.aetherianartificer.townstead.origin.prevent.Prevents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a {@code prevent entity_collision} bearer non-pushable, so mobs stop shoving
 * it. {@code isPushable} is declared on {@code Entity} (SRG name on Forge). The gene
 * is read from the server-side genotype, so this fully covers the common case (a mob's
 * server-side push loop checks the target's {@code isPushable}); the bearer walking
 * into mobs client-side is a follow-up needing the per-entity catalog sync.
 */
@Mixin(Entity.class)
public abstract class EntityCollisionPreventMixin {

    //? if neoforge {
    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6094_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$preventEntityCollision(CallbackInfoReturnable<Boolean> cir) {
        if (((Object) this) instanceof LivingEntity self
                && !self.level().isClientSide
                && Prevents.prevents(self, What.ENTITY_COLLISION)) {
            cir.setReturnValue(false);
        }
    }
}
