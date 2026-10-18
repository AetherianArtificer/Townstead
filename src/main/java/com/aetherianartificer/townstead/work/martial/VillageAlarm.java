package com.aetherianartificer.townstead.work.martial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a place is under attack: a raid, or a siege another mod reports while it lasts (a
 * Vampirism capture, say). A siege report holds for a few seconds, so one that ends or is unloaded
 * lapses by itself.
 */
public final class VillageAlarm {
    private static final long HOLD = 60;

    private record Siege(String dimension, AABB area, long until) {}

    private static final Map<String, Siege> SIEGES = new ConcurrentHashMap<>();

    private VillageAlarm() {}

    public static void siege(ServerLevel level, BlockPos source, AABB area) {
        String dimension = level.dimension().location().toString();
        SIEGES.put(dimension + "@" + source.asLong(), new Siege(dimension, area, level.getGameTime() + HOLD));
    }

    public static boolean sounding(ServerLevel level, BlockPos pos) {
        if (level.isRaided(pos)) return true;
        if (SIEGES.isEmpty()) return false;
        long now = level.getGameTime();
        String dimension = level.dimension().location().toString();
        Vec3 at = Vec3.atCenterOf(pos);
        boolean sounding = false;
        for (var it = SIEGES.values().iterator(); it.hasNext(); ) {
            Siege siege = it.next();
            if (!siege.dimension().equals(dimension)) continue;
            if (siege.until() < now || siege.until() - now > HOLD) {
                it.remove();
                continue;
            }
            if (siege.area().contains(at)) sounding = true;
        }
        return sounding;
    }
}
