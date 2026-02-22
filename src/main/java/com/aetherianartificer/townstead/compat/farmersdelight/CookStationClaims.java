package com.aetherianartificer.townstead.compat.farmersdelight;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CookStationClaims {
    private static final Map<String, UUID> STATION_CLAIM_OWNER = new ConcurrentHashMap<>();
    private static final Map<String, Long> STATION_CLAIM_UNTIL = new ConcurrentHashMap<>();

    private CookStationClaims() {}

    static void claim(ServerLevel level, UUID owner, BlockPos pos, long untilTick) {
        if (level == null || owner == null || pos == null) return;
        String key = claimKey(level, pos);
        STATION_CLAIM_OWNER.put(key, owner);
        STATION_CLAIM_UNTIL.put(key, untilTick);
    }

    static void release(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return;
        String key = claimKey(level, pos);
        UUID existingOwner = STATION_CLAIM_OWNER.get(key);
        if (existingOwner == null || !existingOwner.equals(owner)) return;
        STATION_CLAIM_OWNER.remove(key);
        STATION_CLAIM_UNTIL.remove(key);
    }

    static boolean isClaimedByOther(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return false;
        String key = claimKey(level, pos);
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

    private static String claimKey(ServerLevel level, BlockPos pos) {
        return claimKey(level.dimension().location(), pos.asLong());
    }

    static String claimKey(ResourceLocation dimensionId, long posAsLong) {
        return CookClaimKeys.claimKey(dimensionId == null ? null : dimensionId.toString(), posAsLong);
    }
}
