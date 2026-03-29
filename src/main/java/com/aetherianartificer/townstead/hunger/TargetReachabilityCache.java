package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived cache of recent pathfinding failures so villagers do not
 * repeatedly retry obviously unreachable targets every few ticks.
 */
public final class TargetReachabilityCache {
    private static final Map<String, Long> FAILED_UNTIL = new ConcurrentHashMap<>();

    private TargetReachabilityCache() {}

    public static boolean canAttempt(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (level == null || villager == null || pos == null) return true;
        long now = level.getGameTime();
        String key = key(level, villager, pos);
        Long until = FAILED_UNTIL.get(key);
        if (until == null) return true;
        if (until <= now) {
            FAILED_UNTIL.remove(key);
            return true;
        }
        return false;
    }

    public static void recordFailure(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, int ttlTicks) {
        if (level == null || villager == null || pos == null) return;
        FAILED_UNTIL.put(key(level, villager, pos), level.getGameTime() + Math.max(1, ttlTicks));
    }

    public static void clear(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        if (level == null || villager == null || pos == null) return;
        FAILED_UNTIL.remove(key(level, villager, pos));
    }

    private static String key(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        return level.dimension().location() + "|" + villager.getUUID() + "|" + pos.asLong();
    }
}
