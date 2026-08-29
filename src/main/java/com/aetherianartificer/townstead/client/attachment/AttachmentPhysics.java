package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side secondary motion for attachment bone chains: each joint is a damped
 * angular spring (pitch + side swing) driven by the bearer's movement — walking makes
 * a tail trail and bounce, turning whips it sideways, gravity droops it, and an
 * optional ambient sway keeps it alive at idle. Joints follow their parent with one
 * tick of lag, which is what reads as a whip rather than a rigid stick.
 *
 * <p>The sim steps on the entity's tick clock (catching up at most {@link #MAX_STEPS}
 * ticks per frame, so off-screen time doesn't explode the springs) and renders
 * interpolated between the last two steps. It composes after state poses and whatever
 * animated the anchor bone, because the drive comes from entity motion, not bone
 * motion. State is per entity + definition + anchor and evicted when stale.</p>
 */
public final class AttachmentPhysics {

    private static final Map<Integer, EntityState> STATES = new HashMap<>();
    private static final long EVICT_AFTER_MS = 10_000;
    private static final int MAX_STEPS = 5;
    private static final float SPRING = 0.3f;        // stiffness 1.0 -> 0.3 deg/deg/tick^2 (stable under damping)
    private static final float MAX_DRIVE = 25f;      // per-tick impulse cap (deg/tick^2)
    private static final float TELEPORT = 4f;        // blocks/tick beyond which movement is a teleport, not motion
    // Base drive scales, calibrated so response 1.0 is the intended strength on every channel.
    // Forward and lateral are sustained pushes (they fire every tick the bearer moves), so they
    // run much lower than vertical: a sprint at full forward settles the chain into a lean, not
    // pinned against maxAngle. The chain's `response` multipliers tune each per attachment.
    private static final float DRIVE_VERTICAL = 30f, DRIVE_FORWARD = 3f, DRIVE_LATERAL = 5f, DRIVE_TURN = 0.6f;
    // Acceleration impulse scale (the whip on starts, stops, jumps, landings); `snap` tunes it per chain.
    // 40 gives a sprint-stop flick of roughly 20 degrees at the tip — noticeable, not a slam.
    private static final float DRIVE_SNAP = 40f;

    private AttachmentPhysics() {}

    public static void onManifest() {
        STATES.clear();
    }

    /**
     * The current physics rotations for this entity+attachment at one anchor, keyed by
     * bone, in the same convention as pose bone rotations. Pitch and side swing land on
     * axes chosen per chain direction (a vertical tail leans sideways instead of
     * corkscrewing around its own length). Empty when the definition has no chains.
     */
    public static Map<String, float[]> sample(LivingEntity entity, AttachmentDef def, int anchorIndex,
                                              boolean mirror, float ageInTicks,
                                              com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo geo) {
        if (def.physics().isEmpty()) return Map.of();
        EntityState entityState = STATES.computeIfAbsent(entity.getId(), k -> new EntityState());
        entityState.lastTouchMs = Util.getMillis();
        if (STATES.size() > 256) evictStale();
        ChainSetState state = entityState.anchors.computeIfAbsent(def.id() + "#" + anchorIndex,
                k -> new ChainSetState(def));
        state.resolveSlots(def, geo);

        int tick = (int) ageInTicks;
        if (state.lastTick == Integer.MIN_VALUE || tick < state.lastTick) {
            state.rememberFrame(entity);
            state.lastTick = tick;
        }
        int elapsed = tick - state.lastTick;
        if (elapsed > 0) {
            // Velocity averages over the real elapsed time; only MAX_STEPS ticks of spring
            // integration run, so a long off-screen gap skips sim time instead of spiking drive.
            int steps = Math.min(MAX_STEPS, elapsed);
            float[] drive = driveFor(entity, state, elapsed, mirror, anchorIndex);
            for (int s = 0; s < steps; s++) state.step(def, drive, tick - steps + s + 1, anchorIndex);
            state.rememberFrame(entity);
            state.lastTick = tick;
        }

        float partial = ageInTicks - tick;
        Map<String, float[]> out = new LinkedHashMap<>();
        for (int c = 0; c < def.physics().size(); c++) {
            AttachmentDef.PhysicsChain chain = def.physics().get(c);
            java.util.List<String> chainBones = chain.effectiveBones();
            Joint[] joints = state.chains[c];
            for (int j = 0; j < joints.length; j++) {
                Joint joint = joints[j];
                float[] rotation = new float[3];
                rotation[state.pitchSlot[c]] = Mth.lerp(partial, joint.prevX, joint.x);
                rotation[state.sideSlot[c]] = Mth.lerp(partial, joint.prevY, joint.y);
                out.put(chainBones.get(j), rotation);
            }
        }
        return out;
    }

    /**
     * The movement impulse components since the last sim batch, averaged per elapsed
     * tick: {@code [vertical, forward, lateral, turn, snapVertical, snapForward,
     * snapLateral]}, each pre-scaled by its base drive constant so a chain's
     * {@code response} multipliers apply on top. The first four are velocity terms
     * (drag: the chain streams opposite the airflow — running lifts a tail up and
     * back, turning right lags it left); the snap terms are acceleration (inertia:
     * stopping whips it forward, landing slams it down). Signs are chosen so
     * response 1.0 is the physically correct direction for the canonical chain
     * mappings in {@link ChainSetState#resolveSlots}. A mirrored anchor pre-flips
     * the side terms so the application-side mirror flip restores world-consistent
     * motion.
     */
    private static float[] driveFor(LivingEntity entity, ChainSetState state, int elapsed,
                                    boolean mirror, int anchorIndex) {
        double vx = (entity.getX() - state.lastX) / elapsed;
        double vy = (entity.getY() - state.lastY) / elapsed;
        double vz = (entity.getZ() - state.lastZ) / elapsed;
        float yawDelta = Mth.wrapDegrees(entity.yBodyRot - state.lastYaw) / elapsed;
        if (Math.abs(vx) > TELEPORT || Math.abs(vy) > TELEPORT || Math.abs(vz) > TELEPORT) {
            state.forgetVelocity();
            return new float[7];
        }
        double ax = state.hasVelocity ? (vx - state.lastVelX) / elapsed : 0;
        double ay = state.hasVelocity ? (vy - state.lastVelY) / elapsed : 0;
        double az = state.hasVelocity ? (vz - state.lastVelZ) / elapsed : 0;
        state.rememberVelocity(vx, vy, vz);
        float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
        double forward = -Math.sin(yaw) * vx + Math.cos(yaw) * vz;
        double lateral = Math.cos(yaw) * vx + Math.sin(yaw) * vz;
        double accForward = -Math.sin(yaw) * ax + Math.cos(yaw) * az;
        double accLateral = Math.cos(yaw) * ax + Math.sin(yaw) * az;
        float side = mirror ? -1f : 1f;
        return new float[]{
                (float) (-vy * DRIVE_VERTICAL),
                (float) (forward * DRIVE_FORWARD),
                (float) (lateral * DRIVE_LATERAL) * side,
                -yawDelta * DRIVE_TURN * side,
                (float) (-ay * DRIVE_SNAP),
                (float) (accForward * DRIVE_SNAP),
                (float) (accLateral * DRIVE_SNAP) * side};
    }

    private static void evictStale() {
        long now = Util.getMillis();
        STATES.values().removeIf(state -> now - state.lastTouchMs > EVICT_AFTER_MS);
    }

    private static final class EntityState {
        long lastTouchMs;
        final Map<String, ChainSetState> anchors = new HashMap<>();
    }

    private static final class ChainSetState {
        final Joint[][] chains;
        int[] pitchSlot;
        int[] sideSlot;
        Object slotGeo;
        int lastTick = Integer.MIN_VALUE;
        double lastX, lastY, lastZ;
        float lastYaw;
        double lastVelX, lastVelY, lastVelZ;
        boolean hasVelocity;

        ChainSetState(AttachmentDef def) {
            chains = new Joint[def.physics().size()][];
            for (int c = 0; c < chains.length; c++) {
                chains[c] = new Joint[def.physics().get(c).effectiveBones().size()];
                for (int j = 0; j < chains[c].length; j++) chains[c][j] = new Joint();
            }
        }

        /**
         * Which rotation axes pitch and side swing land on, per chain, from the chain's
         * dominant direction (the second joint's pivot offset in the baked geometry):
         * a backward chain pitches on X and swings on Y; a vertical chain pitches on X
         * but leans on Z — putting side swing on Y would corkscrew it around its own
         * length. Falls back to the declared segment axis, else the backward mapping.
         */
        void resolveSlots(AttachmentDef def, com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo geo) {
            // Re-resolve when the baked geometry changes (a life-stage model swap can
            // introduce chain bones the previous geometry didn't have).
            if (pitchSlot != null && geo == slotGeo) return;
            slotGeo = geo;
            pitchSlot = new int[chains.length];
            sideSlot = new int[chains.length];
            for (int c = 0; c < chains.length; c++) {
                AttachmentDef.PhysicsChain chain = def.physics().get(c);
                char dir = 'z';
                java.util.List<String> names = chain.effectiveBones();
                var second = geo != null && names.size() > 1 ? geo.bone(names.get(1)) : null;
                if (second != null) {
                    float[] offset = second.pivotOffset();
                    float ax = Math.abs(offset[0]), ay = Math.abs(offset[1]), az = Math.abs(offset[2]);
                    dir = ay > ax && ay > az ? 'y' : ax > az ? 'x' : 'z';
                } else {
                    String axis = chain.axis().toLowerCase(java.util.Locale.ROOT);
                    if (axis.equals("x") || axis.equals("y")) dir = axis.charAt(0);
                }
                switch (dir) {
                    case 'y' -> { pitchSlot[c] = 0; sideSlot[c] = 2; }
                    case 'x' -> { pitchSlot[c] = 2; sideSlot[c] = 1; }
                    default -> { pitchSlot[c] = 0; sideSlot[c] = 1; }
                }
            }
        }

        void rememberFrame(LivingEntity entity) {
            lastX = entity.getX();
            lastY = entity.getY();
            lastZ = entity.getZ();
            lastYaw = entity.yBodyRot;
        }

        void rememberVelocity(double vx, double vy, double vz) {
            lastVelX = vx;
            lastVelY = vy;
            lastVelZ = vz;
            hasVelocity = true;
        }

        void forgetVelocity() {
            hasVelocity = false;
        }

        /** One tick of spring integration for every chain, root to tip. */
        void step(AttachmentDef def, float[] drive, int tickTime, int anchorIndex) {
            for (int c = 0; c < chains.length; c++) {
                AttachmentDef.PhysicsChain chain = def.physics().get(c);
                Joint[] joints = chains[c];
                float[] response = chain.response();
                // Snap (acceleration) impulses ride the same channel multipliers as their
                // velocity terms, so a flipped or muted channel stays coherent.
                float snap = chain.snap();
                float driveX = Mth.clamp((drive[0] + snap * drive[4]) * response[0]
                        + (drive[1] + snap * drive[5]) * response[1], -MAX_DRIVE, MAX_DRIVE);
                float driveY = Mth.clamp((drive[2] + snap * drive[6]) * response[2]
                        + drive[3] * response[3], -MAX_DRIVE, MAX_DRIVE);
                // Negative pitch is world-down for a chain extending backward (the tail
                // case, matching the pre-shipped tail_root point).
                float droop = chain.restPitch() - chain.gravity() * chain.droopAngle();
                float damping = Mth.clamp(chain.damping(), 0f, 0.99f);
                float spring = Math.max(0.02f, chain.stiffness()) * SPRING;
                float follow = Mth.clamp(chain.follow(), 0f, 1f);
                // Read parent angles pre-update so swing propagates one tick per joint.
                float[] parentX = new float[joints.length];
                float[] parentY = new float[joints.length];
                for (int j = 0; j < joints.length; j++) {
                    parentX[j] = joints[j].x;
                    parentY[j] = joints[j].y;
                }
                for (int j = 0; j < joints.length; j++) {
                    Joint joint = joints[j];
                    float targetX = j == 0 ? droop : droop + follow * (parentX[j - 1] - droop);
                    float targetY = j == 0 ? 0f : follow * parentY[j - 1];
                    if (chain.sway() > 0f) {
                        float phase = anchorIndex * 1.7f + c * 1.1f + j * 0.8f;
                        targetX += chain.sway() * Mth.sin(tickTime * 0.07f * chain.swaySpeed() + phase);
                        targetY += chain.sway() * Mth.sin(tickTime * 0.05f * chain.swaySpeed() + phase + 1.3f);
                    }
                    // Tips take slightly more of the impulse than the root; that's the whip.
                    float reach = 1f + 0.35f * j;
                    joint.prevX = joint.x;
                    joint.prevY = joint.y;
                    joint.velX = joint.velX * damping + spring * (targetX - joint.x) + driveX * reach;
                    joint.velY = joint.velY * damping + spring * (targetY - joint.y) + driveY * reach;
                    // Kill velocity into the clamp: without this a pinned joint winds up
                    // and snaps violently the moment the drive lets go.
                    float minPitch = Math.min(chain.minPitch(), chain.maxPitch());
                    float maxPitch = Math.max(chain.minPitch(), chain.maxPitch());
                    joint.x += joint.velX;
                    if (joint.x > maxPitch) { joint.x = maxPitch; joint.velX = Math.min(joint.velX, 0f); }
                    else if (joint.x < minPitch) { joint.x = minPitch; joint.velX = Math.max(joint.velX, 0f); }
                    float max = Math.abs(chain.maxAngle());
                    joint.y += joint.velY;
                    if (joint.y > max) { joint.y = max; joint.velY = Math.min(joint.velY, 0f); }
                    else if (joint.y < -max) { joint.y = -max; joint.velY = Math.max(joint.velY, 0f); }
                }
            }
        }
    }

    private static final class Joint {
        float x, y, prevX, prevY;
        float velX, velY;
    }
}
