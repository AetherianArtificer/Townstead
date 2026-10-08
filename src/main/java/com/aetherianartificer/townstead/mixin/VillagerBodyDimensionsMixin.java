package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.rig.RigHitboxes;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCA's per-pose query must agree with the cached collision box, including growth and custom rigs. */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerBodyDimensionsMixin {
    //? if neoforge {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6972_", at = @At("RETURN"), cancellable = true, remap = false)
    *///?}
    private void townstead$bodyDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        EntityDimensions size = RigHitboxes.defaultDimensionsFor((VillagerEntityMCA)(Object)this, pose);
        if (size != null) cir.setReturnValue(size);
    }
}
