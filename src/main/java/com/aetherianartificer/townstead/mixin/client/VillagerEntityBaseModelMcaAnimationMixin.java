package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.animation.McaAnimationBridge;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerEntityBaseModelMcaAnimationMixin<T extends LivingEntity & VillagerLike<T>> {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"))
    private void townstead$applyAnimationBridge(
            T entity,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
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
