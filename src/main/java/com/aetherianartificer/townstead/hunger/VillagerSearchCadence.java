package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spreads repeated expensive searches across ticks using deterministic
 * villager-specific offsets.
 */
public final class VillagerSearchCadence {
    // By kind, then villager: tasks ask whether they are due every tick, so a lookup allocates nothing.
    private static final Map<String, Map<UUID, Long>> NEXT_SEARCH_TICK = new ConcurrentHashMap<>();

    private VillagerSearchCadence() {}

    public static boolean isDue(ServerLevel level, VillagerEntityMCA villager, String kind) {
        if (level == null || villager == null || kind == null) return true;
        long gameTime = level.getGameTime();
        Map<UUID, Long> ofKind = NEXT_SEARCH_TICK.get(kind);
        Long next = ofKind == null ? null : ofKind.get(villager.getUUID());
        return next == null || gameTime >= next;
    }

    public static void schedule(ServerLevel level, VillagerEntityMCA villager, String kind, int baseTicks, int jitterRange) {
        if (level == null || villager == null || kind == null) return;
        long now = level.getGameTime();
        long jitter = jitterRange <= 0 ? 0L : Math.floorMod(villager.getUUID().hashCode() ^ kind.hashCode(), jitterRange);
        NEXT_SEARCH_TICK.computeIfAbsent(kind, ignored -> new ConcurrentHashMap<>())
                .put(villager.getUUID(), now + Math.max(0, baseTicks) + jitter);
    }

    public static void clear(VillagerEntityMCA villager, String kind) {
        if (villager == null || kind == null) return;
        Map<UUID, Long> ofKind = NEXT_SEARCH_TICK.get(kind);
        if (ofKind != null) ofKind.remove(villager.getUUID());
    }
}
