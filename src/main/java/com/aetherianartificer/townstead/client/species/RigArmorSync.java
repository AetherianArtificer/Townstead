package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.origin.Animations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Drives MCA's host villager body model from the species rig's pose, so the host's armor (which
 * MCA's {@code HumanoidArmorLayer} poses by copying from the body model, not from our rig) tracks the
 * skeleton instead of the villager. Without this the armor animates from the villager's
 * {@code setupAnim} while the visible rig animates from the skeleton's, and the two drift apart into a
 * floating, out-of-sync "second body".
 *
 * <p>This syncs the <em>animation</em>, not the shape: the armor stays villager-proportioned (a
 * separate, future "rig-fitted armor" step). The visible host body itself is suppressed for alternate
 * rigs by {@code VillagerBodyLayerSuppressMixin}, so copying onto its bones is harmless beyond
 * feeding the armor layer.</p>
 */
public final class RigArmorSync {

    private RigArmorSync() {}

    /**
     * If the entity has an alternate species rig, pose the shared rig model exactly as
     * {@link SpeciesRigLayer} does and copy its humanoid bones onto the given host body model, then
     * return true (the caller should skip its own bridge pass on the host). Returns false for a
     * normal villager, leaving it to MCA's pose plus the bridge.
     *
     * <p>Called from the tail of the host body model's {@code setupAnim}, which runs once before any
     * layer, so the armor layer reads the rig pose this frame (no lag). {@code partialTick} is derived
     * from {@code animationProgress - tickCount} the same way {@code CemAnimationProgram} does, since
     * {@code setupAnim} is not handed it.</p>
     */
    public static boolean syncHostToRig(LivingEntity entity, HumanoidModel<?> host,
                                        float limbSwing, float limbSwingAmount, float ageInTicks,
                                        float netHeadYaw, float headPitch) {
        String rigBase = RigModels.rigBaseFor(entity);
        if (!RigModels.isAlternate(rigBase)) return false;
        // A non-humanoid rig has no humanoid host pose to copy and wears no fitted armor. If it declares
        // a back anchor, pose the host body bone there so back-worn layers (backpack, cape) land on the
        // rig's back; then skip the humanoid bridge so that pose stands. Otherwise leave the host alone.
        if (RigModels.isGeneric(rigBase)) {
            com.aetherianartificer.townstead.origin.rig.RigDefinition def = RigModels.definition(rigBase);
            return def != null && RigWearables.anchor(host, def, netHeadYaw, headPitch);
        }
        HumanoidModel<LivingEntity> rig = RigModels.model(rigBase);
        if (rig == null) return false;
        Animations anim = RigModels.animations(entity);
        float partialTick = Mth.clamp(ageInTicks - entity.tickCount, 0f, 1f);
        SpeciesRigLayer.poseRig(rig, entity, anim, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick);
        copyHumanoid(rig, host);
        return true;
    }

    /** Copy the standard humanoid bone transforms (rotation + animated offset) from rig to host. */
    private static void copyHumanoid(HumanoidModel<LivingEntity> from, HumanoidModel<?> to) {
        to.head.copyFrom(from.head);
        to.hat.copyFrom(from.hat);
        to.body.copyFrom(from.body);
        to.rightArm.copyFrom(from.rightArm);
        to.leftArm.copyFrom(from.leftArm);
        to.rightLeg.copyFrom(from.rightLeg);
        to.leftLeg.copyFrom(from.leftLeg);
    }
}
