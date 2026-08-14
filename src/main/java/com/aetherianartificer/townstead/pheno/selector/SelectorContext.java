package com.aetherianartificer.townstead.pheno.selector;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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
    private final Map<String, List<BlockPos>> blockRoles;
    private final @Nullable Predicate<BlockPos> defaultBlockMembership;
    private final @Nullable Integer villageId;
    private final @Nullable com.aetherianartificer.townstead.pheno.reservation.ReservationScope reservations;

    public SelectorContext(@Nullable LivingEntity self, @Nullable LivingEntity other,
                           @Nullable LivingEntity origin, Level level, Vec3 pos) {
        this(self, other, origin, level, pos, Map.of(), null, null, null);
    }

    private SelectorContext(@Nullable LivingEntity self, @Nullable LivingEntity other,
                            @Nullable LivingEntity origin, Level level, Vec3 pos,
                            Map<String, List<BlockPos>> blockRoles,
                            @Nullable Predicate<BlockPos> defaultBlockMembership,
                            @Nullable Integer villageId,
                            @Nullable com.aetherianartificer.townstead.pheno.reservation.ReservationScope reservations) {
        this.self = self;
        this.other = other;
        this.origin = origin;
        this.level = level;
        this.pos = pos;
        this.blockRoles = blockRoles;
        this.defaultBlockMembership = defaultBlockMembership;
        this.villageId = villageId;
        this.reservations = reservations;
    }

    public static SelectorContext of(ActionContext ctx) {
        return new SelectorContext(ctx.entity(), ctx.other(), ctx.origin(), ctx.level(),
                ctx.entity().position(), Map.of(), null, null, ctx.reservations());
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

    /** Names a previously resolved block selection for expressions such as count(of=structure). */
    public SelectorContext withBlockRole(String role, List<BlockPos> positions) {
        java.util.LinkedHashMap<String, List<BlockPos>> roles = new java.util.LinkedHashMap<>(blockRoles);
        roles.put(role, List.copyOf(positions));
        return new SelectorContext(self, other, origin, level, pos, Map.copyOf(roles),
                defaultBlockMembership, villageId, reservations);
    }

    public List<BlockPos> blockRole(String role) {
        return blockRoles.getOrDefault(role, List.of());
    }

    /** Supplies domain membership when a generic selector omits an explicit condition. */
    public SelectorContext withDefaultBlockMembership(Predicate<BlockPos> membership) {
        return new SelectorContext(self, other, origin, level, pos, blockRoles, membership,
                villageId, reservations);
    }

    public @Nullable Predicate<BlockPos> defaultBlockMembership() {
        return defaultBlockMembership;
    }

    /**
     * Supplies the exact village owned by the surrounding host (for example a worksite).  Generic
     * Pheno entry points may omit it, in which case village-aware selectors resolve the village
     * from the focus position.
     */
    public SelectorContext withVillage(int id) {
        return new SelectorContext(self, other, origin, level, pos, blockRoles,
                defaultBlockMembership, id, reservations);
    }

    public @Nullable Integer villageId() {
        return villageId;
    }

    /** The execution-owned reservation frame, when selection happens inside an action. */
    public @Nullable com.aetherianartificer.townstead.pheno.reservation.ReservationScope reservations() {
        return reservations;
    }
}
