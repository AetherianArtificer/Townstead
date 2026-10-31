package com.aetherianartificer.townstead.root.locomotion.fly;

import com.aetherianartificer.townstead.root.locomotion.FlightProfile;
import com.aetherianartificer.townstead.root.locomotion.Locomotion;
import com.aetherianartificer.townstead.root.locomotion.Pilot;
import com.aetherianartificer.townstead.root.locomotion.PilotGravity;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoute;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoutePlanner;
import com.aetherianartificer.townstead.root.locomotion.plan.AirSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Flies a glide, flap or soar route on elytra physics.
 *
 * <p>Elytra flight steers only by look angle, so every other tick the pilot tries a
 * handful of pitch and yaw controls in {@link ElytraSim} over the next {@value #HORIZON}
 * ticks and keeps the one that ends nearest the route without touching anything, the
 * trajectory-testing idea Baritone's elytra mode uses. Straight ahead is tried first;
 * side headings only when every straight control would hit something.</p>
 *
 * <p>On approach it brakes on a constant tau-dot schedule (Lee et al. 1993, pigeons
 * landing): speed falls with the square root of the distance left, so closing speed and
 * distance reach zero together. For the last few blocks it flares: out of the glide pose,
 * upright with wings still out, it sets down on the spot under direct control on the same
 * schedule, the way a bird stalls onto a perch rather than skidding in. With no route (a fall caught mid-air) it holds a gentle glide ahead, dodges what the
 * simulation shows, and keeps trying to plan a landing.</p>
 */
public final class ElytraPilot implements Pilot {

    /** Deploys and folds the bearer's wings (gene toggles live with the glide AI). */
    public interface Wings {
        boolean deploy(Mob mob);

        void fold(Mob mob);
    }

    private enum Phase { TAKEOFF, FLY, FLARE, DROP }

    static final int HORIZON = 12;
    private static final int DECIDE_EVERY = 2;
    private static final int TAKEOFF_TICKS = 10;
    private static final int SPACE_REFRESH = 40;
    private static final int REPLAN_EVERY = 20;
    private static final double REPLAN_SHIFT_SQR = 16.0;
    private static final double APPROACH = 12.0;
    private static final double MIN_APPROACH_SPEED = 0.12;
    private static final double STALL_SPEED = 0.2;
    /** Route left when the flare begins. */
    private static final double FLARE_RANGE = 6.0;
    private static final double FLARE_MIN_SPEED = 0.15;
    private static final double FLARE_MAX_SPEED = 0.45;
    private static final double FLARE_SETTLE = 0.3;
    private static final int FLARE_TICKS = 160;
    private static final double LAUNCH_PUSH = 0.6;
    private static final int HOPELESS_LIMIT = 3;
    private static final float[] PITCHES = {-30f, -12f, 0f, 10f, 22f, 40f};
    private static final float[] YAW_OFFSETS = {-40f, -20f, 20f, 40f};

    private final FlightProfile profile;
    private final Wings wings;
    @Nullable private AirRoute route;
    @Nullable private RouteFollower follower;
    @Nullable private BlockPos plannedFor;
    private AirSpace space;
    private long spaceAt;
    private Phase phase;
    private int phaseTicks;
    private int age;
    private int hopeless;
    private double approachSpeed = Double.NaN;
    private float pitch;
    private float yaw;
    private double flareFrom;
    private double flareSpeed;

    /**
     * @param takeoff true when the bearer is still on the ground (it was just given a hop);
     *                false when already airborne (wings deploy now)
     */
    public ElytraPilot(Mob mob, FlightProfile profile, @Nullable AirRoute route, @Nullable BlockPos destination,
                       Wings wings, boolean takeoff) {
        this.profile = profile;
        this.wings = wings;
        this.plannedFor = destination;
        this.space = new AirSpace(mob.level(), mob.getBbWidth(), mob.getBbHeight());
        this.spaceAt = mob.level().getGameTime();
        this.pitch = mob.getXRot();
        this.yaw = mob.getYRot();
        setRoute(route);
        this.phase = takeoff ? Phase.TAKEOFF : Phase.FLY;
        if (!takeoff) deploy(mob);
    }

    @Override
    public boolean tick(Mob mob) {
        age++;
        phaseTicks++;
        return switch (phase) {
            case TAKEOFF -> tickTakeoff(mob);
            case FLY -> tickFly(mob);
            case FLARE -> tickFlare(mob);
            case DROP -> tickDrop(mob);
        };
    }

    @Override
    public void stop(Mob mob) {
        PilotGravity.release(mob);
        mob.resetFallDistance();
        wings.fold(mob);
    }

    @Override
    public boolean wantsLift(Mob mob) {
        if (phase != Phase.FLY || follower == null || follower.remaining() < APPROACH) return false;
        Vec3 here = follower.at(follower.progress());
        Vec3 ahead = follower.at(follower.progress() + 8.0);
        boolean low = mob.getY() < here.y - 0.5;
        boolean climbing = ahead.y > mob.getY() + 0.5 && mob.getDeltaMovement().y < 0.1;
        return low || climbing;
    }

    @Override
    public String describe() {
        String where = follower == null ? "no route (looking for a landing)"
                : String.format("%.0f of %.0f blocks", follower.progress(), follower.length());
        return profile.style().key() + " " + phase.name().toLowerCase() + ", " + where
                + (hopeless > 0 ? ", boxed in x" + hopeless : "");
    }

    private boolean tickTakeoff(Mob mob) {
        if (!mob.onGround()) {
            deploy(mob);
            return true;
        }
        return phaseTicks <= TAKEOFF_TICKS;
    }

    private boolean tickFly(Mob mob) {
        if (!mob.isFallFlying()) {
            // Vanilla clears the flag on touchdown and in water; anything else, finish on foot.
            if (mob.onGround() || mob.isInWater()) return false;
            enter(Phase.DROP);
            return true;
        }
        long now = mob.level().getGameTime();
        if (now - spaceAt > SPACE_REFRESH) {
            space = new AirSpace(mob.level(), mob.getBbWidth(), mob.getBbHeight());
            spaceAt = now;
        }
        Vec3 pos = mob.position();
        if (follower != null) follower.update(pos);
        if (age % REPLAN_EVERY == 0) maybeReplan(mob);
        if (follower != null && follower.remaining() <= FLARE_RANGE) {
            beginFlare(mob, pos);
            return true;
        }
        if (follower == null && mob.getY() - groundBelow(mob) < 2.0) {
            enter(Phase.DROP);
            return true;
        }
        Vec3 velocity = mob.getDeltaMovement();
        double brakeTo = brakeTarget(velocity);
        velocity = ElytraSim.assist(velocity);
        if (!Double.isNaN(brakeTo)) velocity = ElytraSim.brake(velocity, brakeTo);
        mob.setDeltaMovement(velocity);
        if (age % DECIDE_EVERY == 0) decide(mob, pos, velocity, brakeTo);
        mob.setXRot(pitch);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setYHeadRot(yaw);
        return true;
    }

    /**
     * Out of the glide and upright, gravity held off: steer straight for the spot, slowing
     * with the square root of the distance left, and settle onto it. Anything in the way
     * ends the flare in a short drop instead.
     */
    private void beginFlare(Mob mob, Vec3 pos) {
        ((com.aetherianartificer.townstead.root.ability.FallFlightBridge) mob).townstead$setFallFlying(false);
        PilotGravity.hold(mob);
        flareFrom = Math.max(1.0, pos.distanceTo(follower.end()));
        flareSpeed = Mth.clamp(mob.getDeltaMovement().length(), FLARE_MIN_SPEED, FLARE_MAX_SPEED);
        enter(Phase.FLARE);
    }

    private boolean tickFlare(Mob mob) {
        if (mob.onGround() || mob.isInWater()) return false;
        Vec3 pos = mob.position();
        Vec3 to = follower.end().subtract(pos);
        double d = to.length();
        if (d < FLARE_SETTLE) {
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.2));
            return false;
        }
        double speed = Math.max(0.05, flareSpeed * Math.sqrt(d / flareFrom));
        Vec3 desired = to.scale(Math.min(speed, d * 0.5) / d);
        if (!space.bodyFree(pos.add(desired.scale(2.0)), 0.0) || phaseTicks > FLARE_TICKS) {
            PilotGravity.release(mob);
            enter(Phase.DROP);
            return true;
        }
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(v.add(desired.subtract(v).scale(0.5)));
        mob.hasImpulse = true;
        if (to.x * to.x + to.z * to.z > 0.04) {
            yaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90f;
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
            mob.setYHeadRot(yaw);
        }
        pitch = 0f;
        mob.setXRot(0f);
        return true;
    }

    /** Folded: fall the last bit, drifting onto the spot. Harmless while the pilot still has the body. */
    private boolean tickDrop(Mob mob) {
        if (mob.isFallFlying()) ((com.aetherianartificer.townstead.root.ability.FallFlightBridge) mob).townstead$setFallFlying(false);
        if (mob.onGround() || mob.isInWater()) return false;
        Vec3 v = mob.getDeltaMovement();
        double vx = v.x * 0.6;
        double vz = v.z * 0.6;
        if (route != null) {
            Vec3 to = route.end().subtract(mob.position());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            if (d > 0.2) {
                vx += to.x / d * 0.05;
                vz += to.z / d * 0.05;
            }
        }
        mob.setDeltaMovement(vx, v.y, vz);
        return phaseTicks < 200;
    }

    /** Constant tau-dot approach: speed falls with the square root of the distance left. NaN outside the approach. */
    private double brakeTarget(Vec3 velocity) {
        if (follower == null || follower.remaining() > APPROACH) {
            approachSpeed = Double.NaN;
            return Double.NaN;
        }
        if (Double.isNaN(approachSpeed)) approachSpeed = Math.max(velocity.horizontalDistance(), MIN_APPROACH_SPEED);
        return Math.max(MIN_APPROACH_SPEED, approachSpeed * Math.sqrt(follower.remaining() / APPROACH));
    }

    private void decide(Mob mob, Vec3 pos, Vec3 velocity, double brakeTo) {
        float bearing = bearing(mob, pos);
        Choice best = null;
        for (float p : PITCHES) best = better(best, evaluate(pos, velocity, p, bearing, brakeTo));
        best = better(best, evaluate(pos, velocity, pitch, yaw, brakeTo));
        if (best.run.collided()) {
            for (float offset : YAW_OFFSETS) {
                for (float p : PITCHES) best = better(best, evaluate(pos, velocity, p, bearing + offset, brakeTo));
            }
        }
        hopeless = best.run.collided() && best.run.collidedAt() < HORIZON / 2 ? hopeless + 1 : 0;
        pitch = best.pitch;
        yaw = best.yaw;
        if (hopeless >= HOPELESS_LIMIT) {
            // Every control hits something soon: pull up hard and look for somewhere else to land.
            pitch = -35f;
            planLanding(mob, emergencyDestination(mob, pos));
            hopeless = 0;
        }
    }

    private record Choice(float pitch, float yaw, ElytraSim.Run run, double cost) {}

    private static Choice better(@Nullable Choice a, Choice b) {
        return a == null || b.cost < a.cost ? b : a;
    }

    private Choice evaluate(Vec3 pos, Vec3 velocity, float p, float y, double brakeTo) {
        ElytraSim.Run run = ElytraSim.run(space, pos, velocity, p, y, HORIZON, true, brakeTo);
        double cost;
        if (run.collided()) {
            cost = 1000.0 + (HORIZON - run.collidedAt()) * 50.0;
        } else if (follower != null) {
            Vec3 ref = follower.at(follower.progress() + run.arc());
            cost = run.position().distanceTo(ref) + 1.5 * Math.max(0, ref.y - run.position().y);
            if (Double.isNaN(brakeTo) && run.velocity().horizontalDistance() < STALL_SPEED) {
                cost += (STALL_SPEED - run.velocity().horizontalDistance()) * 30.0;
            }
        } else {
            // No route: glide on gently and keep airspeed; any clear control beats a crash.
            double sink = pos.y - run.position().y;
            cost = Math.abs(sink - run.arc() / profile.style().glideRatio())
                    + Math.max(0, STALL_SPEED - run.velocity().horizontalDistance()) * 30.0;
        }
        return new Choice(p, Mth.wrapDegrees(y), run, cost);
    }

    /** Yaw toward a point ahead on the route (pure pursuit), else the current heading. */
    private float bearing(Mob mob, Vec3 pos) {
        if (follower == null) return mob.getYRot();
        double look = Math.max(4.0, mob.getDeltaMovement().horizontalDistance() * HORIZON);
        Vec3 target = follower.at(follower.progress() + look);
        double dx = target.x - pos.x, dz = target.z - pos.z;
        if (dx * dx + dz * dz < 1.0E-4) return mob.getYRot();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
    }

    private void maybeReplan(Mob mob) {
        BlockPos destination = Locomotion.destination(mob);
        if (follower == null) {
            planLanding(mob, destination != null ? destination : emergencyDestination(mob, mob.position()));
            return;
        }
        if (destination == null || plannedFor == null || destination.distSqr(plannedFor) <= REPLAN_SHIFT_SQR) return;
        planLanding(mob, destination);
    }

    private void planLanding(Mob mob, BlockPos destination) {
        if (!PlanThrottle.tryAcquire(mob.level())) return;
        AirRoutePlanner.Result result = AirRoutePlanner.plan(mob, profile, destination);
        if (!result.ok()) return;
        plannedFor = destination;
        setRoute(result.route());
    }

    /** The ground a dozen blocks ahead: somewhere to come down when the plan is gone. */
    private static BlockPos emergencyDestination(Mob mob, Vec3 pos) {
        Vec3 look = mob.getLookAngle();
        double len = Math.sqrt(look.x * look.x + look.z * look.z);
        double ax = len < 1.0E-3 ? 0 : look.x / len, az = len < 1.0E-3 ? 0 : look.z / len;
        int x = Mth.floor(pos.x + ax * 12.0), z = Mth.floor(pos.z + az * 12.0);
        int y = mob.level().hasChunk(x >> 4, z >> 4)
                ? mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : Mth.floor(pos.y);
        return new BlockPos(x, y, z);
    }

    private double groundBelow(Mob mob) {
        int x = mob.getBlockX(), z = mob.getBlockZ();
        if (!mob.level().hasChunk(x >> 4, z >> 4)) return mob.getY() - 64;
        return mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    private void deploy(Mob mob) {
        if (!wings.deploy(mob)) return;
        enter(Phase.FLY);
        // Featherfall leaves almost no airspeed, and a glide without airspeed is a decorated descent.
        Vec3 aim = follower != null ? follower.at(follower.progress() + 6.0).subtract(mob.position()) : mob.getLookAngle();
        double len = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        if (len > 1.0E-3) {
            mob.setDeltaMovement(mob.getDeltaMovement().add(aim.x / len * LAUNCH_PUSH, 0.1, aim.z / len * LAUNCH_PUSH));
            mob.hasImpulse = true;
            yaw = (float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90f;
        }
    }

    private void setRoute(@Nullable AirRoute route) {
        this.route = route;
        this.follower = route == null ? null : new RouteFollower(route.waypoints());
        this.approachSpeed = Double.NaN;
    }

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
    }
}
