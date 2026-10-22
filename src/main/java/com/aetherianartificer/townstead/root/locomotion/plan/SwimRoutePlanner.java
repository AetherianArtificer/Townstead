package com.aetherianartificer.townstead.root.locomotion.plan;

import com.aetherianartificer.townstead.root.locomotion.SwimProfile;
import com.aetherianartificer.townstead.root.locomotion.SwimStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a swim through water cells with the {@link VoxelPlanner}. Each style keeps to
 * its depth (a paddler at the surface, a fish a few blocks down); the route ends at the
 * destination when it is in the water, or in the water beside it when it is on the shore
 * (MCA walks the last steps out). A swimmer who holds its breath only gets a route whose
 * longest stretch under the surface fits in the air it has now, with a margin; failing
 * that, it re-plans hugging the surface.
 */
public final class SwimRoutePlanner {

    public record Result(@Nullable SwimRoute route, String failure, int expansions, long micros) {
        public boolean ok() {
            return route != null;
        }
    }

    private static final int MARGIN = 10;
    private static final int MAX_EXPANSIONS = 6000;
    private static final int DEPTH_SCAN = 6;
    private static final double DEPTH_WEIGHT = 0.5;
    private static final double HOLD_WEIGHT = 0.5;
    private static final double SURFACE_HOLD_WEIGHT = 4.0;
    /** A shore destination is reached from water this close to it. */
    private static final double SHORE_REACH = 3.5;
    /** Share of the air in hand a planned dive may use. */
    private static final double AIR_MARGIN = 0.6;
    private static final double SAMPLE = 0.5;

    private SwimRoutePlanner() {}

    public static Result plan(LivingEntity mob, SwimProfile profile, BlockPos destination) {
        long t0 = System.nanoTime();
        int[] expansions = {0};
        String[] failure = {""};
        SwimRoute route = run(mob, profile, destination, expansions, failure);
        return new Result(route, route == null ? failure[0] : "", expansions[0], (System.nanoTime() - t0) / 1000L);
    }

    @Nullable
    private static SwimRoute run(LivingEntity mob, SwimProfile profile, BlockPos destination, int[] expansions,
                                 String[] failure) {
        AirSpace space = new AirSpace(mob.level(), mob.getBbWidth(), mob.getBbHeight(), AirSpace.Medium.WATER);
        BlockPos start = startCell(space, mob.blockPosition());
        if (start == null) {
            failure[0] = "not in the water";
            return null;
        }
        boolean inWater = space.passable(destination.getX(), destination.getY(), destination.getZ());
        VoxelPlanner.Goal goal = inWater
                ? (x, y, z) -> x == destination.getX() && y == destination.getY() && z == destination.getZ()
                : (x, y, z) -> distSqr(x, y, z, destination) <= SHORE_REACH * SHORE_REACH;
        SwimStyle style = profile.style();
        List<Vec3> path = search(space, start, destination, goal, style, profile.breathes() ? 0 : HOLD_WEIGHT, expansions);
        if (path == null) {
            failure[0] = inWater ? "no way through the water" : "no water reaches the shore by the destination";
            return null;
        }
        int hold = longestHold(space, path, style);
        if (!profile.breathes() && hold > air(mob)) {
            path = search(space, start, destination, goal, style, SURFACE_HOLD_WEIGHT, expansions);
            hold = path == null ? Integer.MAX_VALUE : longestHold(space, path, style);
            if (path == null || hold > air(mob)) {
                failure[0] = "would run out of air";
                return null;
            }
        }
        path.set(0, mob.position());
        return new SwimRoute(style, path, length(path), hold, !inWater);
    }

    @Nullable
    private static List<Vec3> search(AirSpace space, BlockPos start, BlockPos aim, VoxelPlanner.Goal goal,
                                     SwimStyle style, double holdWeight, int[] expansions) {
        VoxelPlanner.CellCost cost = (x, y, z) -> {
            int depth = space.depth(x, y, z, DEPTH_SCAN);
            double extra = DEPTH_WEIGHT * Math.abs(depth - style.depth());
            if (depth > 0) extra += holdWeight;
            return extra;
        };
        VoxelPlanner planner = new VoxelPlanner(space, start, aim, goal, cost, MARGIN, MAX_EXPANSIONS);
        VoxelPlanner.Status status = planner.step(MAX_EXPANSIONS);
        expansions[0] += planner.expansions();
        return status == VoxelPlanner.Status.FOUND ? new ArrayList<>(planner.path()) : null;
    }

    /** The water cell the body is in, or one beside it (a swimmer bobbing half out of a cell). */
    @Nullable
    private static BlockPos startCell(AirSpace space, BlockPos at) {
        if (space.passable(at.getX(), at.getY(), at.getZ())) return at;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (space.passable(at.getX() + dx, at.getY() + dy, at.getZ() + dz)) return at.offset(dx, dy, dz);
                }
            }
        }
        return null;
    }

    /** Ticks of the longest stretch along the route with the head under water, at the style's swim speed. */
    static int longestHold(AirSpace space, List<Vec3> path, SwimStyle style) {
        double longest = 0;
        double run = 0;
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1);
            Vec3 b = path.get(i);
            int steps = Math.max(1, Mth.ceil(a.distanceTo(b) / SAMPLE));
            double step = a.distanceTo(b) / steps;
            for (int k = 1; k <= steps; k++) {
                Vec3 p = a.lerp(b, (double) k / steps);
                if (space.depth(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z), 1) > 0) {
                    run += step;
                    longest = Math.max(longest, run);
                } else {
                    run = 0;
                }
            }
        }
        return (int) Math.ceil(longest / style.swimSpeed());
    }

    private static int air(LivingEntity mob) {
        return (int) (mob.getAirSupply() * AIR_MARGIN);
    }

    private static double distSqr(int x, int y, int z, BlockPos to) {
        double dx = x - to.getX(), dy = y - to.getY(), dz = z - to.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double length(List<Vec3> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) total += points.get(i - 1).distanceTo(points.get(i));
        return total;
    }
}
