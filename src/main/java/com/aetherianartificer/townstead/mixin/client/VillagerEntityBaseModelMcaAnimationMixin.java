package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.animation.McaAnimationBridge;
import com.aetherianartificer.townstead.client.species.RigArmorSync;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerEntityBaseModelMcaAnimationMixin<T extends LivingEntity & VillagerLike<T>> {
    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$applyAnimationBridge(
            T entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        // For an alternate-rig villager, drive this host body model from the rig pose so the armor
        // layer (which copies from here) tracks the skeleton. Runs only for the host body model
        // (VillagerEntityModelMCA), never the villager-shaped armor models, and replaces the bridge
        // pass since the synced pose already carries it.
        if ((Object) this instanceof VillagerEntityModelMCA
                && RigArmorSync.syncHostToRig(entity, (VillagerEntityModelMCA<T>) (Object) this,
                        limbAngle, limbDistance, animationProgress, headYaw, headPitch)) {
            return;
        }
        McaAnimationBridge.apply(
                entity,
                (VillagerEntityBaseModelMCA<T>) (Object) this,
                limbAngle,
                limbDistance,
                animationProgress,
                headYaw,
                headPitch);
    }
}
