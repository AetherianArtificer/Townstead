package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.ability.MovementAbilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The float shared by drifting villagers and players: hold a set height over whatever is
 * below (the surface, or a fluid's top), bob a little, rise onto a step ahead, settle low
 * enough to pass under a door frame, and sink gently where the ground falls away. A wall
 * is not a step: the body only rises where it would fit on top.
 */
public final class DriftPhysics {

    private DriftPhysics() {}

    public static final double BOB = 0.06;
    public static final double MAX_RISE = 0.18;
    public static final double MAX_SINK = 0.12;
    public static final double OPEN_SINK = 0.08;
    private static final int GROUND_SCAN = 8;
    private static final double RESPONSE = 0.3;
    /** Tallest rise that counts as a step rather than a wall. */
    private static final double MAX_STEP = 1.1;
    /** How far past the body's edge to look for a step or a low ceiling. */
    private static final double LOOK_AHEAD = 0.8;
    private static final double SQUEEZE_STEP = 0.1;
    /** Sink speed when a low ceiling ahead squeezes the float, so the body is low before the frame. */
    private static final double SQUEEZE_SINK = 0.25;

    /**
     * Whether this entity drifts: a {@code hover} ability in the drift style (the default
     * for {@code hover}), and no wings, which win when a bearer has both. Side-aware, so
     * the controlling client predicts the same answer as the server.
     */
    public static boolean isDrifter(LivingEntity entity) {
        if (!ExpressedGenes.canCarry(entity)) return false;
        if (!MovementAbilities.isActive(entity, Ability.HOVER)) return false;
        if (MovementAbilities.isActive(entity, Ability.ELYTRA_FLIGHT)) return false;
        if (entity.level().isClientSide) {
            return com.aetherianartificer.townstead.client.root.ClientAbilities.drifts(entity);
        }
        FlightProfile profile = FlightProfile.of(entity);
        return profile != null && profile.style() == FlightStyle.DRIFT;
    }

    /**
     * Vertical speed toward float height for a body heading one way. The floor is the
     * surface below, raised to a step just ahead when that step is at most a block up and
     * the body fits on it. Under a low ceiling here or just ahead (a doorway) the float
     * shrinks until the body fits, bob and all. {@code routeFloor}: a set floor (a drift
     * route crossing a gap level), or NaN to read the ground. Nothing below within reach
     * sinks gently.
     */
    public static double vertical(LivingEntity entity, Vec3 heading, double floatHeight, int age, boolean fluids,
                                  double routeFloor) {
        return vertical(entity, heading, floatHeight, age, fluids, routeFloor, true);
    }

    /** As above; without {@code stepUp} the float never rises onto a step ahead, so a player jumps instead. */
    public static double vertical(LivingEntity entity, Vec3 heading, double floatHeight, int age, boolean fluids,
                                  double routeFloor, boolean stepUp) {
        Level level = entity.level();
        Vec3 pos = entity.position();
        boolean onRoute = !Double.isNaN(routeFloor);
        double floor = onRoute ? routeFloor : surfaceBelow(level, pos, fluids);
        if (Double.isNaN(floor)) return -OPEN_SINK;
        Vec3 ahead = null;
        double h = Math.sqrt(heading.x * heading.x + heading.z * heading.z);
        if (h > 1.0E-3) {
            double reach = entity.getBbWidth() / 2.0 + LOOK_AHEAD;
            ahead = pos.add(heading.x / h * reach, 0, heading.z / h * reach);
        }
        double base = floor;
        if (ahead != null && !onRoute && stepUp) {
            double step = surfaceAt(level, ahead.x, ahead.z, floor + MAX_STEP, fluids);
            if (!Double.isNaN(step) && step > floor && fits(entity, ahead.x, step, ahead.z)) base = step;
        }
        // A wall ahead says nothing about the ceiling; only an opening the body fits into does.
        boolean aheadOpen = ahead != null && fits(entity, ahead.x, base, ahead.z);
        double lift = floatHeight;
        while (lift > 0 && !(fits(entity, pos.x, base + lift, pos.z)
                && (!aheadOpen || fits(entity, ahead.x, base + lift, ahead.z)))) {
            lift -= SQUEEZE_STEP;
        }
        lift = Math.max(0, lift);
        double bob = floatHeight > 0 ? Math.sin(age * 0.1) * BOB * (lift / floatHeight) : 0;
        double wanted = base + lift + bob;
        double sink = lift < floatHeight ? SQUEEZE_SINK : MAX_SINK;
        return Mth.clamp((wanted - pos.y) * (lift < floatHeight ? 0.6 : RESPONSE), -sink, MAX_RISE);
    }

    /** Whether the body fits standing with its feet at this point. */
    private static boolean fits(LivingEntity entity, double x, double y, double z) {
        AABB box = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ()).deflate(1.0E-3);
        return entity.level().noCollision(entity, box);
    }

    /** Top of the first solid or fluid surface below the feet, or NaN within the scan. */
    public static double surfaceBelow(Level level, Vec3 pos) {
        return surfaceBelow(level, pos, true);
    }

    /** As {@link #surfaceBelow(Level, Vec3)}; without {@code fluids}, water is passed through to the bed below. */
    public static double surfaceBelow(Level level, Vec3 pos, boolean fluids) {
        return surfaceAt(level, pos.x, pos.z, pos.y + 0.5, fluids);
    }

    /** Top of the highest solid (or fluid) surface in a column at or below {@code limit}, or NaN within the scan. */
    private static double surfaceAt(Level level, double px, double pz, double limit, boolean fluids) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = Mth.floor(px), z = Mth.floor(pz);
        int start = Mth.floor(limit);
        for (int y = start; y > start - GROUND_SCAN; y--) {
            cursor.set(x, y, z);
            if (!level.hasChunkAt(cursor)) return Double.NaN;
            FluidState fluid = level.getFluidState(cursor);
            if (fluids && !fluid.isEmpty()) return y + fluid.getHeight(level, cursor);
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                if (top <= limit) return top;
            }
        }
        return Double.NaN;
    }
}
