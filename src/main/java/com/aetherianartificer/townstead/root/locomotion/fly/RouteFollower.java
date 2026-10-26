package com.aetherianartificer.townstead.root.locomotion.fly;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Progress along a waypoint polyline: where the body is along it (its projection), and
 * points further ahead to steer for (pure pursuit). Only moves forward, so a route that
 * doubles back near itself never skips a leg.
 */
final class RouteFollower {

    /** How many legs ahead of the current one a projection may jump to. */
    private static final int SEARCH_LEGS = 3;

    private final List<Vec3> points;
    private final double[] arcAt;
    private int leg;
    private double progress;

    RouteFollower(List<Vec3> points) {
        this.points = points;
        this.arcAt = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            arcAt[i] = arcAt[i - 1] + points.get(i - 1).distanceTo(points.get(i));
        }
    }

    /** Re-project the body onto the route; call once per tick before the queries below. */
    void update(Vec3 pos) {
        double best = Double.MAX_VALUE;
        int bestLeg = leg;
        double bestArc = progress;
        int last = Math.min(points.size() - 2, leg + SEARCH_LEGS);
        for (int i = leg; i <= last; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 ab = b.subtract(a);
            double len2 = ab.lengthSqr();
            double t = len2 < 1.0E-9 ? 1.0 : Math.max(0, Math.min(1, pos.subtract(a).dot(ab) / len2));
            Vec3 on = a.add(ab.scale(t));
            double d = on.distanceToSqr(pos);
            if (d < best) {
                best = d;
                bestLeg = i;
                bestArc = arcAt[i] + Math.sqrt(len2) * t;
            }
        }
        leg = bestLeg;
        progress = Math.max(progress, bestArc);
    }

    double progress() {
        return progress;
    }

    double length() {
        return arcAt[arcAt.length - 1];
    }

    double remaining() {
        return length() - progress;
    }

    Vec3 end() {
        return points.get(points.size() - 1);
    }

    /** The route point at an arc distance from the start, clamped to the ends. */
    Vec3 at(double arc) {
        if (arc <= 0) return points.get(0);
        if (arc >= length()) return end();
        int i = Math.max(0, leg - 1);
        while (i < points.size() - 2 && arcAt[i + 1] < arc) i++;
        while (i > 0 && arcAt[i] > arc) i--;
        double span = arcAt[i + 1] - arcAt[i];
        double t = span < 1.0E-9 ? 1.0 : (arc - arcAt[i]) / span;
        return points.get(i).lerp(points.get(i + 1), t);
    }

    /** Waypoints from the current leg onward, for door checks ahead. */
    List<Vec3> ahead(int count) {
        return points.subList(Math.min(points.size() - 1, leg + 1), Math.min(points.size(), leg + 1 + count));
    }
}
