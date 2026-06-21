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
    // Per-tick low-pass on the surface normal, so crossing block edges on blocky terrain swings the surface
    // rather than snapping it between faces.
    private static final float NORMAL_EASE = 0.25f;
    // Vanilla's per-step turn scale (Entity.turn multiplies the raw delta by this before applying).
    private static final double TURN_SCALE = 0.15;
    // How far the neutral gaze tilts off straight-up-the-wall toward the surface, to sit clear of the
    // look-straight-up singularity (0 = straight up the wall, larger = more downward at the surface).
    private static final float NEUTRAL_DOWN = 0.4f;
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    // Flip to true to also tip the third-person orbit camera (and its controls) onto the surface like first
    // person. Default false = stable third-person orbit; first person always reorients.
    public static final boolean REORIENT_THIRD_PERSON = false;

    private static final Map<Integer, Surface> SURFACES = new HashMap<>();

    private static final class Surface {
        float factor;
        Vector3f normal;
    }

    // Local-player surface-relative look, accumulated from the mouse while attached; reset on each attach.
    private static float surfaceYaw;
    private static float surfacePitch;
    private static boolean attached;

    // ---- single writer (once per client tick) ----

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SURFACES.clear();
            return;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            Vector3f n = ClimbRender.wallNormal(le);
            Surface s = SURFACES.get(e.getId());
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
                s.factor = Math.max(0f, s.factor - STEP);
                if (s.factor <= 0f) SURFACES.remove(e.getId());
            }
        }
        SURFACES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        // Reset the local player's surface look on each fresh attach (works in any camera perspective).
        if (mc.player != null) updateAttach(factor(mc.player.getId()));
        ClimbMove.tickGrace();
    }

    /** Whether the view is reoriented onto the surface (always first person; third person only if enabled). */
    public static boolean reorientedView() {
        return REORIENT_THIRD_PERSON || Minecraft.getInstance().options.getCameraType().isFirstPerson();
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
     * {@code Rz(roll) Rx(pitch) Ry(yaw+180)}). Up is the surface normal; forward starts up the surface tilted
     * toward it (off the straight-up pole) and is turned about the normal and pitched about screen-right by
     * the surface look. For a vertical normal (ceiling) the up-the-surface tangent is degenerate, so the
     * entity facing is used as the forward reference.
     */
    public static Quaternionf cameraOrientation(Vector3f n, float entityYaw) {
        Vector3f up = new Vector3f(n).normalize();
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
        Quaternionf base = new Matrix3f().set(
                right.x(), right.y(), right.z(),
                camUp.x(), camUp.y(), camUp.z(),
                -fwd.x(), -fwd.y(), -fwd.z()
        ).getNormalizedRotation(new Quaternionf());
        return new Quaternionf().rotationAxis((float) Math.toRadians(surfaceYaw), up.x(), up.y(), up.z())
                .mul(base).rotateX((float) Math.toRadians(surfacePitch));
    }

    /** The world direction the surface-frame camera looks, used to steer look-relative movement. */
    public static Vector3f lookForward(Vector3f n, float entityYaw) {
        return cameraOrientation(n, entityYaw).transform(new Vector3f(0f, 0f, -1f));
    }
}
