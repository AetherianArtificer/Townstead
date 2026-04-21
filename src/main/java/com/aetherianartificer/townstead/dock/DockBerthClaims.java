package com.aetherianartificer.townstead.dock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-dock berth claim registry. Each dock has a tier-based capacity
 * (Landing=1, Pier=2, Wharf=3); fishermen claim a berth when they start
 * fishing in a dock's bounds and release it when they leave. Overflow
 * fishermen get no tier bonuses and get steered to shoreline water instead.
 *
 * Dock identity is keyed by the bounding-box min corner, not the scanning
 * fisherman's anchor barrel — otherwise two fishermen with different barrels
 * over the same plank deck would each claim their own private "dock" and
 * overwhelm the capacity cap. The plank surface is the shared resource.
 *
 * Claims auto-expire by {@code untilTick} so a crashed or unloaded villager
 * doesn't deadlock a berth forever.
 */
public final class DockBerthClaims {
    private static final Map<String, List<Claim>> BERTHS = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();

    private DockBerthClaims() {}

    /**
     * Try to reserve a berth on this dock for {@code owner}. If the owner
     * already holds a berth here, their expiry is refreshed. If capacity
     * (= dock tier) is full with other owners, returns false.
     */
    public static boolean tryClaim(ServerLevel level, Dock dock, UUID owner, long untilTick) {
        if (level == null || dock == null || owner == null) return false;
        String key = keyOf(level, dock);
        int capacity = Math.max(1, dock.tier());
        synchronized (LOCK) {
            long now = level.getGameTime();
            List<Claim> list = BERTHS.computeIfAbsent(key, k -> new ArrayList<>());
            pruneExpired(list, now);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).owner.equals(owner)) {
                    list.set(i, new Claim(owner, untilTick));
                    return true;
                }
            }
            if (list.size() >= capacity) return false;
            list.add(new Claim(owner, untilTick));
            return true;
        }
    }

    public static void release(ServerLevel level, Dock dock, UUID owner) {
        if (level == null || dock == null || owner == null) return;
        String key = keyOf(level, dock);
        synchronized (LOCK) {
            List<Claim> list = BERTHS.get(key);
            if (list == null) return;
            list.removeIf(c -> c.owner.equals(owner));
            if (list.isEmpty()) BERTHS.remove(key);
        }
    }

    /**
     * How many berths are currently held on this dock (post-expiry). Used
     * by the fisherman debug overlay; not required by the claim flow itself.
     */
    public static int occupancy(ServerLevel level, Dock dock) {
        if (level == null || dock == null) return 0;
        String key = keyOf(level, dock);
        synchronized (LOCK) {
            List<Claim> list = BERTHS.get(key);
            if (list == null) return 0;
            pruneExpired(list, level.getGameTime());
            return list.size();
        }
    }

    private static void pruneExpired(List<Claim> list, long now) {
        Iterator<Claim> it = list.iterator();
        while (it.hasNext()) {
            if (it.next().expiresAt <= now) it.remove();
        }
    }

    private static String keyOf(ServerLevel level, Dock dock) {
        BoundingBox bb = dock.bounds();
        BlockPos min = new BlockPos(bb.minX(), bb.minY(), bb.minZ());
        return level.dimension().location().toString() + "|" + min.asLong();
    }

    private record Claim(UUID owner, long expiresAt) {}
}
