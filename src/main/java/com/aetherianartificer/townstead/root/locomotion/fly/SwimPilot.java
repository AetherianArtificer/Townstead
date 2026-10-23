package com.aetherianartificer.townstead.root.locomotion.fly;

import com.aetherianartificer.townstead.root.locomotion.Locomotion;
import com.aetherianartificer.townstead.root.locomotion.Pilot;
import com.aetherianartificer.townstead.root.locomotion.SwimProfile;
import com.aetherianartificer.townstead.root.locomotion.plan.SwimRoute;
import com.aetherianartificer.townstead.root.locomotion.plan.SwimRoutePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Swims a planned route through water, driving velocity directly (water travel moves the
 * body by its velocity and then drags it, so setting it each tick steers cleanly). Slows
 * on the same constant tau-dot schedule as the fliers on arrival. A swimmer holding its
 * breath breaks off for the surface when its air runs low and resumes once it has
 * breathed. Blocked twice, or beached, it hands the body back to MCA. A swimmer sent
 * somewhere to rest ({@code linger}) holds there, under water, until the rest is over or
 * it has somewhere else to be.
 */
public final class SwimPilot implements Pilot {

    private static final double APPROACH = 4.0;
    private static final double MIN_SPEED = 0.04;
    private static final double ARRIVE = 0.6;
    private static final double RESPONSE = 0.35;
    private static final double SURFACE_SPEED = 0.12;
    /** Head for air below this share of a full breath; carry on above the second. */
    private static final double LOW_AIR = 0.25;
    private static final double BREATHED = 0.8;
    private static final int REPLAN_EVERY = 20;
    private static final double REPLAN_SHIFT_SQR = 16.0;
    private static final int STUCK_LIMIT = 20;
    private static final int DRY_LIMIT = 10;

    private final SwimProfile profile;
    private final Doors doors = new Doors();
    private RouteFollower follower;
    @Nullable private BlockPos plannedFor;
    private int age;
    private int stuck;
    private int dry;
    private boolean replannedForStuck;
    private boolean surfacing;
    private Vec3 lastPos;
    private int linger;
    private boolean resting;

    /** @param linger ticks to hold at the end of the route (0 hands back on arrival) */
    public SwimPilot(Mob mob, SwimProfile profile, SwimRoute route, @Nullable BlockPos destination, int linger) {
        this.profile = profile;
        this.linger = linger;
        this.plannedFor = destination;
        this.follower = new RouteFollower(route.waypoints());
        this.lastPos = mob.position();
    }

    @Override
    public boolean tick(Mob mob) {
        age++;
        if (!mob.isInWater()) {
            if (++dry > DRY_LIMIT) return false;
        } else {
            dry = 0;
        }
        if (age % REPLAN_EVERY == 0) maybeReplan(mob);
        Vec3 pos = mob.position();
        if (needsAir(mob)) return surface(mob);
        if (resting) return rest(mob, pos);
        follower.update(pos);
        double remaining = follower.remaining();
        if (remaining < ARRIVE || pos.distanceTo(follower.end()) < ARRIVE) {
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.3));
            if (linger <= 0) return false;
            resting = true;
            return true;
        }
        double cruise = profile.style().swimSpeed();
        double speed = Math.max(MIN_SPEED, cruise * Math.min(1.0, Math.sqrt(remaining / APPROACH)));
        Vec3 target = follower.at(follower.progress() + Math.max(1.5, speed * 8.0));
        Vec3 to = target.subtract(pos);
        Vec3 desired = to.lengthSqr() < 1.0E-6 ? Vec3.ZERO : to.normalize().scale(Math.min(speed, to.length()));
        if (pos.distanceToSqr(lastPos) < 1.0E-3 && desired.lengthSqr() > 1.0E-4) {
            if (++stuck > STUCK_LIMIT) {
                if (replannedForStuck) return false;
                replannedForStuck = true;
                stuck = 0;
                replan(mob, plannedFor != null ? plannedFor : BlockPos.containing(follower.end()));
            }
        } else {
            stuck = 0;
        }
        lastPos = pos;
        steer(mob, desired);
        doors.openAlong(mob, pos.add(desired.scale(6.0)));
        doors.closeBehind(mob);
        return true;
    }

    /** Hold the spot, drifting back to it, until the rest runs out or somewhere else calls. */
    private boolean rest(Mob mob, Vec3 pos) {
        if (--linger <= 0) return false;
        BlockPos destination = Locomotion.destination(mob);
        if (destination != null && plannedFor != null && destination.distSqr(plannedFor) > REPLAN_SHIFT_SQR) return false;
        Vec3 to = follower.end().subtract(pos);
        steer(mob, to.lengthSqr() < 0.04 ? Vec3.ZERO : to.scale(0.1));
        return true;
    }

    @Override
    public void stop(Mob mob) {
        doors.closeAll(mob);
    }

    @Override
    public String describe() {
        return profile.describe() + String.format(", %.0f of %.0f blocks", follower.progress(), follower.length())
                + (resting ? ", resting " + linger + "t" : "")
                + (surfacing ? ", surfacing for air" : "") + (stuck > 0 ? ", blocked x" + stuck : "");
    }

    private boolean needsAir(Mob mob) {
        if (profile.breathes()) return false;
        int max = Math.max(1, mob.getMaxAirSupply());
        double air = (double) mob.getAirSupply() / max;
        if (!surfacing && air < LOW_AIR) surfacing = true;
        if (surfacing && air >= BREATHED) surfacing = false;
        return surfacing;
    }

    /** Straight up until the head clears the water, then tread water while the breath comes back. */
    private boolean surface(Mob mob) {
        boolean underwater = mob.isUnderWater();
        steer(mob, new Vec3(0, underwater ? SURFACE_SPEED : 0.02, 0));
        return true;
    }

    private void steer(Mob mob, Vec3 desired) {
        Vec3 v = mob.getDeltaMovement();
        v = v.add(desired.subtract(v).scale(RESPONSE));
        mob.setDeltaMovement(v);
        mob.hasImpulse = true;
        HoverPilot.face(mob, v);
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h > 0.02) mob.setXRot(Mth.clamp((float) -Math.toDegrees(Math.atan2(v.y, h)), -60f, 60f));
    }

    private void maybeReplan(Mob mob) {
        BlockPos destination = Locomotion.destination(mob);
        if (destination == null || plannedFor == null || destination.distSqr(plannedFor) <= REPLAN_SHIFT_SQR) return;
        replan(mob, destination);
    }

    private void replan(Mob mob, BlockPos destination) {
        if (!PlanThrottle.tryAcquire(mob.level())) return;
        SwimRoutePlanner.Result result = SwimRoutePlanner.plan(mob, profile, destination);
        if (!result.ok()) return;
        plannedFor = destination;
        follower = new RouteFollower(result.route().waypoints());
    }
}
