package com.aetherianartificer.townstead.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class AnimationTargetMap<T extends LivingEntity> {
    private final Map<String, ModelPart> targets = new HashMap<>();

    private AnimationTargetMap(HumanoidModel<T> model) {
        targets.put("head", model.head);
        targets.put("headwear", model.hat);
        targets.put("body", model.body);
        targets.put("right_arm", model.rightArm);
        targets.put("left_arm", model.leftArm);
        targets.put("right_leg", model.rightLeg);
        targets.put("left_leg", model.leftLeg);
    }

    public static <T extends LivingEntity> AnimationTargetMap<T> forMcaModel(HumanoidModel<T> model) {
        return new AnimationTargetMap<>(model);
    }

    public Optional<ModelPart> resolve(String target) {
        return Optional.ofNullable(targets.get(target));
    }
}
