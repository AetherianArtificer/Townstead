package com.aetherianartificer.townstead.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

    // Server-side only. A structured key cannot collide the way XOR-combining two hashes can.
    private static final Map<Key, FieldPostBlockEntity> INDEX = new ConcurrentHashMap<>();

    private static Key key(Level level, BlockPos pos) {
        return new Key(level.dimension().location(), pos.asLong());
    }

    public static void register(LevelAccessor level, BlockPos pos, FieldPostBlockEntity be) {
        if (level instanceof ServerLevel serverLevel && be != null && !be.isRemoved()) {
            INDEX.put(key(serverLevel, pos), be);
        }
    }

    public static void remove(LevelAccessor level, BlockPos pos) {
        // Client and integrated-server block entities share this static class. A client chunk
        // unload must never evict the authoritative server entry.
        if (level instanceof ServerLevel serverLevel) {
            INDEX.remove(key(serverLevel, pos));
        }
    }

    /**
     * Called when a Field Post's config (including cell plan) changes.
     * Invalidates any cached farm snapshots that may cover this post's area,
     * so farmers pick up the new plan on their next tick.
     */
    public static void notifyConfigChanged(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        FieldPostBlockEntity post = INDEX.get(key(serverLevel, pos));
        int radius = post != null ? post.getRadius() : 32;
        // Invalidate snapshot caches for any farm anchor that could be covered by this post
        for (int dx = -radius; dx <= radius; dx += 16) {
            for (int dz = -radius; dz <= radius; dz += 16) {
                com.aetherianartificer.townstead.hunger.HarvestWorkIndex.invalidate(
                        serverLevel, pos.offset(dx, 0, dz));
            }
        }
    }

    /**
     * Find the best Field Post covering the given anchor position.
     * "Best" = highest priority, ties broken by closest distance.
     */
    @Nullable
    public static FieldPostBlockEntity findBestForAnchor(Level level, BlockPos anchor) {
        if (!(level instanceof ServerLevel)) return null;
        purgeInvalid();
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
        if (!(level instanceof ServerLevel)) return java.util.List.of();
        purgeInvalid();
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

    public static int size() {
        purgeInvalid();
        return INDEX.size();
    }

    private static void purgeInvalid() {
        INDEX.entrySet().removeIf(entry -> {
            FieldPostBlockEntity post = entry.getValue();
            return post == null || post.isRemoved() || !(post.getLevel() instanceof ServerLevel);
        });
    }

    private record Key(ResourceLocation dimension, long position) {}
}
