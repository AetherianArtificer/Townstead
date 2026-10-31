package com.aetherianartificer.townstead.root.locomotion.plan;

import com.aetherianartificer.townstead.root.locomotion.FlightStyle;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Heights along a ground track. Each sample wants cruise height over the terrain but is
 * held inside what the style can fly: no faster rise than its climb slope (a pure glider
 * must sink one block per glide ratio), no steeper fall than its descent slope, and always
 * able to come down onto the landing spot. A sample forced below the terrain is a failure,
 * and its position goes back to the cruise layer as a column to route around.
 */
final class AltitudeProfile {

    private static final double STEP = 1.5;
    /** Distance from either end over which the clearance margin ramps up from zero (body leaves the ground there). */
    private static final double END_RAMP = 3.0;

    record Result(List<Vec3> points, @Nullable Vec2 failAt, String failReason) {
        boolean ok() {
            return failAt == null && failReason.isEmpty();
        }
    }

    private AltitudeProfile() {}

    static Result fit(AirSpace space, FlightStyle style, double cruiseHeight, List<Vec2> track, Vec3 start, Vec3 end) {
        List<Vec2> xz = new ArrayList<>();
        List<Double> s = new ArrayList<>();
        double total = 0;
        xz.add(track.get(0));
        s.add(0.0);
        for (int k = 1; k < track.size(); k++) {
            Vec2 a = track.get(k - 1);
            Vec2 b = track.get(k);
            double len = Math.sqrt(sq(b.x - a.x) + sq(b.y - a.y));
            int steps = Math.max(1, (int) Math.ceil(len / STEP));
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / steps;
                xz.add(new Vec2((float) (a.x + (b.x - a.x) * t), (float) (a.y + (b.y - a.y) * t)));
                s.add(total + len * t);
            }
            total += len;
        }
        int n = xz.size();
        double rise = style.maxRiseSlope();
        double descent = style.descentSlope();
        double clearance = style.clearance();
        List<Vec3> out = new ArrayList<>(n);
        double prev = start.y;
        out.add(start);
        for (int i = 1; i < n; i++) {
            Vec2 p = xz.get(i);
            double si = s.get(i);
            double ds = si - s.get(i - 1);
            double margin = clearance * Math.min(1.0, Math.min(si, total - si) / END_RAMP);
            int top = space.topUnder(p.x, p.y, margin);
            if (top == Integer.MAX_VALUE) return new Result(List.of(), p, "unloaded terrain");
            double floor = top + margin;
            double want = Math.max(top + cruiseHeight, floor);
            double lo = prev - descent * ds;
            double hi = Math.min(prev + rise * ds, end.y + descent * (total - si));
            double a = i == n - 1 ? Math.max(lo, Math.min(hi, end.y)) : Math.max(lo, Math.min(hi, want));
            if (a < floor - 1.0E-6 && i < n - 1) return new Result(List.of(), p, "terrain above reachable height");
            out.add(i == n - 1 ? end : new Vec3(p.x, a, p.y));
            prev = a;
        }
        if (prev < end.y - 0.5) return new Result(List.of(), null, "lands short: too low to reach the landing spot");
        if (prev > end.y + 0.5) return new Result(List.of(), null, "arrives too high to land");
        return new Result(out, null, "");
    }

    /** Height the bearer must gain beyond its free glide along these points: the lift a route costs. */
    static double liftNeeded(List<Vec3> points, double glideRatio) {
        double lift = 0;
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            double ds = Math.sqrt(sq(b.x - a.x) + sq(b.z - a.z));
            double sink = Double.isInfinite(glideRatio) ? 0 : ds / glideRatio;
            lift += Math.max(0, (b.y - a.y) + sink);
        }
        return lift;
    }

    private static double sq(double v) {
        return v * v;
    }
}
