package com.aetherianartificer.townstead.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

public record AnimationSourceContext<T extends LivingEntity>(
        T entity,
        HumanoidModel<T> model,
        McaAnimationParameters parameters,
        McaRigScale rigScale,
        float limbAngle,
        float limbDistance,
        float animationProgress,
        float headYaw,
        float headPitch
) {
}
