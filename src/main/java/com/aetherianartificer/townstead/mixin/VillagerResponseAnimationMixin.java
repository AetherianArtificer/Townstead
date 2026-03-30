package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.animation.VillagerResponseAnimation;
import com.aetherianartificer.townstead.animation.VillagerResponseAnimationClientStore;
import com.aetherianartificer.townstead.animation.VillagerResponsePoseLibrary;
import com.aetherianartificer.townstead.animation.gecko.TownsteadVillagerGeckoPoseBridge;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerResponseAnimationMixin<T extends LivingEntity & VillagerLike<T>> extends HumanoidModel<T> {
    private VillagerResponseAnimationMixin(ModelPart root) {
        super(root);
    }

    //? if neoforge {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    //?} else if forge {
    /*@Inject(method = "m_6973_", at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$applyResponseAnimation(
            T villager,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!villager.isAlive() || villager.isSleeping() || villager.isPassenger()) return;

        VillagerResponseAnimation animation = VillagerResponseAnimationClientStore.getActive(villager.getId());
        if (animation == null) return;

        float progress = VillagerResponseAnimationClientStore.getNormalizedProgress(villager.getId(), 0.0f);
        if (progress < 0.0f) return;

        if (TownsteadVillagerGeckoPoseBridge.apply(
                villager,
                limbAngle,
                limbDistance,
                animationProgress - villager.tickCount,
                this.head,
                this.body,
                this.rightArm,
                this.leftArm,
                this.rightLeg,
                this.leftLeg
        )) {
            return;
        }

        VillagerResponsePoseLibrary.apply(
                animation,
                progress,
                animationProgress,
                this.head,
                this.body,
                this.rightArm,
                this.leftArm,
                this.rightLeg,
                this.leftLeg
        );
    }
}
