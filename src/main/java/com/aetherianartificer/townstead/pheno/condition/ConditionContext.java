package com.aetherianartificer.townstead.pheno.condition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The entity and world state a {@link Condition} reads. Uniform for villagers and
 * players so a conditioned gene gates the same way on both.
 */
public final class ConditionContext {

    private final LivingEntity entity;

    public ConditionContext(LivingEntity entity) {
        this.entity = entity;
    }

    public LivingEntity entity() {
        return entity;
    }

    public Level level() {
        return entity.level();
    }

    public BlockPos pos() {
        return entity.blockPosition();
    }
}
