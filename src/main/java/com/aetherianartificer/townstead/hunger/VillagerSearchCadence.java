package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spreads repeated expensive searches across ticks using deterministic
 * villager-specific offsets.
 */
public final class VillagerSearchCadence {
    private static final Map<String, Long> NEXT_SEARCH_TICK = new ConcurrentHashMap<>();

    private VillagerSearchCadence() {}

    public static boolean isDue(ServerLevel level, VillagerEntityMCA villager, String kind) {
        if (level == null || villager == null || kind == null) return true;
        long gameTime = level.getGameTime();
        return gameTime >= NEXT_SEARCH_TICK.getOrDefault(key(villager, kind), 0L);
    }

    public static void schedule(ServerLevel level, VillagerEntityMCA villager, String kind, int baseTicks, int jitterRange) {
        if (level == null || villager == null || kind == null) return;
        long now = level.getGameTime();
        long jitter = jitterRange <= 0 ? 0L : Math.floorMod(villager.getUUID().hashCode() ^ kind.hashCode(), jitterRange);
        NEXT_SEARCH_TICK.put(key(villager, kind), now + Math.max(0, baseTicks) + jitter);
    }

    public static void clear(VillagerEntityMCA villager, String kind) {
        if (villager == null || kind == null) return;
        NEXT_SEARCH_TICK.remove(key(villager, kind));
    }

    private static String key(VillagerEntityMCA villager, String kind) {
        return villager.getUUID() + "|" + kind;
    }
}
