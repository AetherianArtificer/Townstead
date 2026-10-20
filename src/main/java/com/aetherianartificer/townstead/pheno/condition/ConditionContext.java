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
    private final @Nullable LivingEntity other;
    private final @Nullable LivingEntity origin;
    private final @Nullable java.util.UUID otherId;
    // Set when asking "would this hold if the entity stood there?", as hazard avoidance does.
    private @Nullable BlockPos probe;
    // The item the question is about, such as a gift being offered. Null until set, so building a
    // context never loads ItemStack (offline Pheno and unit tests have no game bootstrap).
    private net.minecraft.world.item.@Nullable ItemStack item;

    public ConditionContext(LivingEntity entity) {
        this(entity, null, entity);
    }

    public ConditionContext(LivingEntity entity, @Nullable LivingEntity other) {
        this(entity, other, entity);
    }

    public ConditionContext(LivingEntity entity, @Nullable LivingEntity other, @Nullable LivingEntity origin) {
        this.entity = entity;
        this.subject = null;
        this.other = other;
        this.origin = origin;
        this.otherId = other == null ? null : other.getUUID();
    }

    public ConditionContext(PhenoSubject subject) {
        this(subject, null);
    }

    public ConditionContext(PhenoSubject subject, @Nullable java.util.UUID otherId) {
        this.entity = null;
        this.subject = subject;
        this.other = null;
        this.origin = null;
        this.otherId = otherId;
    }

    public @Nullable LivingEntity other() { return other; }
    public @Nullable LivingEntity origin() { return origin; }
    public @Nullable java.util.UUID otherId() { return otherId; }

    public static ConditionContext of(com.aetherianartificer.townstead.pheno.action.ActionContext context) {
        return new ConditionContext(context.entity(), context.other(), context.origin());
    }

    public static ConditionContext of(com.aetherianartificer.townstead.pheno.selector.SelectorContext context) {
        return context.subject() != null ? new ConditionContext(context.subject(), context.otherId())
                : new ConditionContext(context.self(), context.other(), context.origin());
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
        if (probe != null) return probe;
        return entity == null ? null : entity.blockPosition();
    }

    /**
     * The same context, read as if the entity stood at {@code pos}: every condition that asks where
     * the entity is answers for that block, and conditions about the entity itself are unchanged.
     */
    public ConditionContext at(BlockPos pos) {
        ConditionContext copy = entity != null ? new ConditionContext(entity, other, origin)
                : new ConditionContext(subject, otherId);
        copy.probe = pos == null ? null : pos.immutable();
        copy.item = item;
        return copy;
    }

    /** The same context, asked about {@code stack} (a gift being offered, an item being used). */
    public ConditionContext withItem(net.minecraft.world.item.ItemStack stack) {
        ConditionContext copy = entity != null ? new ConditionContext(entity, other, origin)
                : new ConditionContext(subject, otherId);
        copy.probe = probe;
        copy.item = stack;
        return copy;
    }

    /** The item this context asks about; empty when it asks about none. */
    public net.minecraft.world.item.ItemStack item() {
        return item == null ? net.minecraft.world.item.ItemStack.EMPTY : item;
    }

    /** True when this context is answering for a position other than the entity's own. */
    public boolean probing() {
        return probe != null;
    }
}
