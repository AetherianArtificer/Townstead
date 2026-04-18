package com.aetherianartificer.townstead.ai.work.producer;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProducerStationClaims {
    private static final Map<String, UUID> STATION_CLAIM_OWNER = new ConcurrentHashMap<>();
    private static final Map<String, Long> STATION_CLAIM_UNTIL = new ConcurrentHashMap<>();
    private static final Object CLAIM_LOCK = new Object();

    private ProducerStationClaims() {}

    public static void claim(ServerLevel level, UUID owner, BlockPos pos, long untilTick) {
        tryClaim(level, owner, pos, untilTick);
    }

    public static boolean tryClaim(ServerLevel level, UUID owner, BlockPos pos, long untilTick) {
        if (level == null || owner == null || pos == null) return false;
        String key = claimKey(level, pos);
        synchronized (CLAIM_LOCK) {
            long now = level.getGameTime();
            Long existingUntil = STATION_CLAIM_UNTIL.get(key);
            UUID existingOwner = STATION_CLAIM_OWNER.get(key);
            if (existingUntil != null && existingUntil <= now) {
                STATION_CLAIM_OWNER.remove(key);
                STATION_CLAIM_UNTIL.remove(key);
                existingOwner = null;
            }
            if (existingOwner != null && !existingOwner.equals(owner)) {
                return false;
            }
            STATION_CLAIM_OWNER.put(key, owner);
            STATION_CLAIM_UNTIL.put(key, untilTick);
            return true;
        }
    }

    public static void release(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return;
        String key = claimKey(level, pos);
        synchronized (CLAIM_LOCK) {
            UUID existingOwner = STATION_CLAIM_OWNER.get(key);
            if (existingOwner == null || !existingOwner.equals(owner)) return;
            STATION_CLAIM_OWNER.remove(key);
            STATION_CLAIM_UNTIL.remove(key);
        }
    }

    public static boolean isClaimedByOther(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return false;
        String key = claimKey(level, pos);
        synchronized (CLAIM_LOCK) {
            Long until = STATION_CLAIM_UNTIL.get(key);
            if (until == null) return false;
            if (until <= level.getGameTime()) {
                STATION_CLAIM_OWNER.remove(key);
                STATION_CLAIM_UNTIL.remove(key);
                return false;
            }
            UUID existingOwner = STATION_CLAIM_OWNER.get(key);
            return existingOwner != null && !existingOwner.equals(owner);
        }
    }

    private static String claimKey(ServerLevel level, BlockPos pos) {
        return claimKey(level.dimension().location(), pos.asLong());
    }

    static String claimKey(ResourceLocation dimensionId, long posAsLong) {
        return ProducerClaimKeys.claimKey(dimensionId == null ? null : dimensionId.toString(), posAsLong);
    }
}
