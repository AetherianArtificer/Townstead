package com.aetherianartificer.townstead.client.animation;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.Map;

/** The game's own zombie arm routine; higher-priority activity poses can still use the arms. */
public final class ZombieAnimationSourceAdapter implements AnimationSourceAdapter {
    private final ModelPart left = new ModelPart(List.of(), Map.of());
    private final ModelPart right = new ModelPart(List.of(), Map.of());
    @Override public String id() { return "zombie"; }
    @Override public boolean isAvailable() { return true; }

    @Override public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        if (!GeneAnimations.isZombie(GeneAnimations.active(context.entity()))) return List.of();
        return vanillaTransforms(context);
    }

    List<AnimationTransform> vanillaTransforms(AnimationSourceContext context) {
        if (GeneAnimations.usesHumanoidStatePose(context.entity())) return List.of();
        // Keep the host's eating, sleeping, swimming, flying and riding poses usable.
        var entity = context.entity();
        if (entity.isUsingItem() || entity.isSleeping() || entity.isSwimming()
                || entity.isFallFlying() || entity.isPassenger()) return List.of();
        return arms(context.model().attackTime, context.animationProgress(),
                entity instanceof Mob mob ? mob.isAggressive() : entity.swinging);
    }

    List<AnimationTransform> arms(float attack, float age, boolean aggressive) {
        AnimationUtils.animateZombieArms(left, right, aggressive, attack, age);
        return List.of(
                AnimationTransform.rotate("right_arm", right.xRot, right.yRot, right.zRot, AnimationTransform.Operation.SET),
                AnimationTransform.rotate("left_arm", left.xRot, left.yRot, left.zRot, AnimationTransform.Operation.SET));
    }
}
