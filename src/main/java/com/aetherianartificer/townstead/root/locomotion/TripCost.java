package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.root.locomotion.plan.AirRoute;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Walk or fly: both priced in ticks. A walk costs its path length at walking pace. A
 * flight costs its route at the style's cruise speed, plus a takeoff, plus a charge for
 * every block of lift so a flapper's stamina is not free. Flying wins when it is clearly
 * quicker, or when walking can't get there at all.
 */
public final class TripCost {

    private TripCost() {}

    private static final double TAKEOFF_TICKS = 40.0;
    private static final double LIFT_TICKS = 4.0;
    /** Flight must take under this share of the walk's time to be worth it. */
    private static final double PREFER = 0.75;
    /** Straight-line distance to walking distance when no path says otherwise. */
    private static final double WALK_DETOUR = 1.4;
    private static final double TARGET_MATCH_SQR = 4.0;
    /** Ground walking turns move speed into about this many blocks a tick per speed squared. */
    private static final double WALK_PACE = 2.92;
    private static final double MAX_PACE = 0.35;
    private static final double DEFAULT_SPEED_MODIFIER = 0.5;

    public static boolean flightWins(double flyTicks, double walkTicks) {
        return flyTicks < PREFER * walkTicks;
    }

    /** Whether walking can reach the walk target, as far as MCA's last path attempt knows. */
    public static boolean walkCanReach(Mob mob) {
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) return false;
        Path path = mob.getNavigation().getPath();
        return path == null || path.canReach();
    }

    /** Ticks to walk there; infinite when walking can't reach. */
    public static double walkTicks(Mob mob, BlockPos target) {
        if (!walkCanReach(mob)) return Double.POSITIVE_INFINITY;
        double length = pathLength(mob, target);
        return length / walkPace(mob, walkSpeedModifier(mob));
    }

    /** A cheap guess before planning: straight line, cruise speed, and the lift a straight climb and glide would cost. */
    public static double estimateFlyTicks(FlightStyle style, double horizontal, double drop) {
        double sink = Double.isInfinite(style.glideRatio()) ? 0 : horizontal / style.glideRatio();
        double lift = Math.max(0, -drop + sink);
        if (style.fullLift() <= 0) lift = 0;
        return Math.sqrt(horizontal * horizontal + drop * drop) / style.cruiseSpeed() + TAKEOFF_TICKS + LIFT_TICKS * lift;
    }

    public static double flyTicks(AirRoute route) {
        return route.length() / route.style().cruiseSpeed() + TAKEOFF_TICKS + LIFT_TICKS * route.lift();
    }

    /** Blocks a tick a walker makes at this speed modifier. */
    public static double walkPace(LivingEntity mob, double modifier) {
        double s = modifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Math.min(MAX_PACE, Math.max(0.01, WALK_PACE * s * s));
    }

    public static double walkSpeedModifier(Mob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> (double) walk.getSpeedModifier()).orElse(DEFAULT_SPEED_MODIFIER);
    }

    /** The rest of MCA's path when it leads to this target, else a detoured straight line. */
    private static double pathLength(Mob mob, BlockPos target) {
        Path path = mob.getNavigation().getPath();
        double dx = target.getX() + 0.5 - mob.getX(), dz = target.getZ() + 0.5 - mob.getZ();
        double straight = Math.sqrt(dx * dx + dz * dz);
        if (path == null || path.isDone() || path.getTarget().distSqr(target) > TARGET_MATCH_SQR) {
            return straight * WALK_DETOUR;
        }
        double length = 0;
        double px = mob.getX(), py = mob.getY(), pz = mob.getZ();
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            double nx = node.x + 0.5, ny = node.y, nz = node.z + 0.5;
            length += Math.sqrt((nx - px) * (nx - px) + (ny - py) * (ny - py) + (nz - pz) * (nz - pz));
            px = nx;
            py = ny;
            pz = nz;
        }
        return length;
    }
}
