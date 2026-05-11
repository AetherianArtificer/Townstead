package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.animation.BendStateRegistry;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the latest bend angle (recorded by {@code McaAnimationBridge}) to
 * the layer model's wear parts right after MCA's {@code copyPropertiesTo}
 * runs in {@link VillagerLayer#render}.
 *
 * <p>Why: in theory, bendylib's {@code copyTransformExtended} mixin
 * propagates the bend mutator state through {@code ModelPart.copyFrom} which
 * MCA's {@code copyAttributes} calls for each wear-layer pair. In practice
 * the clothing-layer's separate {@code VillagerEntityModelMCA} instance was
 * still rendering its sleeves un-bent on guards with auto-equipped clothing
 * — bendylib's propagation chain has some subtle gap we couldn't pin down.
 * Applying the bend explicitly here, using values we already cached when the
 * bridge bent the main model, is a deterministic fallback.</p>
 */
@Mixin(VillagerLayer.class)
public abstract class VillagerLayerBendMixin<T extends LivingEntity, M extends HumanoidModel<T>> {

    @Shadow(remap = false) @Final public M model;

    @Inject(method = "render", remap = false, at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/HumanoidModel;copyPropertiesTo(Lnet/minecraft/client/model/HumanoidModel;)V",
            shift = At.Shift.AFTER
    ))
    private void townstead$reapplyBendToLayerModel(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            T entity,
            float limbAngle,
            float limbDistance,
            float partialTicks,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!(model instanceof VillagerEntityModelMCA<?> mcaModel)) return;
        // On 1.20.1's player-animation-lib 1.0.x (no bendylib), bend is applied
        // as additive rotation on the part itself, which DOES survive
        // copyPropertiesTo/copyFrom — the layer's parts already have the bent
        // xRot/zRot. Re-applying here would double the rotation.
        if (!EmoteReflection.isBendylibAvailable()) return;
        applyStoredBend(entity, "left_arm", mcaModel.leftArmwear);
        applyStoredBend(entity, "right_arm", mcaModel.rightArmwear);
        applyStoredBend(entity, "left_leg", mcaModel.leftLegwear);
        applyStoredBend(entity, "right_leg", mcaModel.rightLegwear);
        // Inner parts also need re-bend on the LAYER model because
        // copyPropertiesTo's per-part copyFrom may not have propagated bend.
        applyStoredBend(entity, "left_arm", mcaModel.leftArm);
        applyStoredBend(entity, "right_arm", mcaModel.rightArm);
        applyStoredBend(entity, "left_leg", mcaModel.leftLeg);
        applyStoredBend(entity, "right_leg", mcaModel.rightLeg);
    }

    private static void applyStoredBend(LivingEntity entity, String partName, Object part) {
        if (part == null) return;
        BendStateRegistry.State state = BendStateRegistry.get(entity.getUUID(), partName);
        if (state == null) return;
        EmoteReflection.applyBend(part, state.axis(), state.angle());
    }
}
