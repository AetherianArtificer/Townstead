package com.aetherianartificer.townstead.fatigue;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived Townstead-local reservations for emergency fallback beds.
 * These claims do not touch villager HOME memory or the world's POI ownership.
 */
public final class EmergencyBedClaims {
    private static final Map<String, UUID> CLAIM_OWNER = new ConcurrentHashMap<>();
    private static final Map<String, Long> CLAIM_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, String> OWNER_TO_KEY = new ConcurrentHashMap<>();
    private static final Object CLAIM_LOCK = new Object();

    private EmergencyBedClaims() {}

    public static boolean tryClaim(ServerLevel level, UUID owner, BlockPos pos, long untilTick) {
        if (level == null || owner == null || pos == null) return false;
        String key = claimKey(level, pos);
        synchronized (CLAIM_LOCK) {
            pruneExpired(level, key);

            String existingKey = OWNER_TO_KEY.get(owner);
            if (existingKey != null && !existingKey.equals(key)) {
                releaseKey(owner, existingKey);
            }

            UUID existingOwner = CLAIM_OWNER.get(key);
            if (existingOwner != null && !existingOwner.equals(owner)) {
                return false;
            }

            CLAIM_OWNER.put(key, owner);
            CLAIM_UNTIL.put(key, untilTick);
            OWNER_TO_KEY.put(owner, key);
            return true;
        }
    }

    public static boolean isClaimedByOther(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return false;
        String key = claimKey(level, pos);
        synchronized (CLAIM_LOCK) {
            pruneExpired(level, key);
            UUID existingOwner = CLAIM_OWNER.get(key);
            return existingOwner != null && !existingOwner.equals(owner);
        }
    }

    public static boolean isClaimedBy(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return false;
        String key = claimKey(level, pos);
        synchronized (CLAIM_LOCK) {
            pruneExpired(level, key);
            UUID existingOwner = CLAIM_OWNER.get(key);
            return owner.equals(existingOwner);
        }
    }

    public static void renew(ServerLevel level, UUID owner, BlockPos pos, long untilTick) {
        tryClaim(level, owner, pos, untilTick);
    }

    public static void release(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return;
        String key = claimKey(level, pos);
        synchronized (CLAIM_LOCK) {
            UUID existingOwner = CLAIM_OWNER.get(key);
            if (!owner.equals(existingOwner)) return;
            releaseKey(owner, key);
        }
    }

    public static void releaseAll(ServerLevel level, UUID owner) {
        if (level == null || owner == null) return;
        synchronized (CLAIM_LOCK) {
            String key = OWNER_TO_KEY.remove(owner);
            if (key == null) return;
            UUID existingOwner = CLAIM_OWNER.get(key);
            if (owner.equals(existingOwner)) {
                CLAIM_OWNER.remove(key);
                CLAIM_UNTIL.remove(key);
            }
        }
    }

    private static void pruneExpired(ServerLevel level, String key) {
        Long until = CLAIM_UNTIL.get(key);
        if (until == null || until > level.getGameTime()) return;
        UUID existingOwner = CLAIM_OWNER.remove(key);
        CLAIM_UNTIL.remove(key);
        if (existingOwner != null) {
            OWNER_TO_KEY.remove(existingOwner, key);
        }
    }

    private static void releaseKey(UUID owner, String key) {
        CLAIM_OWNER.remove(key);
        CLAIM_UNTIL.remove(key);
        OWNER_TO_KEY.remove(owner, key);
    }

    private static String claimKey(ServerLevel level, BlockPos pos) {
        return claimKey(level.dimension().location(), pos.asLong());
    }

    static String claimKey(ResourceLocation dimensionId, long posAsLong) {
        return EmergencyBedClaimKeys.claimKey(dimensionId == null ? null : dimensionId.toString(), posAsLong);
    }
}
