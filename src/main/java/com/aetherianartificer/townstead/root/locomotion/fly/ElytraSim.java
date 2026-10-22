package com.aetherianartificer.townstead.root.locomotion.fly;

import com.aetherianartificer.townstead.root.locomotion.plan.AirSpace;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla elytra flight, one tick at a time, so a pilot can try a control before
 * committing to it. Mirrors the fall-flying branch of {@code LivingEntity.travel}
 * (identical on 1.20.1 and 1.21.1), plus the sink trim the pilot adds after travel.
 */
final class ElytraSim {

    static final double GRAVITY = 0.08;
    /** Sink trim after travel: villagers are born fliers, so a glide sinks slower than a player's. */
    static final double ASSIST_SINK = -0.06;
    static final double ASSIST_FACTOR = 0.7;

    private ElytraSim() {}

    /** Look vector for a pitch and yaw, as {@code Entity.calculateViewVector}. */
    static Vec3 look(float pitch, float yaw) {
        float p = pitch * Mth.DEG_TO_RAD;
        float y = -yaw * Mth.DEG_TO_RAD;
        float cy = Mth.cos(y), sy = Mth.sin(y);
        float cp = Mth.cos(p), sp = Mth.sin(p);
        return new Vec3(sy * cp, -sp, cy * cp);
    }

    /** Velocity after one travel tick (before the move). */
    static Vec3 travel(Vec3 velocity, float pitch, float yaw) {
        Vec3 look = look(pitch, yaw);
        float f = pitch * Mth.DEG_TO_RAD;
        double d1 = Math.sqrt(look.x * look.x + look.z * look.z);
        double d3 = velocity.horizontalDistance();
        double d4 = look.length();
        double d5 = Math.cos(f);
        d5 = d5 * d5 * Math.min(1.0, d4 / 0.4);
        Vec3 v = velocity.add(0.0, GRAVITY * (-1.0 + d5 * 0.75), 0.0);
        if (v.y < 0.0 && d1 > 0.0) {
            double d6 = v.y * -0.1 * d5;
            v = v.add(look.x * d6 / d1, d6, look.z * d6 / d1);
        }
        if (f < 0.0F && d1 > 0.0) {
            double d10 = d3 * -Mth.sin(f) * 0.04;
            v = v.add(-look.x * d10 / d1, d10 * 3.2, -look.z * d10 / d1);
        }
        if (d1 > 0.0) {
            v = v.add((look.x / d1 * d3 - v.x) * 0.1, 0.0, (look.z / d1 * d3 - v.z) * 0.1);
        }
        return v.multiply(0.99F, 0.98F, 0.99F);
    }

    static Vec3 assist(Vec3 velocity) {
        return velocity.y < ASSIST_SINK ? new Vec3(velocity.x, velocity.y * ASSIST_FACTOR, velocity.z) : velocity;
    }

    /** Outcome of holding one control for a few ticks. */
    record Run(Vec3 position, Vec3 velocity, int collidedAt, double arc) {
        boolean collided() {
            return collidedAt >= 0;
        }
    }

    /**
     * Holds a pitch and yaw for {@code ticks}, checking the body against the world at
     * every position (a flier covers up to a block or so a tick, so skipping one could
     * skip a wall). {@code brakeTo}: cap on horizontal speed, the
     * approach air brake; NaN for none.
     */
    static Run run(AirSpace space, Vec3 position, Vec3 velocity, float pitch, float yaw, int ticks,
                   boolean assist, double brakeTo) {
        Vec3 pos = position;
        Vec3 vel = velocity;
        double arc = 0;
        for (int t = 0; t < ticks; t++) {
            vel = travel(vel, pitch, yaw);
            Vec3 next = pos.add(vel);
            arc += vel.length();
            if (!space.bodyFree(next, 0.1)) {
                return new Run(next, vel, t, arc);
            }
            pos = next;
            if (assist) vel = assist(vel);
            if (!Double.isNaN(brakeTo)) vel = brake(vel, brakeTo);
        }
        return new Run(pos, vel, -1, arc);
    }

    /** Scale horizontal speed down toward a cap, at most a fifth per tick (wings can only shed so much). */
    static Vec3 brake(Vec3 velocity, double cap) {
        double h = velocity.horizontalDistance();
        if (h <= cap || h < 1.0E-6) return velocity;
        double scale = Math.max(0.8, cap / h);
        return new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
    }
}
