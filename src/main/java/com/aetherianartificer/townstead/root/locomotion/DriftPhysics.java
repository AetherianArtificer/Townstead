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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The float shared by drifting villagers and players: hold a set height over whatever is
 * below (the surface, or a fluid's top), bob a little, rise before a step or after
 * bumping into something, and sink gently where the ground falls away.
 */
public final class DriftPhysics {

    private DriftPhysics() {}

    public static final double BOB = 0.06;
    public static final double MAX_RISE = 0.18;
    public static final double MAX_SINK = 0.12;
    public static final double OPEN_SINK = 0.08;
    private static final int GROUND_SCAN = 8;
    private static final double RESPONSE = 0.3;

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
     * Vertical speed toward float height. {@code floor}: the height to float over (the
     * surface below, or higher for a step ahead); NaN when nothing is below within reach,
     * which sinks gently.
     */
    public static double vertical(double y, double floor, double floatHeight, int age, boolean bumped) {
        if (Double.isNaN(floor)) return -OPEN_SINK;
        double base = bumped ? Math.max(floor, y - floatHeight + 1.0) : floor;
        double wanted = base + floatHeight + Math.sin(age * 0.1) * BOB;
        return Mth.clamp((wanted - y) * RESPONSE, -MAX_SINK, MAX_RISE);
    }

    /** Top of the first solid or fluid surface below the feet, or NaN within the scan. */
    public static double surfaceBelow(Level level, Vec3 pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int x = Mth.floor(pos.x), z = Mth.floor(pos.z);
        int start = Mth.floor(pos.y + 0.5);
        for (int y = start; y > start - GROUND_SCAN; y--) {
            cursor.set(x, y, z);
            if (!level.hasChunkAt(cursor)) return Double.NaN;
            FluidState fluid = level.getFluidState(cursor);
            if (!fluid.isEmpty()) return y + fluid.getHeight(level, cursor);
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                if (top <= pos.y + 0.5) return top;
            }
        }
        return Double.NaN;
    }
}
