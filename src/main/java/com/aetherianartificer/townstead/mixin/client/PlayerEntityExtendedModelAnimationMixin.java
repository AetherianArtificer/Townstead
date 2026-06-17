package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.animation.McaAnimationBridge;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityExtendedModel.class)
public abstract class PlayerEntityExtendedModelAnimationMixin<T extends LivingEntity> {
    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$applyAnimationBridge(
            T player,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        McaAnimationBridge.apply(
                player,
                (PlayerEntityExtendedModel<T>) (Object) this,
                limbAngle,
                limbDistance,
                animationProgress,
                headYaw,
                headPitch);
        // hide_feature genes: zero the hidden parts on the player's genetics model too
        // (the villager model handles its own; this covers the player render).
        com.aetherianartificer.townstead.client.origin.HideFeatures.hide(
                (net.minecraft.client.model.HumanoidModel<?>) (Object) this,
                com.aetherianartificer.townstead.client.origin.HideFeatures.hiddenGroups(player));
    }
}
