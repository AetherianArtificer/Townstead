package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.origin.Animations;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The origin/species pose layer of the animation bridge. It re-asserts the rig's state poses (crouch
 * today) as bone transforms so they survive on top of the Fresh-Animations/EMF base: ordered after
 * EMF and before Emote, a crouch overrides the idle animation while an emote still wins on top.
 *
 * <p>No-op for entities without an alternate rig (normal villagers/players are untouched) and for
 * states a species opts out of via its {@code animations} block. Crouch values mirror vanilla
 * {@code HumanoidModel.setupAnim}; emitted SET so they replace the FA base on these bones, except the
 * arm tuck which is ADD so it rides on top of the current (FA or swing) arm pose.</p>
 */
public final class OriginAnimationSourceAdapter implements AnimationSourceAdapter {

    private static final String ID = "origin";
    private static final AnimationTransform.Operation SET = AnimationTransform.Operation.SET;
    private static final AnimationTransform.Operation ADD = AnimationTransform.Operation.ADD;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        LivingEntity entity = context.entity();
        if (!RigModels.isAlternate(RigModels.rigBaseFor(entity))) return List.of();
        Animations anim = RigModels.animations(entity);
        if (anim.isHumanoid(Animations.State.CROUCH) && entity.isCrouching()) return crouch();
        return List.of();
    }

    /** Vanilla humanoid crouch: torso bends and drops, head/arms/legs follow. */
    private static List<AnimationTransform> crouch() {
        return List.of(
                bend("body", 3.2f, 0.5f),
                transY("head", 4.2f),
                AnimationTransform.rotate("right_arm", 0.4f, 0f, 0f, ADD),
                AnimationTransform.rotate("left_arm", 0.4f, 0f, 0f, ADD),
                transY("right_arm", 5.2f),
                transY("left_arm", 5.2f),
                transYZ("right_leg", 12.2f, 4.0f),
                transYZ("left_leg", 12.2f, 4.0f));
    }

    /** SET the part's y translation and x rotation (other channels left to the base/FA). */
    private static AnimationTransform bend(String target, float y, float xRot) {
        return new AnimationTransform(target, null, y, null, xRot, null, null, null, null, null, null, null, true, false, false, SET);
    }

    private static AnimationTransform transY(String target, float y) {
        return new AnimationTransform(target, null, y, null, null, null, null, null, null, null, null, null, true, false, false, SET);
    }

    private static AnimationTransform transYZ(String target, float y, float z) {
        return new AnimationTransform(target, null, y, z, null, null, null, null, null, null, null, null, true, false, false, SET);
    }
}
