package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.root.rig.RigHitboxes;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The space a person's attachments take up in the world. Parts that can't fold add to what stops the
 * bearer moving, without widening the square hitbox: a body can walk past a wall that its abdomen
 * only reaches when it backs up. Parts that can fold just report when they don't fit.
 *
 * <p>Boxes come from authored data and the bearer's rest pose, never the physics sim, so both sides
 * agree. Only moving is stopped. Turning isn't, so a part can end up inside a wall, and from there
 * it can only leave (see {@link VolumePlacement#wedgedMoveAllowed}).</p>
 */
public final class BodyVolumes {

    /** One box around its bearer, relative to the feet, and whose definition it came from. */
    public record Placed(AttachmentDef def, AABB box) {}

    private record Cached(int tick, float yaw, List<Placed> blocking, List<Placed> folding, List<Placed> all) {}

    private static final Cached EMPTY = new Cached(0, 0f, List.of(), List.of(), List.of());
    // A wedged body still moves, but it's a squeeze.
    private static final double WEDGED_SPEED = 0.25;
    // The server gives way this far on a player's boxes. The client decided the move, and the two sides
    // may differ slightly on the body's turn; vanilla pulls a player back past a quarter block.
    private static final double SERVER_LENIENCE = 0.12;

    private static final Map<LivingEntity, Cached> CACHE = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private BodyVolumes() {}

    /** The entity's move after its volumes have had their say. Called for every moving entity. */
    public static Vec3 limit(Entity entity, Vec3 move) {
        if (!(entity instanceof Player) && !(entity instanceof VillagerEntityMCA)) return move;
        if (move.lengthSqr() == 0.0) return move;
        LivingEntity living = (LivingEntity) entity;
        if (!solid(living)) return move;
        List<Placed> boxes = placed(living).blocking();
        if (boxes.isEmpty()) return move;
        boolean lenient = !living.level().isClientSide && living instanceof Player;
        Vec3 result = move;
        for (Placed placed : boxes) {
            AABB box = placed.box().move(living.position());
            if (lenient) box = box.deflate(SERVER_LENIENCE);
            result = throughBox(living, box, result);
        }
        return result;
    }

    private static Vec3 throughBox(LivingEntity entity, AABB box, Vec3 move) {
        Level level = entity.level();
        double overlap = overlap(entity, box);
        if (overlap <= 0.0) return Entity.collideBoundingBox(entity, move, box, level, List.of());
        double x = VolumePlacement.wedgedMoveAllowed(overlap, overlap(entity, box.move(move.x, 0.0, 0.0))) ? move.x * WEDGED_SPEED : 0.0;
        double z = VolumePlacement.wedgedMoveAllowed(overlap, overlap(entity, box.move(0.0, 0.0, move.z))) ? move.z * WEDGED_SPEED : 0.0;
        return new Vec3(x, move.y, z);
    }

    private static double overlap(LivingEntity entity, AABB box) {
        double total = 0.0;
        for (VoxelShape shape : entity.level().getBlockCollisions(entity, box)) {
            for (AABB solid : shape.toAabbs()) {
                double dx = Math.min(box.maxX, solid.maxX) - Math.max(box.minX, solid.minX);
                double dy = Math.min(box.maxY, solid.maxY) - Math.max(box.minY, solid.minY);
                double dz = Math.min(box.maxZ, solid.maxZ) - Math.max(box.minZ, solid.minZ);
                if (dx > 1e-4 && dy > 1e-4 && dz > 1e-4) total += dx * dy * dz;
            }
        }
        return total;
    }

    /** Whether a part that folds doesn't fit here: it stays folded, and the bearer can't fly. */
    public static boolean folded(LivingEntity entity) {
        if (!(entity instanceof Player) && !(entity instanceof VillagerEntityMCA)) return false;
        List<Placed> boxes = placed(entity).folding();
        if (boxes.isEmpty()) return false;
        for (Placed placed : boxes) {
            if (!entity.level().noCollision(entity, placed.box().move(entity.position()))) return true;
        }
        return false;
    }

    /** Every box this entity's attachments have, for whoever needs to know where a part is. */
    public static List<Placed> all(LivingEntity entity) {
        return placed(entity).all();
    }

    /** This entity's movement boxes as they'd sit with its body turned to {@code yaw}. */
    public static List<AABB> blockingAt(LivingEntity entity, float yaw) {
        List<AABB> out = new ArrayList<>();
        for (Placed placed : build(entity, yaw).blocking()) out.add(placed.box());
        return out;
    }

    /** Boxes that take part in movement, for planners deciding where the body fits. */
    public static List<Placed> blocking(LivingEntity entity) {
        return placed(entity).blocking();
    }

    private static boolean solid(LivingEntity entity) {
        if (entity.noPhysics || entity.isPassenger() || entity.isSleeping() || entity.isFallFlying()) return false;
        if (entity.isSpectator()) return false;
        Pose pose = entity.getPose();
        return pose != Pose.SWIMMING && pose != Pose.SPIN_ATTACK;
    }

    private static Cached placed(LivingEntity entity) {
        Cached cached = CACHE.get(entity);
        float yaw = entity.yBodyRot;
        if (cached != null && cached.tick() == entity.tickCount && cached.yaw() == yaw) return cached;
        Cached fresh = build(entity, yaw);
        CACHE.put(entity, fresh);
        return fresh;
    }

    private static Cached build(LivingEntity entity, float yaw) {
        List<Placed> blocking = null, folding = null, all = null;
        for (AttachmentDef def : WornAttachments.of(entity)) {
            AttachmentBody.Volume volume = def.body().volume();
            if (volume == null) continue;
            float[] grow = growOf(entity, def, volume);
            float width = 1f, height = 1f;
            RigHitboxes.Inputs inputs = RigHitboxes.inputs(entity);
            if (inputs != null) {
                float shared = inputs.growth() * inputs.rigScale() * inputs.entityScale();
                width = inputs.widthScale() * shared;
                height = inputs.heightScale() * shared;
            }
            for (float[] raw : volume.boxes()) {
                double[] b = VolumePlacement.place(raw, volume.pivot(), grow, width, height, yaw);
                Placed placed = new Placed(def, new AABB(b[0], b[1], b[2], b[3], b[4], b[5]));
                if (all == null) all = new ArrayList<>();
                all.add(placed);
                if (AttachmentBody.Volume.BLOCK.equals(volume.mode())) {
                    if (blocking == null) blocking = new ArrayList<>();
                    blocking.add(placed);
                } else if (AttachmentBody.Volume.FOLD.equals(volume.mode())) {
                    if (folding == null) folding = new ArrayList<>();
                    folding.add(placed);
                }
            }
        }
        if (all == null) return new Cached(entity.tickCount, yaw, EMPTY.blocking(), EMPTY.folding(), EMPTY.all());
        return new Cached(entity.tickCount, yaw, blocking == null ? List.of() : blocking,
                folding == null ? List.of() : folding, all);
    }

    private static float[] growOf(LivingEntity entity, AttachmentDef def, AttachmentBody.Volume volume) {
        if (volume.grow().isEmpty()) return new float[]{1f, 1f, 1f};
        Float value = BodyValues.channel(entity, def, volume.grow());
        if (value == null) return new float[]{1f, 1f, 1f};
        return new float[]{VolumePlacement.growFactor(value, volume.axes()[0]),
                VolumePlacement.growFactor(value, volume.axes()[1]),
                VolumePlacement.growFactor(value, volume.axes()[2])};
    }
}
