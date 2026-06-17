package com.aetherianartificer.townstead.pheno.selector;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The frame a selector resolves against. {@code self} is the current focus (an entity, or null in
 * a pure block context), {@code other} the contextual counterpart, {@code origin} the fixed
 * power-bearer. {@code pos} is the focus position spatial sources anchor on (the entity's position
 * for an entity action, the contextual block for a block action), and {@code self}'s facing drives
 * the directional places.
 */
public final class SelectorContext {

    private final LivingEntity self;
    private final LivingEntity other;
    private final LivingEntity origin;
    private final Level level;
    private final Vec3 pos;

    public SelectorContext(@Nullable LivingEntity self, @Nullable LivingEntity other,
                           @Nullable LivingEntity origin, Level level, Vec3 pos) {
        this.self = self;
        this.other = other;
        this.origin = origin;
        this.level = level;
        this.pos = pos;
    }

    public static SelectorContext of(ActionContext ctx) {
        return new SelectorContext(ctx.entity(), ctx.other(), ctx.origin(), ctx.level(), ctx.entity().position());
    }

    public static SelectorContext of(ConditionContext ctx) {
        return new SelectorContext(ctx.entity(), null, ctx.entity(), ctx.level(), ctx.entity().position());
    }

    /** A block-rooted frame (block actions): the focus is a position, the entity (if any) is the cause. */
    public static SelectorContext ofBlock(Level level, BlockPos pos, @Nullable LivingEntity cause) {
        return new SelectorContext(cause, null, cause, level, Vec3.atCenterOf(pos));
    }

    @Nullable public LivingEntity self() { return self; }

    @Nullable public LivingEntity other() { return other; }

    @Nullable public LivingEntity origin() { return origin; }

    public Level level() { return level; }

    public Vec3 pos() { return pos; }

    public BlockPos focusBlock() { return BlockPos.containing(pos); }
}
