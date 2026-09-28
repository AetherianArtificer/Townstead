package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.pheno.action.ItemRetrievals;
import com.aetherianartificer.townstead.pheno.action.Leaps;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingFieldAbilitiesMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void townstead$fieldAbilities(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (!entity.level().isClientSide) {
            Leaps.tick(entity);
            ItemRetrievals.tick(entity);
        }
    }

    @ModifyVariable(method = "causeFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float townstead$leapLanding(float distance) {
        return Leaps.landingDistance((LivingEntity)(Object)this, distance);
    }
}
