package com.aetherianartificer.townstead.root.attachment;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * One physics chain's particles, stepped the same on the client that draws them and the server that judges
 * hits. Particle {@code i} is the tail of segment {@code i}: the pivot of bone {@code i + 1}, or the far end
 * of the last bone's geometry. Everything is in the entity's frame: origin at its feet, world axes. Whoever
 * runs it fills in the animated pose ({@link #head}, {@link #tail}, {@link #pitchAxis}) and reads back how
 * far each segment turned ({@link #dev}).
 */
public class ChainSim {

    /** One placed capsule a chain rests against, in blocks; equal ends make a sphere. */
    public record Capsule(String name, Vector3f a, Vector3f b, float radius) {

        /** The point on the capsule's core segment nearest {@code p}, into {@code dest}. */
        public Vector3f closest(Vector3f p, Vector3f dest) {
            float abx = b.x - a.x, aby = b.y - a.y, abz = b.z - a.z;
            float lengthSq = abx * abx + aby * aby + abz * abz;
            float t = lengthSq < 1e-10f ? 0f
                    : ((p.x - a.x) * abx + (p.y - a.y) * aby + (p.z - a.z) * abz) / lengthSq;
            t = Math.max(0f, Math.min(1f, t));
            return dest.set(a.x + abx * t, a.y + aby * t, a.z + abz * t);
        }
    }

    public static final float DT = 1f / 120f;
    private static final int MAX_STEPS = 24;
    private static final float MAX_OMEGA = 150f;
    // Under water: a point floats up at this share of its gravity scale, and drag scales up
    // and gains a floor so even a dragless chain moves like it's in water.
    private static final float WATER_FLOAT = 0.2f;
    private static final float WATER_DRAG_SCALE = 4f;
    private static final float WATER_DRAG = 6f;
    // Rounds of limit and contact solving per segment per substep.
    private static final int SOLVE_PASSES = 3;
    // The boxes one chain keeps.
    public static final int MAX_BOXES = 96;
    // The air speed, in blocks per second, at which a wind knob of 1 pushes as hard as a gravity of 1.
    private static final float WIND_REFERENCE = 4f;
    // Gusts vary this much from bone to bone, so a chain ripples instead of leaning as one piece.
    private static final float TURBULENCE = 0.4f;
    // A soaked chain droops this much more, holds still more, and takes this long to soak and dry.
    private static final float SOAK_DROOP = 0.1f;
    private static final float SOAK_SECONDS = 6f;
    private static final float DRY_SECONDS = 30f;
    // Share of the velocity off the goal's that a segment loses when it hits its limit.
    private static final float LIMIT_FRICTION = 0.4f;
    // Share it loses against something solid. Being pushed out isn't a throw, so hardly any survives.
    private static final float CONTACT_FRICTION = 0.85f;
    // The fastest a segment swings relative to its goal, in radians a second: quick, but never a crack.
    private static final float MAX_SWING = 9f;
    // Past the chain's reach, how far a block still counts: the thickest chain, plus a tick of movement.
    private static final float BLOCK_MARGIN = 1f;
    private static final float TWO_PI = (float) (Math.PI * 2);

    private final Vector3f down = new Vector3f(0f, -1f, 0f);
    private final Quaternionf q = new Quaternionf();
    private final Vector3f v = new Vector3f(), v2 = new Vector3f(), v3 = new Vector3f();
    private final Vector3f c1 = new Vector3f(), c2 = new Vector3f(), c3 = new Vector3f();

    public int n;
    public boolean ready;
    public float accumulator;
    public float time;
    public float lastBob;

    // Animated pose this frame and last frame (entity frame of the current frame).
    public final Vector3f head = new Vector3f(), prevHead = new Vector3f();
    public Vector3f[] tail, prevTail, pitchAxis, prevPitchAxis;
    // Simulated state.
    public Vector3f[] pos, vel, target;
    public boolean[] wet;
    public float[] halfThickness;
    // 0 dry to 1 soaked through, from rain or being under water.
    public float soak;
    public Quaternionf[] dev, prevDev;
    // Solid boxes around the chain, relative to where the entity was when gathered, refreshed each tick.
    public final float[] boxes = new float[MAX_BOXES * 6];
    public int boxCount;
    public long boxTick = Long.MIN_VALUE;
    public double boxX, boxY, boxZ;

    /** Makes room for {@code count} segments. Nothing is ready until a pose has been filled in. */
    public void allocate(int count) {
        n = count;
        ready = false;
        tail = vectors(n);
        prevTail = vectors(n);
        pitchAxis = vectors(n);
        prevPitchAxis = vectors(n);
        pos = vectors(n);
        vel = vectors(n);
        target = vectors(n);
        wet = new boolean[n];
        halfThickness = new float[n];
        dev = new Quaternionf[n];
        prevDev = new Quaternionf[n];
        for (int i = 0; i < n; i++) {
            dev[i] = new Quaternionf();
            prevDev[i] = new Quaternionf();
        }
    }

    /** Last frame's animated pose becomes the previous one, ready for this frame's. */
    public void rollPose() {
        prevHead.set(head);
        for (int i = 0; i < n; i++) {
            prevTail[i].set(tail[i]);
            prevPitchAxis[i].set(pitchAxis[i]);
        }
    }

    public void settle() {
        for (int i = 0; i < n; i++) {
            pos[i].set(tail[i]);
            vel[i].zero();
            target[i].set(tail[i]);
            dev[i].identity();
            prevDev[i].identity();
        }
        accumulator = 0f;
        ready = true;
    }

    /** Marks each point under water or not; the frame origin sits at the entity's feet. */
    public void wet(Level level, double x, double y, double z, float frameDt) {
        boolean soaking = false;
        for (int i = 0; i < n; i++) {
            double px = x + pos[i].x, py = y + pos[i].y, pz = z + pos[i].z;
            BlockPos block = BlockPos.containing(px, py, pz);
            FluidState fluid = level.getFluidState(block);
            wet[i] = fluid.is(FluidTags.WATER) && py < block.getY() + fluid.getHeight(level, block);
            soaking |= wet[i];
        }
        soaking = soaking || level.isRainingAt(BlockPos.containing(x + head.x, y + head.y + 1.0, z + head.z));
        float seconds = soaking ? SOAK_SECONDS : DRY_SECONDS;
        soak += ((soaking ? 1f : 0f) - soak) * (1f - (float) Math.exp(-frameDt / seconds));
    }

    public void dry() {
        java.util.Arrays.fill(wet, false);
        soak = 0f;
    }

    /** Refreshes the solid boxes the chain could reach, once a tick. */
    public void gatherBlocks(LivingEntity entity, double x, double y, double z) {
        long tick = entity.level().getGameTime();
        if (tick == boxTick) return;
        boxTick = tick;
        boxX = x;
        boxY = y;
        boxZ = z;
        boxCount = gather(entity, head, tail, n, x, y, z, boxes);
    }

    /** The solid boxes a chain from {@code head} could reach, relative to {@code x, y, z}; how many there are. */
    public static int gather(LivingEntity entity, Vector3f head, Vector3f[] tail, int n, double x, double y, double z, float[] into) {
        float reach = 0f;
        Vector3f last = head;
        float minX = head.x, minY = head.y, minZ = head.z, maxX = head.x, maxY = head.y, maxZ = head.z;
        for (int i = 0; i < n; i++) {
            reach += tail[i].distance(last);
            last = tail[i];
            minX = Math.min(minX, tail[i].x);
            minY = Math.min(minY, tail[i].y);
            minZ = Math.min(minZ, tail[i].z);
            maxX = Math.max(maxX, tail[i].x);
            maxY = Math.max(maxY, tail[i].y);
            maxZ = Math.max(maxZ, tail[i].z);
        }
        // The chain can swing its whole length past its pose, and the entity moves until the next refresh.
        double grow = reach + 1.0;
        AABB around = new AABB(x + minX - grow, y + minY - grow, z + minZ - grow,
                x + maxX + grow, y + maxY + grow, z + maxZ + grow);
        int count = 0;
        double range = reach + BLOCK_MARGIN;
        double hx = x + head.x, hy = y + head.y, hz = z + head.z;
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, around)) {
            for (AABB box : shape.toAabbs()) {
                if (count == MAX_BOXES) return count;
                // Blocks out of the chain's reach would use up the budget before the ones that matter.
                double nx = hx - Mth.clamp(hx, box.minX, box.maxX);
                double ny = hy - Mth.clamp(hy, box.minY, box.maxY);
                double nz = hz - Mth.clamp(hz, box.minZ, box.maxZ);
                if (nx * nx + ny * ny + nz * nz > range * range) continue;
                int b = count++ * 6;
                into[b] = (float) (box.minX - x);
                into[b + 1] = (float) (box.minY - y);
                into[b + 2] = (float) (box.minZ - z);
                into[b + 3] = (float) (box.maxX - x);
                into[b + 4] = (float) (box.maxY - y);
                into[b + 5] = (float) (box.maxZ - z);
            }
        }
        return count;
    }

    /** Moves the simulated points with the entity frame's origin, keeping their world velocity. */
    public void shift(float x, float y, float z) {
        for (int i = 0; i < n; i++) {
            pos[i].add(x, y, z);
            target[i].add(x, y, z);
        }
        prevHead.add(x, y, z);
        for (int i = 0; i < n; i++) prevTail[i].add(x, y, z);
    }

    public void simulate(AttachmentDef.PhysicsChain chain, float frameDt, float bob, float speed, float phase,
                         List<Capsule> colliders, float size, Vector3f air,
                         float boxOffsetX, float boxOffsetY, float boxOffsetZ) {
        accumulator = Math.min(accumulator + frameDt, MAX_STEPS * DT);
        int steps = (int) (accumulator / DT);
        if (steps == 0) return;
        accumulator -= steps * DT;
        Vector3f stepHead = new Vector3f();
        Vector3f[] stepTail = vectors(n);
        Vector3f[] lastTail = vectors(n);
        Vector3f stepAxis = new Vector3f();
        Vector3f restDir = new Vector3f();
        Vector3f aim = new Vector3f();
        Vector3f parentHead = new Vector3f();
        Vector3f carried = new Vector3f();
        Quaternionf carry = new Quaternionf();
        float lastStepBob = lastBob;
        for (int i = 0; i < n; i++) lastTail[i].set(prevTail[i]);
        for (int s = 0; s < steps; s++) {
            float u = (s + 1f) / steps;
            time += DT;
            float stepBob = Mth.lerp(u, lastBob, bob);
            float bobDelta = stepBob - lastStepBob;
            lastStepBob = stepBob;
            prevHead.lerp(head, u, stepHead);
            for (int i = 0; i < n; i++) prevTail[i].lerp(tail[i], u, stepTail[i]);
            carry.identity();
            parentHead.set(stepHead);
            for (int i = 0; i < n; i++) {
                prevDev[i].set(dev[i]);
                float t = n == 1 ? 0f : i / (float) (n - 1);
                Vector3f restHead = i == 0 ? stepHead : stepTail[i - 1];
                stepTail[i].sub(restHead, restDir);
                float length = restDir.length();
                if (length < 1e-5f) continue;
                restDir.div(length);
                float omega = Math.min(MAX_OMEGA, TWO_PI * Math.max(0.05f, chain.frequency().at(t)) * speed);
                float zeta = Math.max(0f, chain.dampingRatio().at(t));
                float inertia = Mth.clamp(chain.inertia().at(t), 0f, 1f);

                // Carry the share of anchor motion the chain doesn't feel, plus the gait bob.
                Vector3f p = pos[i];
                carried.set(stepTail[i]).sub(lastTail[i]).mul(1f - inertia);
                p.add(carried);
                p.y -= bobDelta;

                // Aim mostly along the animated direction, with a share of the parent's
                // swing: enough for a wave, not enough to stack into a whip.
                carry.transform(restDir, aim);
                aim.mul(BodyPose.CARRY).add(v.set(restDir).mul(1f - BodyPose.CARRY)).normalize();
                prevPitchAxis[i].lerp(pitchAxis[i], u, stepAxis);
                if (stepAxis.lengthSquared() > 1e-8f) stepAxis.normalize();
                // rotateAxis divides by the axis length, so a degenerate axis would poison the chain.
                if (chain.sway() != 0f && stepAxis.lengthSquared() > 1e-8f) {
                    float sway = chain.sway() * Mth.DEG_TO_RAD * (1f - soak * 0.6f);
                    float jointPhase = phase + i * 0.8f;
                    q.identity().rotateAxis(sway * Mth.sin(time * 1.4f * chain.swaySpeed() + jointPhase),
                            stepAxis.x, stepAxis.y, stepAxis.z);
                    q.transform(aim);
                    aim.cross(stepAxis, v2);
                    if (v2.lengthSquared() > 1e-8f) {
                        v2.normalize();
                        q.identity().rotateAxis(sway * Mth.sin(time * 1.0f * chain.swaySpeed() + jointPhase + 1.3f),
                                v2.x, v2.y, v2.z);
                        q.transform(aim);
                    }
                }
                Vector3f goal = v3.set(aim).mul(length).add(parentHead);
                // The carried share already moved the point, so it isn't lag to damp away.
                Vector3f goalVel = v.set(goal).sub(target[i]).sub(carried).div(DT);
                target[i].set(goal);

                // Spring toward the goal, damped relative to the goal's own motion.
                Vector3f velocity = vel[i];
                velocity.sub(goalVel).mul((float) Math.exp(-2f * zeta * omega * DT)).add(goalVel);
                velocity.add(v2.set(goal).sub(p).mul(omega * omega * DT));
                // Under water the point floats and drags through still water instead of air.
                float gravity = wet[i] ? -WATER_FLOAT : Math.max(0f, chain.gravity().at(t)) + soak * SOAK_DROOP;
                float drag = Math.max(0f, chain.drag().at(t)) * (1f + soak);
                velocity.add(v2.set(down).mul(gravity * omega * omega * length * DT));
                if (wet[i]) {
                    velocity.mul((float) Math.exp(-(drag * WATER_DRAG_SCALE + WATER_DRAG) * DT));
                } else {
                    velocity.sub(air).mul((float) Math.exp(-drag * DT)).add(air);
                    // Wind pushes like gravity does, so a chain's lean doesn't depend on its stiffness.
                    float gust = 1f + TURBULENCE * Mth.sin(time * 2.7f + phase + i * 1.3f);
                    float push = Math.max(0f, chain.wind().at(t)) / WIND_REFERENCE * gust;
                    velocity.add(v2.set(air).mul(push * omega * omega * length * DT));
                }

                float bx = p.x, by = p.y, bz = p.z;
                p.fma(DT, velocity);
                float radius = (chain.radius() >= 0f ? chain.radius() : halfThickness[i]) / 16f * size;
                boolean clamped = false, touching = false;
                for (int pass = 0; pass < SOLVE_PASSES; pass++) {
                    clamped |= constrain(chain, t, parentHead, p, aim, stepAxis, length);
                    boolean hit = collide(chain, colliders, parentHead, p, goal, length, radius);
                    hit |= ChainCollision.outOfBoxes(parentHead, p, length, radius, boxes, boxCount,
                            boxOffsetX, boxOffsetY, boxOffsetZ);
                    if (!hit) break;
                    touching = true;
                }
                velocity.set(p.x - bx, p.y - by, p.z - bz).div(DT);
                // Hitting the limit spends energy instead of skating along the cone.
                if (touching) velocity.sub(goalVel).mul(1f - CONTACT_FRICTION).add(goalVel);
                else if (clamped) velocity.sub(goalVel).mul(1f - LIMIT_FRICTION).add(goalVel);
                float swing = v2.set(velocity).sub(goalVel).length();
                float fastest = MAX_SWING * length;
                if (swing > fastest) velocity.set(v2).mul(fastest / swing).add(goalVel);

                // This segment's simulated direction, as a turn from its animated one.
                v.set(p).sub(parentHead).normalize();
                dev[i].rotationTo(restDir, v);
                carry.set(dev[i]);
                parentHead.set(p);
            }
            for (int i = 0; i < n; i++) lastTail[i].set(stepTail[i]);
        }
        // A NaN never decays out of the sim and renders the bone and everything below it
        // invisible, so a chain that blew up snaps back to its pose instead.
        for (int i = 0; i < n; i++) {
            if (!pos[i].isFinite() || !vel[i].isFinite() || !finite(dev[i]) || !finite(prevDev[i])) {
                settle();
                return;
            }
        }
    }

    private static boolean finite(Quaternionf quaternion) {
        return Float.isFinite(quaternion.x) && Float.isFinite(quaternion.y) && Float.isFinite(quaternion.z)
                && Float.isFinite(quaternion.w);
    }

    /** Keeps the segment out of every collider the chain rests against. */
    private static boolean collide(AttachmentDef.PhysicsChain chain, List<Capsule> colliders,
                                   Vector3f head, Vector3f p, Vector3f goal, float length, float radius) {
        if (colliders.isEmpty() || chain.collide().isEmpty()) return false;
        boolean all = chain.collide().contains("*");
        boolean hit = false;
        for (Capsule capsule : colliders) {
            if (!all && !chain.collide().contains(capsule.name())) continue;
            hit |= ChainCollision.outOfCapsule(head, p, goal, length, capsule.radius() + radius,
                    capsule.a(), capsule.b());
        }
        return hit;
    }

    /** Holds the segment at its length, inside its hinge plane, and within its limit of the aim. */
    boolean constrain(AttachmentDef.PhysicsChain chain, float t, Vector3f head, Vector3f p,
                      Vector3f aim, Vector3f pitchAxis, float length) {
        Vector3f dir = c1.set(p).sub(head);
        switch (chain.hinge()) {
            case "pitch" -> dir.sub(c2.set(pitchAxis).mul(dir.dot(pitchAxis)));
            case "side" -> {
                Vector3f normal = c2.set(aim).cross(pitchAxis);
                if (normal.lengthSquared() > 1e-8f) {
                    normal.normalize();
                    dir.sub(normal.mul(dir.dot(normal)));
                }
            }
            default -> {}
        }
        if (dir.lengthSquared() < 1e-10f) dir.set(aim);
        dir.normalize();
        float limit = Mth.clamp(chain.limit().at(t), 0f, 180f) * Mth.DEG_TO_RAD;
        float cos = Mth.clamp(dir.dot(aim), -1f, 1f);
        boolean clamped = Math.acos(cos) > limit;
        if (clamped) {
            Vector3f perp = c2.set(dir).sub(c3.set(aim).mul(cos));
            if (perp.lengthSquared() < 1e-10f) perp.set(pitchAxis).cross(aim);
            if (perp.lengthSquared() < 1e-10f) {
                perp.set(Math.abs(aim.y) < 0.9f ? down : c3.set(1f, 0f, 0f)).cross(aim);
            }
            perp.normalize();
            dir.set(aim).mul(Mth.cos(limit)).add(perp.mul(Mth.sin(limit)));
        }
        p.set(dir).mul(length).add(head);
        return clamped;
    }

    public static Vector3f[] vectors(int count) {
        Vector3f[] out = new Vector3f[count];
        for (int i = 0; i < count; i++) out[i] = new Vector3f();
        return out;
    }
}
