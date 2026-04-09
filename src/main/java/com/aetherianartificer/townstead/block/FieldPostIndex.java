package com.aetherianartificer.townstead.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight cache of loaded Field Post positions for fast lookup by the farming AI.
 * Posts register/unregister themselves as block entities load/unload.
 */
public final class FieldPostIndex {
    private FieldPostIndex() {}

    // Keyed by dimension + position packed long for fast lookup
    private static final Map<Long, FieldPostBlockEntity> INDEX = new ConcurrentHashMap<>();

    private static long key(Level level, BlockPos pos) {
        // Combine dimension hash with block pos long for uniqueness across dimensions
        return pos.asLong() ^ ((long) level.dimension().location().hashCode() << 32);
    }

    public static void register(LevelAccessor level, BlockPos pos, FieldPostBlockEntity be) {
        if (level instanceof Level l) {
            INDEX.put(key(l, pos), be);
        }
    }

    public static void remove(LevelAccessor level, BlockPos pos) {
        if (level instanceof Level l) {
            INDEX.remove(key(l, pos));
        }
    }

    /**
     * Find the best Field Post covering the given anchor position.
     * "Best" = highest priority, ties broken by closest distance.
     */
    @Nullable
    public static FieldPostBlockEntity findBestForAnchor(Level level, BlockPos anchor) {
        FieldPostBlockEntity best = null;
        int bestPriority = -1;
        int bestDistSq = Integer.MAX_VALUE;

        for (FieldPostBlockEntity post : INDEX.values()) {
            if (post.isRemoved()) continue;
            if (post.getLevel() != level) continue;

            BlockPos postPos = post.getBlockPos();
            int dx = Math.abs(anchor.getX() - postPos.getX());
            int dz = Math.abs(anchor.getZ() - postPos.getZ());
            int postRadius = post.getRadius();

            if (dx > postRadius || dz > postRadius) continue;

            int priority = post.getPriority();
            int distSq = dx * dx + dz * dz;

            if (priority > bestPriority || (priority == bestPriority && distSq < bestDistSq)) {
                best = post;
                bestPriority = priority;
                bestDistSq = distSq;
            }
        }
        return best;
    }

    /**
     * Returns all Field Posts in the given level within radius of the given position.
     */
    public static java.util.List<FieldPostBlockEntity> findAllInRange(Level level, BlockPos center, int radius) {
        java.util.List<FieldPostBlockEntity> result = new java.util.ArrayList<>();
        for (FieldPostBlockEntity post : INDEX.values()) {
            if (post.isRemoved()) continue;
            if (post.getLevel() != level) continue;
            BlockPos postPos = post.getBlockPos();
            int dx = Math.abs(center.getX() - postPos.getX());
            int dz = Math.abs(center.getZ() - postPos.getZ());
            if (dx <= radius && dz <= radius) {
                result.add(post);
            }
        }
        return result;
    }

    public static void clear() {
        INDEX.clear();
    }
}
