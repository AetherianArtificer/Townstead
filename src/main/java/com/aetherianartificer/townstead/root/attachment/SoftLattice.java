package com.aetherianartificer.townstead.root.attachment;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * How a soft part moves as drawn: a small grid of particles that springs toward the part's animated shape by
 * shape matching (Müller et al. 2005), keeps its volume, sags, lags behind its bearer and is pushed out of
 * blocks. The mesh rides it: each vertex moves as the grid around it moved off its rest (see
 * {@link #displacement}). Positions are in blocks from the bearer's feet, world axes, as the chain sim keeps them.
 *
 * <p>Only for looks, and only on clients, which needn't agree; what moves and stops the bearer is
 * {@link SoftEnvelope}, and the grid is pulled toward it so the two never visibly disagree.</p>
 */
public final class SoftLattice {

    private static final float STEP = 1f / 120f;
    private static final int MAX_STEPS = 12;
    // The most a contact moves a particle in one step, in blocks: growing into a wall comes out over time.
    private static final float MAX_CORRECTION = 0.35f;
    private static final float FRICTION = 0.6f;
    // Share of its spring a particle keeps while it presses on something.
    // Not too little, or the grid folds through itself where it's pressed.
    private static final float CONTACT_GIVE = 0.45f;
    // How many steps a particle keeps giving after it last touched: a tenth of a second.
    private static final int CONTACT_MEMORY = 12;
    private static final float MAX_SPEED = 6f;
    // How far a pressed point may fall behind where its shape wants it before it follows along the rock.
    private static final float FOLLOW_AFTER = 1.0f;
    // How fast a pressed point follows along the rock, in blocks a step: about two blocks a second.
    private static final float FOLLOW_SPEED = 0.018f;
    private static final int POLAR_ITERATIONS = 8;

    public final int nx, ny, nz, count;
    /** Where each particle is, where it was a step ago, and where the animated shape would put it. */
    public final Vector3f[] pos, prev, goal;
    /**
     * The box the blocks were looked for in, from the feet: outside it nothing is known, so a particle is never
     * pushed out of rock into it (down through a floor into what wasn't searched). Null means anywhere.
     */
    public float[] known;
    /** The joint the grid hangs from, this frame. */
    public final Vector3f hinge = new Vector3f();
    // Steps since each particle last pressed on something: what touches gives a while, rather than spring back in.
    final int[] touching;
    public boolean ready;
    private float accumulator;
    private final Quaternionf rotation = new Quaternionf();

    // Scratch.
    private final Vector3f c1 = new Vector3f(), c2 = new Vector3f(), v = new Vector3f();
    private final Matrix3f apq = new Matrix3f(), aqq = new Matrix3f(), m = new Matrix3f(), r = new Matrix3f();

    public SoftLattice(int nx, int ny, int nz) {
        this.nx = Math.max(2, nx);
        this.ny = Math.max(2, ny);
        this.nz = Math.max(2, nz);
        count = this.nx * this.ny * this.nz;
        pos = vectors(count);
        prev = vectors(count);
        goal = vectors(count);
        touching = new int[count];
        java.util.Arrays.fill(touching, CONTACT_MEMORY);
    }

    private static Vector3f[] vectors(int n) {
        Vector3f[] out = new Vector3f[n];
        for (int i = 0; i < n; i++) out[i] = new Vector3f();
        return out;
    }

    /** How the whole grid has turned from its rest shape, as last fitted. */
    public Quaternionf rotation(Quaternionf dest) {
        return dest.set(rotation);
    }

    public int index(int i, int j, int k) {
        return (k * ny + j) * nx + i;
    }

    /**
     * How the part feels, from {@code squish} 0 (a taut, full balloon) to 1 (a slack water balloon).
     * {@code stiffness} is how hard it holds its shape each step, {@code squash} how much of that shape may
     * stretch or flatten rather than only turn, {@code damping} how fast a swing dies, {@code sag} its weight,
     * {@code home} how hard it swings back to its rest angle about the hinge, and {@code droop} an extra hang
     * built into its rest shape.
     */
    public record Feel(float stiffness, float squash, float damping, float sag, float home, float droop, float swing) {
        public static Feel of(float squish) {
            float s = Math.max(0f, Math.min(1f, squish));
            return new Feel(lerp(0.8f, 0.2f, s), lerp(0.03f, 0.5f, s), lerp(2.2f, 3.5f, s), 9.8f,
                    lerp(0.006f, 0.0015f, s), lerp(0f, 0.2f, s), (float) Math.toRadians(lerp(35f, 55f, s)));
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    /**
     * Sets this frame's animated shape: the grid of {@code goal} points, and the {@code hinge} it hangs from (the
     * joint where the part meets what carries it). The grid turns about the hinge as one piece and never leaves it.
     * {@code droop} builds a hang into the rest shape: points further from the hinge sit lower, by that share of
     * their distance, so a part lies on whatever holds it up and hangs where nothing does.
     */
    public void setGoal(Vector3f[] goals, Vector3f hinge, float droop) {
        this.hinge.set(hinge);
        float reach = 0f;
        for (int i = 0; i < count; i++) {
            goal[i].set(goals[i]);
            reach = Math.max(reach, goal[i].distance(hinge));
        }
        if (droop > 0f && reach > 1e-4f) {
            for (int i = 0; i < count; i++) {
                float d = goal[i].distance(hinge);
                goal[i].y -= droop * d * d / reach;
            }
        }
        if (!ready) settle();
    }

    /**
     * The part's rest shape changed size this frame (it filled or emptied): every particle moves with its own
     * goal, where it was included, so growing never reads as a shove and sets it bouncing.
     */
    public void reshape(Vector3f[] before, Vector3f[] after) {
        if (!ready) return;
        Vector3f d = new Vector3f();
        for (int i = 0; i < count && i < before.length && i < after.length; i++) {
            d.set(after[i]).sub(before[i]);
            pos[i].add(d);
            prev[i].add(d);
        }
    }

    /** Puts every particle on its goal, at rest. */
    public void settle() {
        for (int i = 0; i < count; i++) {
            pos[i].set(goal[i]);
            prev[i].set(goal[i]);
        }
        rotation.identity();
        accumulator = 0f;
        ready = true;
    }

    /** Moves the particles with the world while the bearer moves by {@code d}: they stay where they were. */
    public void shift(float dx, float dy, float dz) {
        for (int i = 0; i < count; i++) {
            pos[i].add(dx, dy, dz);
            prev[i].add(dx, dy, dz);
        }
    }

    /**
     * Runs the grid forward {@code seconds}. {@code solids} are six numbers a box from the feet, and
     * {@code envelope} the squashed lobes the same way; either may be empty.
     */
    public void step(float seconds, Feel feel, float radius, float[] solids, int solidCount, float[] envelope, int lobeCount) {
        step(seconds, feel, radius, solids, solidCount, envelope, lobeCount, java.util.List.of());
    }

    /**
     * As above, also kept out of {@code bodies}: the bearer's own limbs, so a swinging part doesn't pass
     * through its legs. A capsule the hinge itself sits in (the torso it hangs from) is left out.
     */
    public void step(float seconds, Feel feel, float radius, float[] solids, int solidCount, float[] envelope, int lobeCount,
                     java.util.List<ChainSim.Capsule> bodies) {
        if (!ready) return;
        accumulator = Math.min(accumulator + seconds, STEP * MAX_STEPS);
        float drag = (float) Math.exp(-feel.damping() * STEP);
        while (accumulator >= STEP) {
            accumulator -= STEP;
            for (int i = 0; i < count; i++) {
                // Verlet: carry on as it was going, slowed, and sag.
                v.set(pos[i]).sub(prev[i]).mul(drag);
                float speed = v.length() / STEP;
                if (speed > MAX_SPEED) v.mul(MAX_SPEED / speed);
                prev[i].set(pos[i]);
                pos[i].add(v).add(0f, -feel.sag() * STEP * STEP, 0f);
            }
            match(feel, solids, solidCount, radius);
            limitSwing(feel, solids, solidCount);
            home(feel);
            if (lobeCount > 0) towardEnvelope(envelope, lobeCount, radius);
            if (!bodies.isEmpty()) collideBodies(bodies, radius);
            if (solidCount > 0) collide(solids, solidCount, radius);
        }
    }

    /** Spheres of {@code radius} out of the bearer's limbs, softly, skipping whatever the hinge is inside. */
    private void collideBodies(java.util.List<ChainSim.Capsule> bodies, float radius) {
        Vector3f near = new Vector3f();
        for (ChainSim.Capsule capsule : bodies) {
            float reach = capsule.radius() + radius;
            capsule.closest(hinge, near);
            if (near.distanceSquared(hinge) < reach * reach) continue;
            for (int i = 0; i < count; i++) {
                Vector3f p = pos[i];
                capsule.closest(p, near);
                float dx = p.x - near.x, dy = p.y - near.y, dz = p.z - near.z;
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 >= reach * reach || d2 < 1e-10f) continue;
                float d = (float) Math.sqrt(d2);
                float nx = dx / d, ny = dy / d, nz = dz / d;
                // Half the way a step: a leg nudges the part aside rather than knocking it.
                float depth = (reach - d) * 0.5f;
                p.add(nx * depth, ny * depth, nz * depth);
                rub(i, nx, ny, nz);
            }
        }
    }

    /** Shape matching: each particle moves toward where the best fit of the goal shape onto the grid puts it. */
    private void match(Feel feel, float[] solids, int solidCount, float radius) {
        // About the hinge, not the middle: the part turns on its joint and can't drift off it.
        Vector3f cp = c1.set(hinge), cg = c2.set(hinge);
        apq.zero();
        aqq.zero();
        Vector3f p = new Vector3f(), q = new Vector3f();
        for (int i = 0; i < count; i++) {
            p.set(pos[i]).sub(cp);
            q.set(goal[i]).sub(cg);
            addOuter(apq, p, q);
            addOuter(aqq, q, q);
        }
        extractRotation(apq);
        rotation.get(r);
        Matrix3f fit = r;
        if (Math.abs(aqq.determinant()) > 1e-9f) {
            // The linear fit, scaled to keep the volume, blended with the rigid one for give.
            m.set(apq).mul(aqq.invert(new Matrix3f()));
            float det = m.determinant();
            if (det > 1e-6f) {
                m.scale(1f / (float) Math.cbrt(det));
                Matrix3f blend = new Matrix3f(r).scale(1f - feel.squash());
                m.scale(feel.squash());
                blend.add(m);
                fit = blend;
            }
        }
        Vector3f target = new Vector3f();
        for (int i = 0; i < count; i++) {
            q.set(goal[i]).sub(cg);
            fit.transform(q, target).add(cp);
            // Where the shape wants it is inside rock: aim for the rock's surface instead. Pulling it in for the
            // contact to throw it back out, many times a second, made a part pressed on a wall flail; holding it
            // still left it behind as the body walked on, and it snapped.
            if (solidCount > 0 && inside(target.x, target.y, target.z, solids, solidCount) >= 0) {
                // Close by, a pressed point holds: that keeps it calm against a wall it's growing into. Left well
                // behind as the body walks on, it follows as far as open space goes, so it never stretches and snaps.
                if (pos[i].distanceSquared(target) < FOLLOW_AFTER * FOLLOW_AFTER) continue;
                if (!towardOpen(pos[i], target, solids, solidCount)) continue;
                float gap = pos[i].distance(target);
                if (gap > FOLLOW_SPEED) pos[i].lerp(target, FOLLOW_SPEED / gap);
                else pos[i].set(target);
                continue;
            }
            pos[i].lerp(target, touching[i] < CONTACT_MEMORY ? feel.stiffness() * CONTACT_GIVE : feel.stiffness());
        }
    }

    /**
     * A waist only bends so far: past {@code swing} from its rest angle the whole part is turned back about the
     * hinge, so what's in its way has to press and flatten it instead of tipping it over.
     */
    private void limitSwing(Feel feel, float[] solids, int solidCount) {
        float w = Math.min(1f, Math.abs(rotation.w));
        float angle = 2f * (float) Math.acos(w);
        if (angle <= feel.swing() || angle < 1e-4f) return;
        Quaternionf allowed = new Quaternionf().slerp(rotation, feel.swing() / angle);
        Quaternionf back = new Quaternionf(allowed).mul(new Quaternionf(rotation).invert());
        Vector3f arm = new Vector3f();
        Vector3f turned = new Vector3f();
        for (int i = 0; i < count; i++) {
            // Where it was turns back with it, so meeting the limit stops the swing rather than flinging it.
            arm.set(pos[i]).sub(hinge);
            back.transform(arm);
            turned.set(hinge).add(arm);
            // Never turned back into rock: what's pressed stays pressed, and the rest comes round.
            if (solidCount > 0 && inside(turned.x, turned.y, turned.z, solids, solidCount) >= 0) continue;
            pos[i].set(turned);
            arm.set(prev[i]).sub(hinge);
            back.transform(arm);
            prev[i].set(hinge).add(arm);
        }
        rotation.set(allowed);
    }

    /** Swings the part back toward its rest angle about the hinge, gently, so it hangs and sways rather than spins. */
    private void home(Feel feel) {
        for (int i = 0; i < count; i++) {
            pos[i].lerp(goal[i], feel.home());
        }
    }

    private static void addOuter(Matrix3f into, Vector3f a, Vector3f b) {
        into.m00 += a.x * b.x; into.m10 += a.x * b.y; into.m20 += a.x * b.z;
        into.m01 += a.y * b.x; into.m11 += a.y * b.y; into.m21 += a.y * b.z;
        into.m02 += a.z * b.x; into.m12 += a.z * b.y; into.m22 += a.z * b.z;
    }

    /** The rotation part of {@code a}, warm-started from last step's (Müller et al. 2016). */
    private void extractRotation(Matrix3f a) {
        Matrix3f rm = new Matrix3f();
        Vector3f rc = new Vector3f(), ac = new Vector3f(), omega = new Vector3f(), cross = new Vector3f();
        for (int it = 0; it < POLAR_ITERATIONS; it++) {
            rotation.get(rm);
            omega.zero();
            float dot = 0f;
            for (int c = 0; c < 3; c++) {
                rm.getColumn(c, rc);
                a.getColumn(c, ac);
                omega.add(rc.cross(ac, cross));
                dot += rc.dot(ac);
            }
            omega.div(Math.abs(dot) + 1e-9f);
            float w = omega.length();
            if (w < 1e-9f) break;
            rotation.premul(new Quaternionf().fromAxisAngleRad(omega.x / w, omega.y / w, omega.z / w, w)).normalize();
        }
    }

    /** A particle outside every squashed lobe drifts back toward the nearest, so the look keeps to the envelope. */
    private void towardEnvelope(float[] lobes, int lobeCount, float radius) {
        for (int i = 0; i < count; i++) {
            Vector3f p = pos[i];
            float best = Float.MAX_VALUE, bx = 0f, by = 0f, bz = 0f;
            for (int l = 0; l < lobeCount; l++) {
                int b = l * 6;
                float qx = clamp(p.x, lobes[b] - radius, lobes[b + 3] + radius);
                // Only sideways: a part may tilt up off a wall or hang over an edge where nothing stops it.
                float qy = p.y;
                float qz = clamp(p.z, lobes[b + 2] - radius, lobes[b + 5] + radius);
                float d = (qx - p.x) * (qx - p.x) + (qy - p.y) * (qy - p.y) + (qz - p.z) * (qz - p.z);
                if (d < best) {
                    best = d;
                    bx = qx;
                    by = qy;
                    bz = qz;
                }
            }
            if (best > 1e-8f) p.lerp(v.set(bx, by, bz), 0.15f);
        }
    }

    /**
     * Spheres of {@code radius} out of boxes, with friction. A particle buried in rock leaves by the shortest way
     * out into open air, through however many blocks that takes, never into the block next door.
     */
    private void collide(float[] solids, int solidCount, float radius) {
        for (int i = 0; i < count; i++) {
            if (touching[i] < CONTACT_MEMORY) touching[i]++;
            Vector3f p = pos[i];
            if (inside(p.x, p.y, p.z, solids, solidCount) >= 0) {
                float best = Float.MAX_VALUE;
                int bestAxis = -1, bestSign = 0;
                for (int axis = 0; axis < 3; axis++) {
                    for (int sign = -1; sign <= 1; sign += 2) {
                        float out = escape(p, axis, sign, solids, solidCount);
                        if (out < Float.MAX_VALUE && known != null && !knownAfter(p, axis, sign, out + radius)) continue;
                        if (out < best) {
                            best = out;
                            bestAxis = axis;
                            bestSign = sign;
                        }
                    }
                }
                if (bestAxis >= 0) {
                    // All the way out at once: a spring would pull a part-way particle straight back in.
                    float move = best + radius;
                    float nx = bestAxis == 0 ? bestSign : 0f, ny = bestAxis == 1 ? bestSign : 0f, nz = bestAxis == 2 ? bestSign : 0f;
                    p.add(nx * move, ny * move, nz * move);
                    rub(i, nx, ny, nz);
                    touching[i] = 0;
                }
                continue;
            }
            for (int s = 0; s < solidCount; s++) {
                int b = s * 6;
                float qx = clamp(p.x, solids[b], solids[b + 3]);
                float qy = clamp(p.y, solids[b + 1], solids[b + 4]);
                float qz = clamp(p.z, solids[b + 2], solids[b + 5]);
                float dx = p.x - qx, dy = p.y - qy, dz = p.z - qz;
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 >= radius * radius || d2 <= 1e-10f) continue;
                float d = (float) Math.sqrt(d2);
                float nx = dx / d, ny = dy / d, nz = dz / d;
                float depth = radius - d;
                p.add(nx * depth, ny * depth, nz * depth);
                rub(i, nx, ny, nz);
                touching[i] = 0;
            }
        }
    }

    /**
     * A contact soaks up the blow: the push out of a block mustn't become speed, or the part bounces off the
     * ground like rubber. Whatever moves it along the normal this step is taken back out of its velocity, and
     * friction holds back what it slid along the surface.
     */
    private void rub(int i, float nx, float ny, float nz) {
        Vector3f vel = v.set(pos[i]).sub(prev[i]);
        float normal = vel.dot(nx, ny, nz);
        if (normal > 0f) prev[i].add(nx * normal, ny * normal, nz * normal);
        vel.set(pos[i]).sub(prev[i]);
        normal = vel.dot(nx, ny, nz);
        vel.sub(nx * normal, ny * normal, nz * normal);
        prev[i].add(vel.mul(FRICTION));
    }

    /**
     * Shortens the way from {@code from} to {@code target} to the last point before rock, into {@code target};
     * false when {@code from} is itself in rock. It changes smoothly as things move, where the nearest way out
     * of rock can jump from one face to another.
     */
    private boolean towardOpen(Vector3f from, Vector3f target, float[] solids, int solidCount) {
        if (inside(from.x, from.y, from.z, solids, solidCount) >= 0) return false;
        float lo = 0f, hi = 1f;
        float fx = from.x, fy = from.y, fz = from.z;
        float dx = target.x - fx, dy = target.y - fy, dz = target.z - fz;
        for (int it = 0; it < 7; it++) {
            float mid = (lo + hi) * 0.5f;
            if (inside(fx + dx * mid, fy + dy * mid, fz + dz * mid, solids, solidCount) >= 0) hi = mid;
            else lo = mid;
        }
        target.set(fx + dx * lo, fy + dy * lo, fz + dz * lo);
        return true;
    }

    /** Whether a point moved {@code by} along one axis is still where the blocks were looked for. */
    private boolean knownAfter(Vector3f p, int axis, int sign, float by) {
        float x = p.x + (axis == 0 ? sign * by : 0f), y = p.y + (axis == 1 ? sign * by : 0f), z = p.z + (axis == 2 ? sign * by : 0f);
        return x >= known[0] && x <= known[3] && y >= known[1] && y <= known[4] && z >= known[2] && z <= known[5];
    }

    /** The first box holding this point, or -1. */
    private static int inside(float x, float y, float z, float[] solids, int solidCount) {
        for (int s = 0; s < solidCount; s++) {
            int b = s * 6;
            if (x > solids[b] && x < solids[b + 3] && y > solids[b + 1] && y < solids[b + 4]
                    && z > solids[b + 2] && z < solids[b + 5]) {
                return s;
            }
        }
        return -1;
    }

    /** How far a point has to go along one axis, one way, to be out of every box; past a few blocks, never. */
    private static float escape(Vector3f p, int axis, int sign, float[] solids, int solidCount) {
        float x = p.x, y = p.y, z = p.z, travelled = 0f;
        for (int hop = 0; hop < 8; hop++) {
            int s = inside(x, y, z, solids, solidCount);
            if (s < 0) return travelled;
            int b = s * 6;
            float at = axis == 0 ? x : axis == 1 ? y : z;
            float face = sign > 0 ? solids[b + 3 + axis] : solids[b + axis];
            float step = Math.abs(face - at) + 1e-3f;
            travelled += step;
            if (axis == 0) x += sign * step;
            else if (axis == 1) y += sign * step;
            else z += sign * step;
        }
        return Float.MAX_VALUE;
    }

    private static float clamp(float value, float lo, float hi) {
        return value < lo ? lo : Math.min(value, hi);
    }

    /**
     * How far the grid has moved a point off its rest: trilinear across the cell it falls in. {@code u},
     * {@code vv}, {@code w} run 0..1 across the whole grid.
     */
    public Vector3f displacement(float u, float vv, float w, Vector3f out) {
        float tx = clamp(u, 0f, 1f) * (nx - 1), ty = clamp(vv, 0f, 1f) * (ny - 1), tz = clamp(w, 0f, 1f) * (nz - 1);
        int i = Math.min((int) tx, nx - 2), j = Math.min((int) ty, ny - 2), k = Math.min((int) tz, nz - 2);
        float fx = tx - i, fy = ty - j, fz = tz - k;
        out.zero();
        for (int c = 0; c < 8; c++) {
            int di = c & 1, dj = (c >> 1) & 1, dk = (c >> 2) & 1;
            float weight = (di == 1 ? fx : 1f - fx) * (dj == 1 ? fy : 1f - fy) * (dk == 1 ? fz : 1f - fz);
            if (weight == 0f) continue;
            int n = index(i + di, j + dj, k + dk);
            out.add((pos[n].x - goal[n].x) * weight, (pos[n].y - goal[n].y) * weight, (pos[n].z - goal[n].z) * weight);
        }
        return out;
    }
}
