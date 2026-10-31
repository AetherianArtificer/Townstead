package com.aetherianartificer.townstead.root.locomotion.plan;

import com.aetherianartificer.townstead.root.locomotion.FlightProfile;
import com.aetherianartificer.townstead.root.locomotion.FlightStamina;
import com.aetherianartificer.townstead.root.locomotion.FlightStyle;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans a flight in layers, coarse to fine:
 *
 * <ol>
 * <li>pick landing spots near the destination ({@link LandingSites});</li>
 * <li>route the ground track over the terrain columns ({@link CruisePlanner});</li>
 * <li>fit heights the style can fly ({@link AltitudeProfile});</li>
 * <li>pull the string: keep only the waypoints whose straight legs are clear.</li>
 * </ol>
 *
 * <p>Gravity-free styles may start or end indoors: a {@link VoxelPlanner} leg links the
 * spot to the nearest open sky, and short hops (and every drift trip) are planned in
 * voxels end to end. Any failure in the profile or the string-pull marks that column
 * and re-routes the track around it, a few times before giving up on a landing spot.</p>
 */
public final class AirRoutePlanner {

    public record Result(@Nullable AirRoute route, String failure, int expansions, long micros) {
        public boolean ok() {
            return route != null;
        }
    }

    private static final int CRUISE_ATTEMPTS = 4;
    private static final double SHORT_HOP = 24.0;
    private static final int LEG_MARGIN = 12;
    private static final int LEG_EXPANSIONS = 2500;
    private static final int DRIFT_MARGIN = 8;
    private static final int DRIFT_EXPANSIONS = 6000;
    private static final double DRIFT_LIFT_COST = 2.0;

    private int expansions;

    private AirRoutePlanner() {}

    /** Plans synchronously. Callers pace how often they ask; a plan touches at most a few thousand cells. */
    public static Result plan(LivingEntity mob, FlightProfile profile, BlockPos destination) {
        long t0 = System.nanoTime();
        AirRoutePlanner planner = new AirRoutePlanner();
        String[] failure = {""};
        AirRoute route = planner.run(mob, profile, destination, failure);
        long micros = (System.nanoTime() - t0) / 1000L;
        return new Result(route, route == null ? failure[0] : "", planner.expansions, micros);
    }

    @Nullable
    private AirRoute run(LivingEntity mob, FlightProfile profile, BlockPos destination, String[] failure) {
        FlightStyle style = profile.style();
        AirSpace space = new AirSpace(mob.level(), mob.getBbWidth(), mob.getBbHeight());
        Vec3 start = mob.position();
        BlockPos startCell = mob.blockPosition();
        if (style.needsOpenSky() && !space.openSky(startCell.getX(), startCell.getY(), startCell.getZ())) {
            failure[0] = "no open sky at takeoff";
            return null;
        }
        List<BlockPos> candidates = LandingSites.find(space, style, destination);
        if (candidates.isEmpty()) {
            failure[0] = "no landing spot near the destination";
            return null;
        }
        double budget = FlightStamina.liftBudget(mob, profile);
        for (BlockPos landing : candidates) {
            Vec3 end = new Vec3(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
            boolean voxelOnly = style == FlightStyle.DRIFT
                    || (style == FlightStyle.HOVER && horizontal(start, end) <= SHORT_HOP);
            List<Vec3> points = voxelOnly
                    ? voxelTrip(space, style, start, startCell, landing, failure)
                    : cruiseTrip(space, style, profile.cruiseHeight(), start, startCell, end, landing, failure);
            if (points == null) continue;
            double lift = AltitudeProfile.liftNeeded(points, style.glideRatio());
            if (lift > budget && !voxelOnly && profile.cruiseHeight() > style.clearance() + 1.0) {
                // Out of stamina for a high cruise: try skimming the terrain instead.
                List<Vec3> low = cruiseTrip(space, style, style.clearance() + 1.0, start, startCell, end, landing, failure);
                if (low != null) {
                    points = low;
                    lift = AltitudeProfile.liftNeeded(points, style.glideRatio());
                }
            }
            if (lift > budget) {
                failure[0] = String.format("needs %.0f lift, stamina buys %.0f", lift, budget);
                continue;
            }
            boolean perch = landing.getY() - destination.getY() > LandingSites.PERCH_HEIGHT;
            return new AirRoute(style, points, landing, length(points), lift, budget, perch);
        }
        return null;
    }

    @Nullable
    private List<Vec3> voxelTrip(AirSpace space, FlightStyle style, Vec3 start, BlockPos startCell,
                                 BlockPos landing, String[] failure) {
        VoxelPlanner.CellCost cost = style == FlightStyle.DRIFT
                ? (x, y, z) -> DRIFT_LIFT_COST * space.airBelow(x, y, z, 6)
                : null;
        int cap = style == FlightStyle.DRIFT ? DRIFT_EXPANSIONS : LEG_EXPANSIONS;
        VoxelPlanner planner = new VoxelPlanner(space, startCell, landing,
                (x, y, z) -> x == landing.getX() && y == landing.getY() && z == landing.getZ(),
                cost, style == FlightStyle.DRIFT ? DRIFT_MARGIN : LEG_MARGIN, cap);
        VoxelPlanner.Status status = planner.step(cap);
        expansions += planner.expansions();
        if (status != VoxelPlanner.Status.FOUND) {
            failure[0] = "no way through (" + planner.expansions() + " cells searched)";
            return null;
        }
        List<Vec3> path = new ArrayList<>(planner.path());
        path.set(0, start);
        return path;
    }

    @Nullable
    private List<Vec3> cruiseTrip(AirSpace space, FlightStyle style, double cruise, Vec3 start, BlockPos startCell,
                                  Vec3 end, BlockPos landing, String[] failure) {
        List<Vec3> head = new ArrayList<>();
        List<Vec3> tail = new ArrayList<>();
        Vec3 from = start;
        Vec3 to = end;
        if (!style.needsOpenSky()) {
            if (!space.openSky(startCell.getX(), startCell.getY(), startCell.getZ())) {
                List<Vec3> leg = legToSky(space, startCell, failure);
                if (leg == null) return null;
                leg.set(0, start);
                head.addAll(leg.subList(0, leg.size() - 1));
                from = leg.get(leg.size() - 1);
            }
            if (!space.openSky(landing.getX(), landing.getY(), landing.getZ())) {
                List<Vec3> leg = legToSky(space, landing, failure);
                if (leg == null) return null;
                java.util.Collections.reverse(leg);
                leg.set(leg.size() - 1, end);
                to = leg.get(0);
                tail.addAll(leg.subList(1, leg.size()));
            }
        }
        LongSet blocked = CruisePlanner.newBlockedSet();
        for (int attempt = 0; attempt < CRUISE_ATTEMPTS; attempt++) {
            List<Vec2> track = new CruisePlanner(space, blocked).track(new Vec2((float) from.x, (float) from.z),
                    new Vec2((float) to.x, (float) to.z));
            if (track == null) {
                failure[0] = "no open-air track";
                return null;
            }
            AltitudeProfile.Result profile = AltitudeProfile.fit(space, style, cruise, track, from, to);
            if (!profile.ok()) {
                failure[0] = profile.failReason();
                if (profile.failAt() == null) return null;
                blocked.add(CruisePlanner.key(CruisePlanner.cellOf(profile.failAt().x), CruisePlanner.cellOf(profile.failAt().y)));
                continue;
            }
            int[] blockedAt = {-1};
            List<Vec3> pulled = pull(space, profile.points(), style.clearance(), blockedAt);
            if (pulled == null) {
                Vec3 bad = profile.points().get(blockedAt[0]);
                failure[0] = "obstacle on the open-air leg";
                blocked.add(CruisePlanner.key(CruisePlanner.cellOf(bad.x), CruisePlanner.cellOf(bad.z)));
                continue;
            }
            List<Vec3> out = new ArrayList<>(head.size() + pulled.size() + tail.size());
            out.addAll(head);
            out.addAll(pulled);
            out.addAll(tail);
            return out;
        }
        return null;
    }

    /** Voxel leg from an indoor cell to the nearest cell under open sky. */
    @Nullable
    private List<Vec3> legToSky(AirSpace space, BlockPos cell, String[] failure) {
        BlockPos aim = cell.above(LEG_MARGIN / 2);
        VoxelPlanner planner = new VoxelPlanner(space, cell, aim, space::openSky, null, LEG_MARGIN, LEG_EXPANSIONS);
        VoxelPlanner.Status status = planner.step(LEG_EXPANSIONS);
        expansions += planner.expansions();
        if (status != VoxelPlanner.Status.FOUND) {
            failure[0] = "no way out to open sky";
            return null;
        }
        return new ArrayList<>(planner.path());
    }

    /**
     * String-pulling: from each kept point, jump to the farthest later point with a clear
     * straight leg (binary search, assuming clearance mostly shrinks with distance).
     * Null when even neighbouring points are blocked; {@code blockedAt[0]} then names the far one.
     */
    @Nullable
    private static List<Vec3> pull(AirSpace space, List<Vec3> points, double clearance, int[] blockedAt) {
        int last = points.size() - 1;
        List<Vec3> out = new ArrayList<>();
        out.add(points.get(0));
        int i = 0;
        while (i < last) {
            if (!clear(space, points, i, i + 1, last, clearance)) {
                blockedAt[0] = i + 1;
                return null;
            }
            int best = i + 1;
            if (clear(space, points, i, last, last, clearance)) {
                best = last;
            } else {
                int lo = i + 2, hi = last - 1;
                while (lo <= hi) {
                    int mid = (lo + hi) >>> 1;
                    if (clear(space, points, i, mid, last, clearance)) {
                        best = mid;
                        lo = mid + 1;
                    } else {
                        hi = mid - 1;
                    }
                }
            }
            out.add(points.get(best));
            i = best;
        }
        return out;
    }

    /** Legs touching the ground ends fly close to it; open-air legs keep half the style's clearance. */
    private static boolean clear(AirSpace space, List<Vec3> points, int from, int to, int last, double clearance) {
        double margin = from == 0 || to == last ? 0.0 : clearance * 0.5;
        return space.segmentClear(points.get(from), points.get(to), margin);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double length(List<Vec3> points) {
        double total = 0;
        for (int i = 1; i < points.size(); i++) total += points.get(i - 1).distanceTo(points.get(i));
        return total;
    }
}
