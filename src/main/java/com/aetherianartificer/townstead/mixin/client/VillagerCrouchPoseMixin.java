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
 * Draws a crouching villager crouched. {@code HumanoidModel.setupAnim} already has the full sneak pose,
 * gated on its {@code crouching} flag, but vanilla only ever sets that flag in {@code PlayerRenderer} --
 * no vanilla mob crouches, so no mob renderer bothers. Now that {@code RigCrouch} can put a villager in
 * {@link net.minecraft.world.entity.Pose#CROUCHING}, the flag has to be fed for the pose to be visible.
 *
 * <p>HEAD, not TAIL like the glide hook: MCA's {@code setupAnim} calls {@code super.setupAnim} near its
 * start, and that is what reads the flag, so setting it afterwards would land a frame late. Nothing MCA
 * does after the super call touches the bones the crouch branch moves. Layer models inherit it through
 * {@code copyPropertiesTo}. Generic (non-humanoid) rigs do not come through here at all -- they take their
 * authored {@code poses.crouch} in {@code SpeciesRigLayer}, off the same pose flag.</p>
 */
@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerCrouchPoseMixin<T extends LivingEntity & VillagerLike<T>> {

    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("HEAD"), require = 1)
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("HEAD"), require = 1)
    *///?}
    private void townstead$crouchPose(T entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        ((HumanoidModel<?>) (Object) this).crouching = entity.isCrouching();
    }
}
