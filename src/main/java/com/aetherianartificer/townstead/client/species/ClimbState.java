package com.aetherianartificer.townstead.client.species;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Single source of truth for spider-gravity climbing (see {@code docs/design/spider_gravity.md}).
 *
 * <p>Per rendered entity it holds the eased attach {@code factor} (0 upright → 1 on the surface) and a
 * smoothed surface {@code normal} (which drive the model tilt and the gait). For the local player it also
 * holds the surface-relative look ({@link #surfaceYaw}/{@link #surfacePitch}) the mouse drives (which drives
 * the camera and movement). Ticked once per client tick (the single writer); every other subsystem only
 * reads it.</p>
 */
public final class ClimbState {

    private ClimbState() {}

    // Per-tick ramp step: ~7 ticks (~0.35 s) from upright to fully on the surface.
    private static final float STEP = 0.15f;
    // Gentler ease-OUT (~17 ticks) when the surface probe momentarily finds nothing, so a convex edge (the lip
    // of a ceiling, a gap to the next overhang at the same level) holds the climb alive long enough for the
    // controller to coast across and re-attach, instead of dropping the instant the probe reads null.
    private static final float DECAY = 0.06f;
    // Per-tick low-pass on the surface normal, so crossing block edges on blocky terrain swings the surface
    // rather than snapping it between faces.
    private static final float NORMAL_EASE = 0.25f;
    // Vanilla's per-step turn scale (Entity.turn multiplies the raw delta by this before applying).
    private static final double TURN_SCALE = 0.15;
    // How far the neutral gaze tilts off straight-up-the-wall toward the surface, to sit clear of the
    // look-straight-up singularity (0 = straight up the wall, larger = more downward at the surface).
    private static final float NEUTRAL_DOWN = 0.4f;
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    // Whether to also tip the third-person orbit camera (and its controls) onto the surface like first
    // person. False = stable third-person orbit; first person always reorients regardless.
    public static final boolean REORIENT_THIRD_PERSON = false;
    // In third person, only reorient onto floor/ceiling (a near-vertical surface normal); on walls and
    // intermediate angles keep the normal orbit camera + controls (easier to navigate). |normal.y| above
    // this counts as floor/ceiling (~32 deg of vertical). First person always reorients regardless.
    private static final float THIRD_PERSON_VERTICAL = 0.85f;

    private static final boolean DEBUG = false; // flip true to log the local player's detected surface (latest.log)
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("townstead/climb");

    private static final Map<Integer, Surface> SURFACES = new HashMap<>();

    private static final class Surface {
        float factor;
        Vector3f normal;
    }

    // Local-player surface-relative look, accumulated from the mouse while attached; reset on each attach.
    private static float surfaceYaw;
    private static float surfacePitch;
    private static boolean attached;

    // Pole-free surface camera frame for the local player (the NEUTRAL look, before surfaceYaw/Pitch). Built
    // once on attach from a stable wall reference, then rotated incrementally by the small per-tick change in
    // the surface normal as it eases wall->ceiling. Rebuilding it from world-up every frame (the old way)
    // hit the +Y->(0,-1,0) 180-degree singularity at the ceiling and snapped the camera; transporting it in
    // small steps never reaches the pole. Reset (null) on detach so the next climb re-anchors cleanly.
    private static Quaternionf frame;
    private static Vector3f frameNormal;

    // ---- single writer (once per client tick) ----

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SURFACES.clear();
            ClimbMove.reset();
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            Surface s = SURFACES.get(e.getId());
            // Starting a climb needs intent: the local player must be blocked by a wall it faces, so merely
            // squeezing past walls in a narrow hole/doorway does not latch. Holding (already on) stays on
            // proximity, and other entities (whose horizontalCollision is not simulated for the observer)
            // keep proximity so their climbing models still tilt.
            boolean alreadyOn = s != null && s.factor > 0f;
            // Already attached: probe further (hysteresis) so a wall->ceiling corner or a small sag off the
            // ceiling does not lose the surface for a tick and drop the climb into a fall.
            Vector3f previous = s == null ? null : s.normal;
            Vector3f n = le == mc.player ? ClimbMove.activeNormal() : null;
            if (n == null) n = ClimbRender.wallNormal(le, alreadyOn, previous);
            if (n != null && le == mc.player && !alreadyOn && !ClimbRender.startIntent(le)) {
                n = null;
            }
            if (n != null) {
                if (s == null) {
                    s = new Surface();
                    SURFACES.put(e.getId(), s);
                }
                if (s.normal == null) {
                    s.normal = n;
                } else {
                    s.normal.lerp(n, NORMAL_EASE);
                    if (s.normal.lengthSquared() < 1.0e-4f) s.normal.set(n);
                    else s.normal.normalize();
                }
                s.factor = Math.min(1f, s.factor + STEP);
            } else if (s != null) {
                s.factor = Math.max(0f, s.factor - DECAY);
                if (s.factor <= 0f) SURFACES.remove(e.getId());
            }
        }
        SURFACES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        // Reset the local player's surface look on each fresh attach (works in any camera perspective), and
        // advance the pole-free camera frame by this tick's normal change.
        if (mc.player != null) {
            float f = factor(mc.player.getId());
            updateAttach(f);
            updateFrame(f > 0f ? normal(mc.player.getId()) : null, mc.player.getYRot());
            if (DEBUG && mc.player.tickCount % 3 == 0) {
                Vector3f raw = ClimbRender.wallNormal(mc.player, f > 0f);
                if (f > 0f || raw != null) {
                    Vector3f sm = normal(mc.player.getId());
                    LOG.info("detect f={} raw={} smoothed={} onGround={} reoriented={}",
                            String.format(java.util.Locale.ROOT, "%.2f", f),
                            raw == null ? "null" : fmtVec(raw),
                            sm == null ? "null" : fmtVec(sm), mc.player.onGround(), reorientedView());
                }
            }
        }
        ClimbMove.tickGrace();
    }

    /**
     * Advance the local player's surface frame: anchor it on the first attached tick (stable wall reference),
     * then rotate it by the small rotation taking the previous normal to the current one. Small steps keep it
     * clear of the {@code +Y -> -Y} singularity, so the camera rolls smoothly onto a ceiling instead of
     * snapping when the old from-scratch build crossed vertical.
     */
    private static void updateFrame(Vector3f n, float entityYaw) {
        if (n == null) {
            frame = null;
            frameNormal = null;
            return;
        }
        Vector3f up = new Vector3f(n).normalize();
        if (frame == null || frameNormal == null) {
            frame = anchorFrame(up, entityYaw);
            frameNormal = up;
            return;
        }
        Quaternionf step = new Quaternionf().rotationTo(frameNormal, up);
        frame = step.mul(frame, new Quaternionf());
        frameNormal.set(up);
    }

    /**
     * A surface camera-to-world basis (up = surface normal, neutral forward = up the surface tilted off the
     * straight-up pole) built from world-up. Used only to ANCHOR the transported {@link #frame} on attach; for
     * a wall this is well defined, and a one-off vertical anchor (rare pure-ceiling attach) falls back to the
     * entity facing without a continuity cost (it is a single frame, not a per-tick rebuild).
     */
    private static Quaternionf anchorFrame(Vector3f up, float entityYaw) {
        Vector3f upWall = new Vector3f(0f, 1f, 0f).sub(new Vector3f(up).mul(up.y()));
        if (upWall.lengthSquared() < 1.0e-4f) {
            float y = (float) Math.toRadians(entityYaw);
            upWall.set(-Mth.sin(y), 0f, Mth.cos(y));
            upWall.sub(new Vector3f(up).mul(upWall.dot(up)));
            if (upWall.lengthSquared() < 1.0e-4f) upWall.set(1f, 0f, 0f);
        }
        upWall.normalize();
        Vector3f fwd = new Vector3f(upWall).sub(new Vector3f(up).mul(NEUTRAL_DOWN)).normalize();
        Vector3f camUp = new Vector3f(up).sub(new Vector3f(fwd).mul(up.dot(fwd))).normalize();
        Vector3f right = new Vector3f(fwd).cross(camUp).normalize();
        return new Matrix3f().set(
                right.x(), right.y(), right.z(),
                camUp.x(), camUp.y(), camUp.z(),
                -fwd.x(), -fwd.y(), -fwd.z()
        ).getNormalizedRotation(new Quaternionf());
    }

    /**
     * Whether the local player's view is reoriented onto the surface. First person: always. Third person:
     * only when enabled AND the surface is floor/ceiling (near-vertical normal); walls and intermediate
     * angles keep the normal orbit (camera + controls), which all readers respect uniformly.
     */
    public static boolean reorientedView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson()) return true;
        if (!REORIENT_THIRD_PERSON || mc.player == null) return false;
        Vector3f n = normal(mc.player.getId());
        return n != null && Math.abs(n.y()) >= THIRD_PERSON_VERTICAL;
    }

    private static void updateAttach(float f) {
        if (f > 0f && !attached) {
            attached = true;
            surfaceYaw = 0f;
            surfacePitch = 0f;
        } else if (f <= 0f && attached) {
            attached = false;
        }
    }

    // ---- per-entity reads (model + gait) ----

    /** Eased reorientation amount for an entity, 0 (upright) to 1 (fully on the surface). */
    public static float factor(int entityId) {
        Surface s = SURFACES.get(entityId);
        return s == null ? 0f : s.factor;
    }

    /** The smoothed surface normal seen while climbing, retained through the ease-out, or null. */
    public static Vector3f normal(int entityId) {
        Surface s = SURFACES.get(entityId);
        return s == null ? null : s.normal;
    }

    // ---- local-player input ----

    /**
     * Applies the mouse delta to the local player's surface look. Returns true when it handled the turn (the
     * caller cancels vanilla's), false to let vanilla run. First person only; in third person vanilla turns
     * the body (and the orbit camera) so it isn't frozen.
     */
    public static boolean tryTurn(LivingEntity self, double yawDelta, double pitchDelta) {
        Minecraft mc = Minecraft.getInstance();
        int id = self.getId();
        if (self != mc.player || factor(id) <= 0f || normal(id) == null) return false;
        if (!reorientedView()) return false;
        surfaceYaw -= (float) (yawDelta * TURN_SCALE);
        surfacePitch -= (float) (pitchDelta * TURN_SCALE);
        surfacePitch = Math.max(-89f, Math.min(89f, surfacePitch));
        return true;
    }

    // ---- local-player derived look (camera + movement) ----

    /**
     * The camera-to-world orientation for the surface-frame view (view forward -Z, matching MC's view matrix
     * {@code Rz(roll) Rx(pitch) Ry(yaw+180)}). Up is the surface normal; the neutral forward comes from the
     * pole-free transported {@link #frame}, then is turned about the normal and pitched about screen-right by
     * the surface look. Falls back to a from-scratch anchor if the frame has not been built yet (before the
     * first attached tick).
     */
    public static Quaternionf cameraOrientation(Vector3f n, float entityYaw) {
        Quaternionf base = frame != null ? new Quaternionf(frame) : anchorFrame(new Vector3f(n).normalize(), entityYaw);
        Vector3f up = frameNormal != null ? new Vector3f(frameNormal) : new Vector3f(n).normalize();
        return new Quaternionf().rotationAxis((float) Math.toRadians(surfaceYaw), up.x(), up.y(), up.z())
                .mul(base).rotateX((float) Math.toRadians(surfacePitch));
    }

    /** The world direction the surface-frame camera looks, used to steer look-relative movement. */
    public static Vector3f lookForward(Vector3f n, float entityYaw) {
        return cameraOrientation(n, entityYaw).transform(new Vector3f(0f, 0f, -1f));
    }

    private static String fmtVec(Vector3f v) {
        return String.format(java.util.Locale.ROOT, "(%.2f,%.2f,%.2f)", v.x(), v.y(), v.z());
    }
}
