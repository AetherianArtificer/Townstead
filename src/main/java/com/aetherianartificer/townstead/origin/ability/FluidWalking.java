package com.aetherianartificer.townstead.origin.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Holds a walk-on-fluid entity at the surface of the fluid it stands over: each
 * physics tick, when the entity is at or just above a fluid's top face (and not
 * sneaking, which lets it dive), its downward motion is zeroed and it is lifted to
 * the surface. Side-agnostic; called from the {@code aiStep} mixin on whichever
 * side controls the entity (server for villagers, the owning client for players),
 * so it never fights client prediction.
 */
public final class FluidWalking {

    private FluidWalking() {}

    public static void clamp(LivingEntity entity) {
        if (entity.isShiftKeyDown()) return;
        Level level = entity.level();
        double y = entity.getY();
        BlockPos feet = BlockPos.containing(entity.getX(), y, entity.getZ());
        for (BlockPos pos : new BlockPos[]{feet, feet.below()}) {
            FluidState fluid = level.getFluidState(pos);
            if (fluid.isEmpty()) continue;
            // Only the topmost fluid block counts as a surface to stand on.
            if (!level.getFluidState(pos.above()).isEmpty()) continue;
            double surface = pos.getY() + fluid.getHeight(level, pos);
            if (y > surface + 0.1 || y < surface - 1.0) continue;
            Vec3 motion = entity.getDeltaMovement();
            if (motion.y < 0) entity.setDeltaMovement(motion.x, 0.0, motion.z);
            if (entity.getY() < surface) entity.setPos(entity.getX(), surface, entity.getZ());
            entity.resetFallDistance();
            return;
        }
    }
}
