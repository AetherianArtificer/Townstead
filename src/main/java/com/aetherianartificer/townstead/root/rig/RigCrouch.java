package com.aetherianartificer.townstead.root.rig;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ducks a rigged villager under a ceiling too low to stand in, and stands it back up when the room is
 * there again. Vanilla mobs never change pose, so this is the whole trigger: set {@link Pose#CROUCHING}
 * and everything downstream follows for free. {@code setPose} syncs through {@code DATA_POSE}, which runs
 * {@code refreshDimensions} on BOTH sides, so the {@code EntityEvent.Size} hook swaps in the rig's crouch
 * box; {@code isCrouching()} is a plain pose check, so a generic rig's authored {@code poses.crouch} and
 * MCA's humanoid sneak pose both engage with no extra wiring.
 *
 * <p>Reactive only, by design: it ducks a villager that is ALREADY somewhere tight, it does not make
 * pathfinding plan through gaps only a crouched body fits. That distinction is what keeps this cheap —
 * path clearance is baked once per path from {@code getBbHeight()}, so a crouch-aware planner would have
 * to commit every path to the crouched height and then guarantee the pose flips at exactly the right
 * node. {@link RigHitboxes#DOOR_SAFE_HEIGHT} handles the case that actually mattered (a tall rig getting
 * through its own front door) without any of that.</p>
 *
 * <p>Only entities whose rig declares a hitbox take part; a plain MCA villager resolves no crouch box, so
 * it is left exactly as it was.</p>
 */
public final class RigCrouch {

    private RigCrouch() {}

    // Probe cadence in ticks. A fit test is ~12 block lookups, but this runs per villager, so keep it well
    // off the 20 Hz path; the stagger by entity id spreads a village's villagers across the window instead
    // of spiking them all on the same tick. Half a second is plenty for a reactive duck.
    private static final int PROBE_INTERVAL = 10;

    // Stand-up needs this much more headroom than staying stood needs, so a villager parked right on the
    // threshold (or jittering by a few hundredths) does not flicker between the two poses.
    private static final float STAND_MARGIN = 0.05f;

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        if ((entity.tickCount + entity.getId()) % PROBE_INTERVAL != 0) return;
        // Sleeping, riding and dying own the pose; never fight another owner for it.
        if (entity.isSleeping() || entity.isPassenger() || !entity.isAlive()) return;
        boolean crouching = entity.getPose() == Pose.CROUCHING;
        // A villager standing still under a ceiling it already fits cannot have changed its answer, so skip
        // the probe entirely -- which is most of a village most of the time (work sites, gossip, idling).
        // Read off vanilla's own previous-tick position, so this costs nothing and caches nothing. A crouched
        // one still probes while stationary, otherwise it could never notice the ceiling being taken away.
        if (!crouching && !moved(entity)) return;

        EntityDimensions crouched = RigHitboxes.dimensionsFor(entity, Pose.CROUCHING);
        EntityDimensions standing = RigHitboxes.dimensionsFor(entity, Pose.STANDING);
        if (crouched == null || standing == null) return;
        if (height(crouched) >= height(standing)) return; // nothing to gain by ducking

        if (crouching) {
            if (fits(entity, standing, STAND_MARGIN)) entity.setPose(Pose.STANDING);
        } else if (!fits(entity, standing, 0f) && fits(entity, crouched, 0f)) {
            entity.setPose(Pose.CROUCHING);
        }
    }

    /** {@code EntityDimensions} is a record from 1.20.5 on and a plain field-carrying class before it. */
    private static float height(EntityDimensions dims) {
        //? if neoforge {
        return dims.height();
        //?} else {
        /*return dims.height;
        *///?}
    }

    private static boolean moved(LivingEntity entity) {
        return entity.getX() != entity.xOld || entity.getY() != entity.yOld || entity.getZ() != entity.zOld;
    }

    /**
     * Whether {@code dims} (grown by {@code margin} of headroom) is clear of BLOCKS where the entity stands.
     * Deliberately not {@code noCollision}: that also runs an entity-section query and allocates a shape list
     * per call, and another villager standing nearby is not a reason to duck.
     */
    private static boolean fits(LivingEntity entity, EntityDimensions dims, float margin) {
        AABB box = dims.makeBoundingBox(entity.position()).deflate(1.0E-7);
        if (margin > 0f) box = box.setMaxY(box.maxY + margin);
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, box)) {
            if (!shape.isEmpty()) return false;
        }
        return true;
    }
}
