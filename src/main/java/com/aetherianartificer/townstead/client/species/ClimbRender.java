package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.origin.ClientAbilities;
import com.aetherianartificer.townstead.origin.ability.Ability;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spider gravity, stage 1 (model): rotates a climbing rig so the wall it is on becomes the floor, applied
 * to the WHOLE entity render (body + worn armor + held item together) so nothing comes apart. The hook is
 * {@code RenderLivingEvent.Pre}/{@code Post}: {@code Pre} fires before the entity's model, in the raw
 * world-aligned pose (before the renderer applies the body yaw), so the surface normal can be used
 * directly in world space; {@code Post} fires after the model but before the name tag, so the tag stays
 * upright. A push in {@code Pre} balanced by a pop in {@code Post} brackets exactly the model and its
 * layers. {@code Pre}/{@code Post} both fire for players (via {@code PlayerRenderer.super.render}).
 *
 * <p>Stage-1 scope: vertical walls only (the surface normal points out of the wall), no in-plane spin
 * correction yet; eased in/out by {@link ClimbState}. The camera/input stages reuse {@link #wallNormal}.</p>
 */
public final class ClimbRender {

    private ClimbRender() {}

    // True between a Pre push and its Post pop, so the pop only happens when the push did.
    private static final ThreadLocal<Boolean> PUSHED = ThreadLocal.withInitial(() -> false);

    // Hold the model this far proud of the surface instead of seating its origin exactly on the face, so a
    // model whose geometry sits around its origin (e.g. the spider body) does not clip into the surface.
    private static final float SURFACE_CLEARANCE = 0.05f;

    // How far out to probe for a surface. Starting a climb uses the tight reach (you must be right against
    // the wall). Once attached we probe much further (hysteresis), so rounding a wall->ceiling corner or
    // sagging a little off a ceiling does not momentarily lose the surface and drop you into a fall.
    private static final float REACH = 0.12f;
    private static final float STICKY_REACH = 0.34f;
    // How much more a ceiling overhead counts than a wall in the surface normal, so a wall->ceiling corner
    // commits to the ceiling instead of staying stuck on the wall the body is pressed against.
    private static final float CEILING_BIAS = 3.0f;
    private static final float CONTINUITY_BIAS = 1.25f;

    /**
     * The intent to START a climb: the entity is blocked horizontally and there is a solid block in the
     * direction it faces, so it is pushing into a wall rather than just brushing past walls in a narrow gap.
     */
    public static boolean startIntent(LivingEntity entity) {
        if (!entity.horizontalCollision) return false;
        return solidToward(entity, entity.getBoundingBox(), entity.getDirection());
    }

    /** The horizontal direction toward the wall a climbing entity is on (the one it faces first), or null. */
    public static Direction wallDir(LivingEntity entity) {
        if (entity.onGround()) return null;
        if (!ClientAbilities.isActive(entity, Ability.CLIMBING)) return null;
        AABB box = entity.getBoundingBox();
        Direction facing = entity.getDirection();
        if (solidToward(entity, box, facing)) return facing;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d != facing && solidToward(entity, box, d)) return d;
        }
        return null;
    }

    /** The climbed surface's outward normal for a fresh (not-yet-attached) entity. */
    public static Vector3f wallNormal(LivingEntity entity) {
        return wallNormal(entity, false);
    }

    /**
     * The climbed surface's outward normal (the rig's new "up"), or null when not on a surface. Sums the
     * outward normals of every solid face around the entity (the horizontal walls plus a ceiling overhead),
     * each weighted by PROXIMITY (the face the entity is pressed against counts most). A single flat wall
     * returns exactly that wall's normal; a true inside corner returns the diagonal; but at a wall->ceiling
     * transition the ceiling wins as soon as the entity slides under it, so the climb commits to the ceiling
     * instead of hanging forever at the 45-degree corner average (the old equal-weight sum could not let go
     * of the wall). When {@code attached}, probes further so the transition (or a small sag off a ceiling)
     * keeps the surface instead of briefly losing it and dropping into a fall.
     */
    public static Vector3f wallNormal(LivingEntity entity, boolean attached) {
        return wallNormal(entity, attached, null);
    }

    public static Vector3f wallNormal(LivingEntity entity, boolean attached, Vector3f previousNormal) {
        if (entity.onGround()) return null;
        if (!ClientAbilities.isActive(entity, Ability.CLIMBING)) return null;
        // The local player lets go (drops to the ground) while sneaking: force-detach so it falls clean
        // instead of crouch-sliding down the wall.
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && mc.player.isShiftKeyDown()) return null;
        float reach = attached ? STICKY_REACH : REACH;
        AABB box = entity.getBoundingBox();
        Vector3f best = null;
        float bestScore = 0f;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            float w = faceWeight(entity, box, d.getStepX(), 0, d.getStepZ(), reach);
            if (w > 0f) {
                Vector3f normal = new Vector3f(-d.getStepX(), 0f, -d.getStepZ());
                float score = surfaceScore(w, normal, previousNormal, 1.0f);
                if (score > bestScore) {
                    bestScore = score;
                    best = normal;
                }
            }
        }
        // A ceiling overhead outweighs the walls (CEILING_BIAS): climbing a wall up to a ceiling, the body is
        // pressed flat against the wall, so by raw proximity the wall would win and the climb would just bounce
        // against the ceiling tile instead of flipping onto it. Biasing the ceiling lets the normal tilt onto
        // it as soon as it is overhead, so the corner-assist engages and the body rounds onto the ceiling.
        float ceiling = faceWeight(entity, box, 0, 1, 0, reach);
        if (ceiling > 0f) {
            Vector3f normal = new Vector3f(0f, -1f, 0f);
            float score = surfaceScore(ceiling, normal, previousNormal, CEILING_BIAS);
            if (score > bestScore) {
                bestScore = score;
                best = normal;
            }
        }
        return best;
    }

    private static float surfaceScore(float proximity, Vector3f normal, Vector3f previousNormal, float bias) {
        float score = proximity * bias;
        if (previousNormal != null && previousNormal.lengthSquared() > 1.0e-4f) {
            float alignment = Math.max(0f, new Vector3f(previousNormal).normalize().dot(normal));
            score += alignment * CONTINUITY_BIAS;
        }
        return score;
    }

    /**
     * Proximity weight for the face in direction {@code (dx,dy,dz)}: the nearest contact gets the most weight,
     * so the surface the entity is pressed against outweighs one merely within reach. Stepped probe, near to
     * far; 0 when nothing solid is within {@code maxReach}.
     */
    private static float faceWeight(LivingEntity entity, AABB box, int dx, int dy, int dz, float maxReach) {
        final int steps = 4;
        for (int i = 1; i <= steps; i++) {
            float r = maxReach * i / steps;
            if (!entity.level().noCollision(entity, box.move(dx * r, dy * r, dz * r))) return steps - i + 1;
        }
        return 0f;
    }

    private static boolean solidToward(LivingEntity entity, AABB box, Direction d) {
        return !entity.level().noCollision(entity, box.move(d.getStepX() * REACH, 0.0, d.getStepZ() * REACH));
    }

    /**
     * The rotation that turns a surface with the given outward {@code normal} into the floor (maps the
     * model's local up, +Y, onto the normal). For a wall (non-vertical normal) this is the minimal rotation
     * {@code rotationTo} the wall path has always used. For a ceiling (normal exactly opposite +Y),
     * {@code rotationTo} is degenerate, so a stable basis is built using the entity's facing ({@code yawDeg})
     * as the forward reference instead.
     */
    public static Quaternionf surfaceToWorld(Vector3f normal, float yawDeg) {
        Vector3f up = new Vector3f(normal).normalize();
        if (Math.abs(up.y()) < 0.999f) {
            return new Quaternionf().rotationTo(0f, 1f, 0f, up.x(), up.y(), up.z());
        }
        // Vertical normal: pick a forward from the entity's facing, projected onto the surface plane.
        float yaw = (float) Math.toRadians(yawDeg);
        Vector3f forward = new Vector3f(-Mth.sin(yaw), 0f, Mth.cos(yaw));
        forward.sub(new Vector3f(up).mul(forward.dot(up)));
        if (forward.lengthSquared() < 1.0e-4f) forward.set(1f, 0f, 0f);
        forward.normalize();
        Vector3f right = new Vector3f(forward).cross(up).normalize();
        // Columns are the images of local +X, +Y, +Z (local forward is -Z, so +Z maps to -forward).
        return new Matrix3f().set(
                right.x(), right.y(), right.z(),
                up.x(), up.y(), up.z(),
                -forward.x(), -forward.y(), -forward.z()
        ).getNormalizedRotation(new Quaternionf());
    }

    //? if neoforge {
    public static void onRenderLivingPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    //?} else {
    /*public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    *///?}
        LivingEntity entity = event.getEntity();
        int id = entity.getId();
        float f = ClimbState.factor(id);
        if (f <= 0f) return;
        Vector3f up = ClimbState.normal(id);
        if (up == null) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.set(true);
        // Seat the model's feet (its origin, the bottom of the model) onto the surface, then rotate about
        // that point so "down" points into the surface and the body stands out from it, exactly as a model
        // stands on a floor. The seat depth is the origin's distance to the contact face along the normal:
        // for a wall the origin is half a width from the side face; for a ceiling it is a full height below
        // the top face (the origin is the bottom of the box). World frame here (yaw not yet applied), so the
        // normal is used directly. Eased so the swing comes in smoothly.
        // Seat depth = origin-to-surface distance along the normal: half the width off a side wall, the full
        // height off a ceiling (origin is the box bottom). Blend smoothly by how ceiling-ward the normal is,
        // so the wall->ceiling transition does not lurch the model into the block.
        float ceiling = Math.max(0f, -up.y());
        float depth = Mth.lerp(ceiling, entity.getBbWidth() * 0.5f, entity.getBbHeight());
        float seat = Math.max(0f, depth - SURFACE_CLEARANCE) * f;
        pose.translate(-up.x() * seat, -up.y() * seat, -up.z() * seat);
        pose.mulPose(new Quaternionf().slerp(surfaceToWorld(up, entity.getYRot()), f));
        // Spin the body about the surface normal to face where the local player looks along the surface, so
        // turning on a ceiling actually turns the model (it would otherwise keep its frozen compass body-yaw
        // and walk backwards). Replaces the renderer's body-yaw within the reoriented frame at f=1, eased in.
        float spin = headingSpinDegrees(entity, up);
        if (spin != 0f) pose.mulPose(Axis.YP.rotationDegrees(spin * f));
        if (DEBUG && entity == Minecraft.getInstance().player && entity.tickCount % 3 == 0) {
            Vector3f modelUp = surfaceToWorld(up, entity.getYRot()).transform(new Vector3f(0f, 1f, 0f));
            LOG.info("tilt up=({},{},{}) f={} spin={} modelUp=({},{},{}) pose={} bbH={}",
                    fmt(up.x()), fmt(up.y()), fmt(up.z()), fmt(f), fmt(spin),
                    fmt(modelUp.x()), fmt(modelUp.y()), fmt(modelUp.z()), entity.getPose(), fmt(entity.getBbHeight()));
        }
    }

    private static final boolean DEBUG = false; // flip true to log the model tilt on the local player (latest.log)
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("townstead/climb");

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /**
     * Degrees to spin the model about the surface normal (local +Y after {@link #surfaceToWorld}) so the body
     * faces the local player's surface-look direction. Cancels the renderer's body-yaw and substitutes the
     * surface heading; the renderer applies {@code YP(180 - yBodyRot)} after this, so {@code beta - 180 +
     * yBodyRot} nets out to {@code beta}, the heading from the surfaceToWorld forward to the look. Only the
     * local reoriented player (whose surface look we hold); 0 for everyone else. The clung player's yaw is
     * frozen so {@code yBodyRot == yBodyRotO}, no partial-tick interpolation needed.
     */
    private static float headingSpinDegrees(LivingEntity entity, Vector3f up) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || !ClimbState.reorientedView()) return 0f;
        Vector3f look = inPlane(ClimbState.lookForward(up, entity.getYRot()), up);
        Vector3f base = inPlane(surfaceToWorld(up, entity.getYRot()).transform(new Vector3f(0f, 0f, -1f)), up);
        if (look == null || base == null) return 0f;
        float sin = new Vector3f(base).cross(look).dot(up);
        float beta = (float) Math.toDegrees(Math.atan2(sin, base.dot(look)));
        return beta - 180f + entity.yBodyRot;
    }

    /** Projects a vector onto the surface plane (removes its normal component) and normalizes it, or null. */
    private static Vector3f inPlane(Vector3f v, Vector3f up) {
        v.sub(new Vector3f(up).mul(v.dot(up)));
        if (v.lengthSquared() < 1.0e-4f) return null;
        return v.normalize();
    }

    //? if neoforge {
    public static void onRenderLivingPost(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> event) {
    //?} else {
    /*public static void onRenderLivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
    *///?}
        if (PUSHED.get()) {
            event.getPoseStack().popPose();
            PUSHED.set(false);
        }
    }
}
