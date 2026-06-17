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
        String rigBase = com.aetherianartificer.townstead.client.species.RigModels.rigBaseFor(player);
        boolean generic = com.aetherianartificer.townstead.client.species.RigModels.isGeneric(rigBase);
        // Hide the host's mis-fitting boots on a non-humanoid rig by zeroing the leg scale (the worn
        // armor copies it); reset it for a normal player so the shared model does not leak the zero.
        com.aetherianartificer.townstead.client.species.RigWearables.suppressHostBoots(
                (net.minecraft.client.model.HumanoidModel<?>) (Object) this, generic);
        // For a non-humanoid rig, re-pose the body bone onto the rig's back so back-worn layers
        // (Backpacked, cape, elytra) sit on the creature's back instead of the default humanoid chest.
        if (generic) {
            com.aetherianartificer.townstead.origin.rig.RigDefinition def =
                    com.aetherianartificer.townstead.client.species.RigModels.definition(rigBase);
            if (def != null) {
                com.aetherianartificer.townstead.client.species.RigWearables.anchor(
                        (net.minecraft.client.model.HumanoidModel<?>) (Object) this, def, headYaw, headPitch);
            }
        }
    }
}
