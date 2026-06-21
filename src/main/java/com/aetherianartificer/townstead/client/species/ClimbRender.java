package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.origin.ClientAbilities;
import com.aetherianartificer.townstead.origin.ability.Ability;
import com.mojang.blaze3d.vertex.PoseStack;
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

    /**
     * The climbed surface's outward normal (the rig's new "up"), or null when not on a surface. Averages the
     * outward normals of every solid face around the entity (the horizontal walls plus a ceiling overhead) so
     * inside corners and blocky/stepped terrain give a stable resultant normal instead of flipping between
     * faces. A single flat wall returns exactly that wall's normal.
     */
    public static Vector3f wallNormal(LivingEntity entity) {
        if (entity.onGround()) return null;
        if (!ClientAbilities.isActive(entity, Ability.CLIMBING)) return null;
        AABB box = entity.getBoundingBox();
        Vector3f sum = new Vector3f();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (solidToward(entity, box, d)) sum.add(-d.getStepX(), 0f, -d.getStepZ());
        }
        if (!entity.level().noCollision(entity, box.move(0.0, 0.12, 0.0))) sum.add(0f, -1f, 0f);
        if (sum.lengthSquared() < 1.0e-4f) return null;
        return sum.normalize();
    }

    private static boolean solidToward(LivingEntity entity, AABB box, Direction d) {
        return !entity.level().noCollision(entity, box.move(d.getStepX() * 0.12, 0.0, d.getStepZ() * 0.12));
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
