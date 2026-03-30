package com.aetherianartificer.townstead.animation.gecko;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
//? if neoforge {
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
//?} else if forge {
/*import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;*/
//?}

import java.util.Optional;

public final class TownsteadVillagerGeckoPoseBridge {
    private static final TownsteadVillagerGeoModel MODEL = new TownsteadVillagerGeoModel();

    private TownsteadVillagerGeckoPoseBridge() {}

    public static boolean apply(
            Entity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            ModelPart head,
            ModelPart body,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart rightLeg,
            ModelPart leftLeg
    ) {
        TownsteadVillagerReplacedGeoAnimatable animatable = TownsteadGeckoClient.resolveAnimatable(entity);
        if (animatable == null) return false;

        MODEL.getBakedModel(MODEL.getModelResource(animatable));

        AnimationState<TownsteadVillagerReplacedGeoAnimatable> animationState =
                new AnimationState<>(animatable, limbSwing, limbSwingAmount, partialTick, limbSwingAmount > 1.0E-3f);
        //? if neoforge {
        MODEL.handleAnimations(animatable, entity.getId(), animationState, partialTick);
        //?} else if forge {
        /*MODEL.handleAnimations(animatable, entity.getId(), animationState);*/
        //?}

        boolean applied = false;
        applied |= applyRotation("head", head);
        applied |= applyRotation("body", body);
        applied |= applyRotation("right_arm", rightArm);
        applied |= applyRotation("left_arm", leftArm);
        applied |= applyRotation("right_leg", rightLeg);
        applied |= applyRotation("left_leg", leftLeg);

        return applied;
    }

    private static boolean applyRotation(String boneName, ModelPart part) {
        Optional<GeoBone> bone = MODEL.getBone(boneName);
        if (bone.isEmpty()) return false;

        GeoBone geoBone = bone.get();
        part.xRot = -geoBone.getRotX();
        part.yRot = -geoBone.getRotY();
        part.zRot = -geoBone.getRotZ();
        return true;
    }
}
