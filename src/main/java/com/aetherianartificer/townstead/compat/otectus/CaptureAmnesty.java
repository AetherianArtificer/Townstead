package com.aetherianartificer.townstead.compat.otectus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a Vampirism capture is running, or ended in the last half minute. MCA: Crime and MCA:
 * Reputation judge a player who strikes or kills a villager there as they would anywhere else; the
 * capture is a war, so {@code CrimeGateCaptureAmnestyMixin} drops Crime's charges and
 * {@link ReputationCaptureExemption} Reputation's deeds while a window covers the victim.
 * The totem reports once a second while it captures, so a window that stops being refreshed
 * lapses by itself after the tail.
 */
public final class CaptureAmnesty {
    static final long TAIL = 600;
    private static final long REFRESH = 20;

    private record Window(String dimension, AABB area, long until) {}

    private static final Map<String, Window> WINDOWS = new ConcurrentHashMap<>();

    private CaptureAmnesty() {}

    public static void report(ServerLevel level, BlockPos totem, AABB area) {
        report(level.dimension().location().toString(), totem.asLong(), area, level.getGameTime());
    }

    public static boolean covers(ServerLevel level, BlockPos pos) {
        if (WINDOWS.isEmpty()) return false;
        return covers(level.dimension().location().toString(), Vec3.atCenterOf(pos), level.getGameTime());
    }

    static void report(String dimension, long source, AABB area, long now) {
        WINDOWS.put(dimension + "@" + source, new Window(dimension, area, now + TAIL + REFRESH));
    }

    static boolean covers(String dimension, Vec3 at, long now) {
        boolean covered = false;
        for (var it = WINDOWS.values().iterator(); it.hasNext(); ) {
            Window window = it.next();
            // A window far ahead of the clock was left by another world (single player keeps this map).
            if (window.until() < now || window.until() - now > TAIL + REFRESH) {
                it.remove();
                continue;
            }
            if (window.dimension().equals(dimension) && window.area().contains(at)) covered = true;
        }
        return covered;
    }

    static void clear() {
        WINDOWS.clear();
    }
}
