package com.aetherianartificer.townstead.pheno.condition;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The entity and world state a {@link Condition} reads. Uniform for villagers and
 * players so a conditioned gene gates the same way on both.
 *
 * <p>A context may instead carry a {@link PhenoSubject}: someone described by
 * facts, with no entity and no world. Only conditions reporting
 * {@link Condition#supportsSubject()} may be evaluated against one, so
 * {@link #entity()} is null there and callers must check the flag first.</p>
 */
public final class ConditionContext {

    private final @Nullable LivingEntity entity;
    private final @Nullable PhenoSubject subject;

    public ConditionContext(LivingEntity entity) {
        this.entity = entity;
        this.subject = null;
    }

    public ConditionContext(PhenoSubject subject) {
        this.entity = null;
        this.subject = subject;
    }

    public @Nullable LivingEntity entity() {
        return entity;
    }

    /** Non-null exactly when this context describes someone not in the world. */
    public @Nullable PhenoSubject subject() {
        return subject;
    }

    public @Nullable Level level() {
        return entity == null ? null : entity.level();
    }

    public @Nullable BlockPos pos() {
        return entity == null ? null : entity.blockPosition();
    }
}
