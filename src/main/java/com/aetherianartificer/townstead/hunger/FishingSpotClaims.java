package com.aetherianartificer.townstead.hunger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-water-spot claim registry for fishermen. A spot can be held by at most one
 * villager at a time; other fishermen see the spot as unavailable and pick a
 * different one. Claims expire on their own tick budget so crashed or unloaded
 * villagers don't deadlock a spot forever.
 */
public final class FishingSpotClaims {
    private static final Map<String, UUID> OWNERS = new ConcurrentHashMap<>();
    private static final Map<String, Long> UNTIL = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();

    private FishingSpotClaims() {}

    public static boolean tryClaim(ServerLevel level, UUID owner, BlockPos pos, long untilTick) {
        if (level == null || owner == null || pos == null) return false;
        String key = key(level, pos);
        synchronized (LOCK) {
            long now = level.getGameTime();
            Long existingUntil = UNTIL.get(key);
            UUID existingOwner = OWNERS.get(key);
            if (existingUntil != null && existingUntil <= now) {
                OWNERS.remove(key);
                UNTIL.remove(key);
                existingOwner = null;
            }
            if (existingOwner != null && !existingOwner.equals(owner)) return false;
            OWNERS.put(key, owner);
            UNTIL.put(key, untilTick);
            return true;
        }
    }

    public static void release(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return;
        String key = key(level, pos);
        synchronized (LOCK) {
            UUID existing = OWNERS.get(key);
            if (existing == null || !existing.equals(owner)) return;
            OWNERS.remove(key);
            UNTIL.remove(key);
        }
    }

    public static boolean isClaimedByOther(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return false;
        String key = key(level, pos);
        synchronized (LOCK) {
            Long until = UNTIL.get(key);
            if (until == null) return false;
            if (until <= level.getGameTime()) {
                OWNERS.remove(key);
                UNTIL.remove(key);
                return false;
            }
            UUID existing = OWNERS.get(key);
            return existing != null && !existing.equals(owner);
        }
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location().toString() + "|" + pos.asLong();
    }
}
