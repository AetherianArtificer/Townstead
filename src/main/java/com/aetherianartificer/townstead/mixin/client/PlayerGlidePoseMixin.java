package com.aetherianartificer.townstead.mixin.client;

import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Calms a gliding player's limbs, mirroring {@code VillagerGlidePoseMixin}: MCA's
 * player model extends vanilla {@code PlayerModel} (NOT the villager base model, so
 * the villager hook never applies here), and vanilla's fall-fly damping divides the
 * walk swing by flight speed cubed — near stall speed that divisor is ~1 and the
 * full run cycle plays mid-air. Legs straight, arms swept slightly back; the wear
 * layers (sleeves/pants) re-copy afterwards because vanilla copies them before TAIL.
 */
// Apply this base pose before the default-priority animation bridge's TAIL hook,
// so the selected EMF provider can replace it rather than being overwritten by it.
@Mixin(value = PlayerEntityExtendedModel.class, priority = 1100)
public abstract class PlayerGlidePoseMixin<T extends LivingEntity> {

    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"), require = 1)
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"), require = 1)
    *///?}
    private void townstead$glidePose(T entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (!entity.isFallFlying()) return;
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
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
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }
}
