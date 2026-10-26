package com.aetherianartificer.townstead.root.locomotion.fly;

import com.aetherianartificer.townstead.root.locomotion.FlightProfile;
import com.aetherianartificer.townstead.root.locomotion.Locomotion;
import com.aetherianartificer.townstead.root.locomotion.Pilot;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoute;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoutePlanner;
import com.aetherianartificer.townstead.root.locomotion.plan.AirSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Flies a hover route with gravity off, driving velocity directly: pixie and sprite
 * flight, precise enough for doorways. Slows on the same constant tau-dot schedule as
 * the elytra pilot. A leg that turns out blocked gets one re-plan; failing that, or with
 * no route at all, the bearer sinks straight down and lands.
 */
public final class HoverPilot implements Pilot {

    private static final double APPROACH = 6.0;
    private static final double MIN_SPEED = 0.05;
    private static final double ARRIVE = 0.35;
    private static final double RESPONSE = 0.35;
    private static final double SINK_SPEED = 0.15;
    private static final int REPLAN_EVERY = 20;
    private static final double REPLAN_SHIFT_SQR = 16.0;
    private static final int STUCK_LIMIT = 20;

    private final FlightProfile profile;
    private final boolean ownsGravity;
    private final Doors doors = new Doors();
    @Nullable private RouteFollower follower;
    @Nullable private BlockPos plannedFor;
    private AirSpace space;
    private int age;
    private int stuck;
    private boolean replannedForStuck;

    public HoverPilot(Mob mob, FlightProfile profile, @Nullable AirRoute route, @Nullable BlockPos destination) {
        this.profile = profile;
        this.plannedFor = destination;
        this.ownsGravity = !mob.isNoGravity();
        this.space = new AirSpace(mob.level(), mob.getBbWidth(), mob.getBbHeight());
        this.follower = route == null ? null : new RouteFollower(route.waypoints());
        mob.setNoGravity(true);
    }

    @Override
    public boolean tick(Mob mob) {
        age++;
        if (!mob.isNoGravity()) mob.setNoGravity(true);
        if (follower == null) return sinkToGround(mob);
        if (age % 40 == 0) space = new AirSpace(mob.level(), mob.getBbWidth(), mob.getBbHeight());
        if (age % REPLAN_EVERY == 0) maybeReplan(mob);
        Vec3 pos = mob.position();
        follower.update(pos);
        double remaining = follower.remaining();
        if (remaining < ARRIVE || pos.distanceTo(follower.end()) < ARRIVE) {
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.3));
            return false;
        }
        double speed = Math.max(MIN_SPEED, profile.style().cruiseSpeed() * Math.min(1.0, Math.sqrt(remaining / APPROACH)));
        Vec3 target = follower.at(follower.progress() + Math.max(1.5, speed * 8.0));
        Vec3 to = target.subtract(pos);
        Vec3 desired = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize().scale(Math.min(speed, to.length()));
        if (!space.bodyFree(pos.add(desired.scale(3.0)), 0.0)) {
            if (++stuck > STUCK_LIMIT) {
                if (replannedForStuck) {
                    follower = null;
                    return true;
                }
                replannedForStuck = true;
                stuck = 0;
                replan(mob, plannedFor != null ? plannedFor : BlockPos.containing(follower.end()));
            }
            desired = desired.scale(0.3).add(0, 0.04, 0);
        } else {
            stuck = 0;
        }
        Vec3 v = mob.getDeltaMovement();
        v = v.add(desired.subtract(v).scale(RESPONSE));
        mob.setDeltaMovement(v);
        mob.hasImpulse = true;
        face(mob, v);
        doors.openAlong(mob, pos.add(desired.scale(6.0)));
        doors.closeBehind(mob);
        return true;
    }

    @Override
    public void stop(Mob mob) {
        if (ownsGravity) mob.setNoGravity(false);
        doors.closeAll(mob);
        mob.resetFallDistance();
    }

    @Override
    public String describe() {
        String where = follower == null ? "sinking to land"
                : String.format("%.0f of %.0f blocks", follower.progress(), follower.length());
        return profile.style().key() + ", " + where + (stuck > 0 ? ", blocked x" + stuck : "");
    }

    /** No route: come straight down, then hand back. */
    private boolean sinkToGround(Mob mob) {
        if (mob.onGround() || mob.isInWater()) return false;
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(v.x * 0.5, -SINK_SPEED, v.z * 0.5);
        return age < 400;
    }

    private void maybeReplan(Mob mob) {
        BlockPos destination = Locomotion.destination(mob);
        if (destination == null || plannedFor == null || destination.distSqr(plannedFor) <= REPLAN_SHIFT_SQR) return;
        replan(mob, destination);
    }

    private void replan(Mob mob, BlockPos destination) {
        if (!PlanThrottle.tryAcquire(mob.level())) return;
        AirRoutePlanner.Result result = AirRoutePlanner.plan(mob, profile, destination);
        if (!result.ok()) return;
        plannedFor = destination;
        follower = new RouteFollower(result.route().waypoints());
    }

    static void face(Mob mob, Vec3 velocity) {
        if (velocity.horizontalDistanceSqr() < 4.0E-4) return;
        float yaw = (float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90f;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setYHeadRot(yaw);
    }
}
