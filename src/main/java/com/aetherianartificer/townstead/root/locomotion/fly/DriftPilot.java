package com.aetherianartificer.townstead.root.locomotion.fly;

import com.aetherianartificer.townstead.root.locomotion.DriftPhysics;
import com.aetherianartificer.townstead.root.locomotion.FlightProfile;
import com.aetherianartificer.townstead.root.locomotion.FlightStyle;
import com.aetherianartificer.townstead.root.locomotion.Locomotion;
import com.aetherianartificer.townstead.root.locomotion.MovementMode;
import com.aetherianartificer.townstead.root.locomotion.Pilot;
import com.aetherianartificer.townstead.root.locomotion.PilotGravity;
import com.aetherianartificer.townstead.root.locomotion.TripCost;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoutePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A ghost's way of moving: always floating a little above the surface, bobbing, never
 * walking and never falling. Drops, gaps and water don't matter; it rises and sinks with
 * whatever is below, rises onto steps and settles low through doorways.
 *
 * <p>Normally MCA keeps navigating (planning paths, opening doors, every task that steers
 * the navigation directly) and this pilot only moves the body toward where the move
 * control points, at walking pace. When the walk target is out of walking reach, it
 * plans its own drift route and takes the navigation over until it arrives.</p>
 */
public final class DriftPilot implements Pilot {

    private static final int CHECK_EVERY = 20;
    private static final int OWN_ROUTE_EVERY = 40;
    private static final double OWN_ROUTE_SHIFT_SQR = 9.0;

    private final FlightProfile profile;
    private final Doors doors = new Doors();
    @Nullable private RouteFollower ownRoute;
    @Nullable private BlockPos ownRouteFor;
    private int age;

    public DriftPilot(Mob mob, FlightProfile profile) {
        this.profile = profile;
        this.age = mob.getId();
        PilotGravity.hold(mob);
    }

    /** Puts drift bearers into drift mode; called every tick for villagers, works once a second. */
    public static void maintain(Mob mob) {
        if ((mob.tickCount + mob.getId()) % CHECK_EVERY != 0) return;
        if (Locomotion.mode(mob) != MovementMode.GROUND) return;
        if (mob.isSleeping() || mob.isPassenger() || mob.isDeadOrDying()) return;
        FlightProfile profile = FlightProfile.of(mob);
        if (profile == null || profile.style() != FlightStyle.DRIFT) return;
        Locomotion.start(mob, MovementMode.DRIFT, new DriftPilot(mob, profile), null);
    }

    @Override
    public boolean tick(Mob mob) {
        age++;
        if (age % CHECK_EVERY == 0) {
            FlightProfile now = FlightProfile.of(mob);
            if (now == null || now.style() != FlightStyle.DRIFT) return false;
        }
        PilotGravity.hold(mob);
        if (age % OWN_ROUTE_EVERY == 0) reviewOwnRoute(mob);
        Vec3 pos = mob.position();
        Vec3 target = null;
        double pace;
        if (ownRoute != null) {
            ownRoute.update(pos);
            if (ownRoute.remaining() < 0.6) {
                dropOwnRoute(mob);
            } else {
                target = ownRoute.at(ownRoute.progress() + 1.5);
            }
            pace = TripCost.walkPace(mob, TripCost.walkSpeedModifier(mob));
        } else {
            MoveControl control = mob.getMoveControl();
            if (control.hasWanted()) target = new Vec3(control.getWantedX(), control.getWantedY(), control.getWantedZ());
            pace = TripCost.walkPace(mob, control.getSpeedModifier());
        }
        Vec3 v = mob.getDeltaMovement();
        double vx = v.x * 0.5, vz = v.z * 0.5;
        if (target != null) {
            double dx = target.x - pos.x, dz = target.z - pos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.05) {
                double speed = Math.min(pace, d * 0.5);
                vx = dx / d * speed;
                vz = dz / d * speed;
            }
        }
        double routeFloor = ownRoute != null && target != null ? target.y : Double.NaN;
        double vy = DriftPhysics.vertical(mob, new Vec3(vx, 0, vz), profile.cruiseHeight(), age, true, routeFloor);
        mob.setDeltaMovement(vx, vy, vz);
        mob.hasImpulse = true;
        HoverPilot.face(mob, new Vec3(vx, 0, vz));
        if (ownRoute != null) {
            doors.openAlong(mob, pos.add(vx * 6.0, 0, vz * 6.0));
            doors.closeBehind(mob);
        }
        mob.resetFallDistance();
        // MCA's navigation only advances its path on the ground; the float is too low for the airborne fallback.
        mob.setOnGround(true);
        return true;
    }

    @Override
    public void stop(Mob mob) {
        PilotGravity.release(mob);
        doors.closeAll(mob);
        mob.resetFallDistance();
    }

    @Override
    public boolean ownsNavigation() {
        return ownRoute != null;
    }

    @Override
    public String describe() {
        return "drift " + (ownRoute == null ? "with MCA's path"
                : String.format("own route, %.0f of %.0f blocks", ownRoute.progress(), ownRoute.length()));
    }

    /** Take over navigation with a drift route when walking can't reach; hand it back when it can. */
    private void reviewOwnRoute(Mob mob) {
        BlockPos destination = Locomotion.destination(mob);
        if (ownRoute != null) {
            if (destination == null || ownRouteFor == null || destination.distSqr(ownRouteFor) > OWN_ROUTE_SHIFT_SQR) {
                dropOwnRoute(mob);
            }
            return;
        }
        if (destination == null || TripCost.walkCanReach(mob)) return;
        double dx = destination.getX() + 0.5 - mob.getX(), dz = destination.getZ() + 0.5 - mob.getZ();
        if (dx * dx + dz * dz < 4.0) return;
        if (!PlanThrottle.tryAcquire(mob.level())) return;
        AirRoutePlanner.Result result = AirRoutePlanner.plan(mob, profile, destination);
        if (!result.ok()) return;
        ownRoute = new RouteFollower(result.route().waypoints());
        ownRouteFor = destination;
    }

    private void dropOwnRoute(Mob mob) {
        ownRoute = null;
        ownRouteFor = null;
        doors.closeAll(mob);
        if (!mob.getNavigation().isDone()) mob.getNavigation().recomputePath();
    }


}
