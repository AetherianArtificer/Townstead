package com.aetherianartificer.townstead.mixin.client;

import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Calms the limbs while fall-flying. Vanilla {@code HumanoidModel} damps the walk
 * swing by flight speed during a glide; MCA's model reimplements {@code setupAnim}
 * without that branch, so a gliding bearer windmills like a sprinter. Legs go
 * straight, arms sweep slightly back along the (renderer-pitched) body. Runs at
 * TAIL like the proportions hook; layer models inherit via copyPropertiesTo.
 */
@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerGlidePoseMixin<T extends LivingEntity & VillagerLike<T>> {

    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"), expect = 1)
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"), expect = 1)
    *///?}
    private void townstead$glidePose(T entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!entity.isFallFlying()) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        model.leftLeg.xRot = 0f;
        model.rightLeg.xRot = 0f;
        model.leftLeg.yRot = 0f;
        model.rightLeg.yRot = 0f;
        model.leftLeg.zRot = 0.02f;
        model.rightLeg.zRot = -0.02f;
        model.leftArm.xRot = 0.4f;
        model.rightArm.xRot = 0.4f;
        model.leftArm.yRot = 0f;
        model.rightArm.yRot = 0f;
        model.leftArm.zRot = -0.12f;
        model.rightArm.zRot = 0.12f;
    }
}
