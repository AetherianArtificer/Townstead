package com.aetherianartificer.townstead.block.haze;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Concealing haze hides whoever has their eyes in it, and blinds whoever is looking out of it.
 * New targets are vetoed from the change-target event; mobs that already hold a target lose it
 * on the haze's own decay tick, at most once per chunk section every {@link #SWEEP_INTERVAL}.
 */
public final class HazeConcealment {

    /** Inside this range a mob can still find its target by touch and sound. */
    private static final double REVEAL_DISTANCE_SQR = 2.5 * 2.5;
    private static final double SWEEP_RADIUS = 16.0;
    private static final long SWEEP_INTERVAL = 10L;

    private static final Map<ServerLevel, Long2LongOpenHashMap> LAST_SWEEP = new WeakHashMap<>();

    private HazeConcealment() {}

    /** Whether {@code mob} cannot see {@code target} through concealing haze. */
    public static boolean conceals(@Nullable LivingEntity mob, @Nullable LivingEntity target) {
        if (mob == null || target == null || mob.level().isClientSide()) return false;
        if (mob.distanceToSqr(target) <= REVEAL_DISTANCE_SQR) return false;
        return inConcealingHaze(target) || inConcealingHaze(mob);
    }

    static void sweep(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        Long2LongOpenHashMap last = LAST_SWEEP.computeIfAbsent(level, l -> new Long2LongOpenHashMap());
        long section = SectionPos.asLong(pos);
        if (now - last.getOrDefault(section, Long.MIN_VALUE / 2) < SWEEP_INTERVAL) return;
        last.put(section, now);
        if (last.size() > 512) last.long2LongEntrySet().removeIf(e -> now - e.getLongValue() > SWEEP_INTERVAL);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(SWEEP_RADIUS),
                m -> m.getTarget() != null)) {
            if (!conceals(mob, mob.getTarget())) continue;
            mob.setTarget(null);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    private static boolean inConcealingHaze(LivingEntity entity) {
        BlockState state = entity.level().getBlockState(BlockPos.containing(entity.getEyePosition()));
        HazeKind kind = HazeBlock.kindOf(state, false);
        return kind != null && kind.conceals();
    }
}
