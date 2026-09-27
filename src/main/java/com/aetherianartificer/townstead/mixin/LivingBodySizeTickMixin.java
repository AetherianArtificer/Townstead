package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.rig.RigHitboxes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingBodySizeTickMixin {
    //? if neoforge {
    @Inject(method = "tick", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_8119_", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$refreshBodySize(CallbackInfo ci) {
        RigHitboxes.tick((LivingEntity)(Object)this);
    }
}
