package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses MCA's villager body layers (skin/face/clothing/hair, all {@link VillagerLayer}) for a
 * villager with a supported alternate species rig, so only {@link SpeciesRigLayer} draws its body.
 * The villager body is rendered by these layers (the base model is null-rendered), so cancelling
 * them blanks the villager and the rig model stands in.
 */
@Mixin(VillagerLayer.class)
public abstract class VillagerBodyLayerSuppressMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void townstead$suppressForAltRig(PoseStack transform, MultiBufferSource provider, int light,
                                             LivingEntity villager, float limbAngle, float limbDistance,
                                             float tickDelta, float animationProgress, float headYaw,
                                             float headPitch, CallbackInfo ci) {
        if (RigModels.isAlternate(RigModels.rigBaseFor(villager))) ci.cancel();
    }
}
