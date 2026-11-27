package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.root.rig.RigHitboxes;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where each soft part a body wears is this tick, squashed into the room around it (see {@link SoftEnvelope}).
 * Both sides work it out the same way from the same synced values and blocks, so what stops a body moving,
 * what a blow lands on, and what's drawn agree. Worked out at most once a tick per body and turn.
 */
public final class SoftBodies {

    // How much room around a part's full size is searched for blocks, in blocks.
    private static final double SEARCH = 0.5;
    private static final int MAX_SOLIDS = 256;

    /**
     * One soft part this tick: its lobes at full size, as squashed, and at their least, from the feet in
     * world axes, six numbers a lobe in the order of its hit groups; a null lobe is a piece not showing.
     */
    public record Shape(AttachmentDef def, double[] full, double[] lobes, double[] squeezed, boolean[] showing,
                        double pushX, double pushZ, boolean stuck) {
        /** Lobe {@code i} as squashed, or null when it isn't showing. */
        @Nullable
        public AABB lobe(int i) {
            return i < showing.length && showing[i] ? box(lobes, i) : null;
        }

        @Nullable
        public AABB least(int i) {
            return i < showing.length && showing[i] ? box(squeezed, i) : null;
        }

        private static AABB box(double[] boxes, int i) {
            int b = i * 6;
            return new AABB(boxes[b], boxes[b + 1], boxes[b + 2], boxes[b + 3], boxes[b + 4], boxes[b + 5]);
        }
    }

    private record Cached(long tick, float yaw, double x, double y, double z, List<Shape> shapes) {}

    private static final Map<LivingEntity, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private SoftBodies() {}

    public static boolean any(LivingEntity entity) {
        if (!(entity instanceof Player) && !(entity instanceof VillagerEntityMCA)) return false;
        for (AttachmentDef def : WornAttachments.of(entity)) {
            if (def.body().soft() != null) return true;
        }
        return false;
    }

    /** Every soft part this body wears, as it is now. */
    public static List<Shape> of(LivingEntity entity) {
        if (!(entity instanceof Player) && !(entity instanceof VillagerEntityMCA)) return List.of();
        long tick = entity.level().getGameTime();
        float yaw = entity.yBodyRot;
        Cached cached = CACHE.get(entity);
        if (cached != null && cached.tick() == tick && cached.yaw() == yaw && cached.x() == entity.getX()
                && cached.y() == entity.getY() && cached.z() == entity.getZ()) {
            return cached.shapes();
        }
        List<Shape> shapes = null;
        for (AttachmentDef def : WornAttachments.of(entity)) {
            if (def.body().soft() == null) continue;
            Shape shape = solve(entity, def);
            if (shape == null) continue;
            if (shapes == null) shapes = new ArrayList<>();
            shapes.add(shape);
        }
        List<Shape> result = shapes == null ? List.of() : shapes;
        CACHE.put(entity, new Cached(tick, yaw, entity.getX(), entity.getY(), entity.getZ(), result));
        return result;
    }

    /** This one soft part as it is now, or null when it isn't worn or showing. */
    @Nullable
    public static Shape of(LivingEntity entity, AttachmentDef def) {
        for (Shape shape : of(entity)) {
            if (shape.def() == def || shape.def().id().equals(def.id())) return shape;
        }
        return null;
    }

    /** How far, in blocks this tick, the body's soft parts push it: horizontal only, capped by each part. */
    public static Vec3 push(LivingEntity entity) {
        double x = 0.0, z = 0.0;
        for (Shape shape : of(entity)) {
            AttachmentBody.Soft soft = shape.def().body().soft();
            double px = shape.pushX() * soft.push(), pz = shape.pushZ() * soft.push();
            double length = Math.sqrt(px * px + pz * pz);
            if (length > soft.pushMax() && length > 0.0) {
                px *= soft.pushMax() / length;
                pz *= soft.pushMax() / length;
            }
            x += px;
            z += pz;
        }
        return new Vec3(x, 0.0, z);
    }

    /** Pushes a villager off what its soft parts press on. Server side, once a tick. */
    public static void tick(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA) || !entity.onGround() || entity.isPassenger() || entity.isSleeping()) return;
        Vec3 push = push(entity);
        if (push.lengthSqr() < 1e-8) return;
        entity.setDeltaMovement(entity.getDeltaMovement().add(push));
    }

    @Nullable
    private static Shape solve(LivingEntity entity, AttachmentDef def) {
        AttachmentBody.Soft soft = def.body().soft();
        double[] full;
        double[] roots;
        boolean[] showing;
        if (!soft.lobes().isEmpty()) {
            float[] grow = growOf(entity, def, soft);
            float width = 1f, height = 1f;
            RigHitboxes.Inputs inputs = RigHitboxes.inputs(entity);
            if (inputs != null) {
                float shared = inputs.growth() * inputs.rigScale() * inputs.entityScale();
                width = inputs.widthScale() * shared;
                height = inputs.heightScale() * shared;
            }
            int n = soft.lobes().size();
            full = new double[n * 6];
            roots = new double[n * 3];
            showing = new boolean[n];
            double[] pivot = VolumePlacement.place(new float[]{soft.pivot()[0], soft.pivot()[1], soft.pivot()[2],
                    soft.pivot()[0], soft.pivot()[1], soft.pivot()[2]}, soft.pivot(), grow, width, height, entity.yBodyRot);
            for (int i = 0; i < n; i++) {
                double[] b = VolumePlacement.place(soft.lobes().get(i), soft.pivot(), grow, width, height, entity.yBodyRot);
                System.arraycopy(b, 0, full, i * 6, 6);
                for (int a = 0; a < 3; a++) roots[i * 3 + a] = pivot[a];
                showing[i] = true;
            }
        } else {
            float[] measured = BodyPose.measure(entity, def);
            int n = measured.length / 6;
            if (n == 0) return null;
            full = new double[n * 6];
            roots = new double[n * 3];
            showing = new boolean[n];
            for (int i = 0; i < n; i++) {
                int b = i * 6;
                showing[i] = measured[b] <= measured[b + 3];
                for (int k = 0; k < 6; k++) full[b + k] = showing[i] ? measured[b + k] : 0.0;
                // Each lobe grows from its side nearest the body's own axis, at its own height: where a part
                // that hangs off a body meets it. The envelope moves the point onto the lobe.
                roots[i * 3] = 0.0;
                roots[i * 3 + 1] = (full[b + 1] + full[b + 4]) * 0.5;
                roots[i * 3 + 2] = 0.0;
            }
        }
        double[] solids = new double[MAX_SOLIDS * 6];
        int count = gather(entity, full, showing, solids);
        double[] live = full;
        if (!allShowing(showing)) {
            // A piece that isn't showing squashes nothing: give it no size at its root.
            live = full.clone();
            for (int i = 0; i < showing.length; i++) {
                if (showing[i]) continue;
                for (int a = 0; a < 3; a++) {
                    live[i * 6 + a] = live[i * 6 + 3 + a] = roots[i * 3 + a];
                }
            }
        }
        SoftEnvelope.Result result = SoftEnvelope.squash(live, roots, soft.min(), soft.keepVolume(), solids, count);
        return new Shape(def, full, result.lobes(), result.squeezed(), showing, result.pushX(), result.pushZ(), result.stuck());
    }

    private static boolean allShowing(boolean[] showing) {
        for (boolean b : showing) if (!b) return false;
        return true;
    }

    private static int gather(LivingEntity entity, double[] lobes, boolean[] showing, double[] into) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < showing.length; i++) {
            if (!showing[i]) continue;
            int b = i * 6;
            minX = Math.min(minX, lobes[b]);
            minY = Math.min(minY, lobes[b + 1]);
            minZ = Math.min(minZ, lobes[b + 2]);
            maxX = Math.max(maxX, lobes[b + 3]);
            maxY = Math.max(maxY, lobes[b + 4]);
            maxZ = Math.max(maxZ, lobes[b + 5]);
        }
        if (minX > maxX) return 0;
        double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        AABB around = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(SEARCH).move(ex, ey, ez);
        int count = 0;
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, around)) {
            for (AABB box : shape.toAabbs()) {
                if (count == MAX_SOLIDS) return count;
                int b = count++ * 6;
                into[b] = box.minX - ex;
                into[b + 1] = box.minY - ey;
                into[b + 2] = box.minZ - ez;
                into[b + 3] = box.maxX - ex;
                into[b + 4] = box.maxY - ey;
                into[b + 5] = box.maxZ - ez;
            }
        }
        return count;
    }

    private static float[] growOf(LivingEntity entity, AttachmentDef def, AttachmentBody.Soft soft) {
        if (soft.grow().isEmpty()) return new float[]{1f, 1f, 1f};
        Float value = BodyValues.channel(entity, def, soft.grow());
        if (value == null) return new float[]{1f, 1f, 1f};
        return new float[]{VolumePlacement.growFactor(value, soft.axes()[0]),
                VolumePlacement.growFactor(value, soft.axes()[1]),
                VolumePlacement.growFactor(value, soft.axes()[2])};
    }
}
