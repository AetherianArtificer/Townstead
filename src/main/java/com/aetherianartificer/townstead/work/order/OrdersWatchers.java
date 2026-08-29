package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who currently has a worksite's orders open, so the server can tell them when something changes
 * instead of being asked once a second.
 *
 * <p>Deliberately <em>not</em> built on Chronicles. That system records what happened in a
 * village's story — templates, witnesses, cooldowns, an archive on disk — and it is rate-limited
 * and probabilistic by design. A screen needing to know that a count went from eight to nine is
 * not a story event, and pushing one through there would both flood the history and arrive
 * late.</p>
 *
 * <p>What can be pushed is pushed: every change to an order passes through code we own, so it
 * bumps the worksite's revision and the next tick sends the snapshot. What cannot is
 * <strong>stock</strong> — nothing in the game announces that a chest three blocks away now holds
 * one fewer carrot, and there is no cross-mod signal for it — so a slow re-read backs the pushes
 * up. Instant for state, a few seconds for counts, rather than a full rebuild every second.</p>
 */
public final class OrdersWatchers {

    /** How often the watch list is examined at all. Cheap: a revision compare per watcher. */
    private static final int SCAN_TICKS = 5;

    /**
     * How often a watcher is refreshed even when nothing has been reported as changed, so numbers
     * read out of containers do not sit stale. The only polling left in the system.
     */
    private static final int STOCK_TICKS = 60;

    private record Watch(long worksiteId, int revision, int lastSent) {}

    private static final Map<UUID, Watch> WATCHING = new ConcurrentHashMap<>();
    private static int tickCounter;

    private OrdersWatchers() {}

    /** Starts watching, or moves an existing watch to another worksite. */
    public static void watch(ServerPlayer player, Worksite site) {
        WATCHING.put(player.getUUID(), new Watch(site.id(), site.ordersRevision(), 0));
    }

    /** The screen closed, the player left, or they opened something else. */
    public static void forget(ServerPlayer player) {
        WATCHING.remove(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (WATCHING.isEmpty()) return;
        if (++tickCounter < SCAN_TICKS) return;
        tickCounter = 0;

        WorksiteRegister register = WorksiteRegister.get(server);
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, Watch> entry : WATCHING.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                gone.add(entry.getKey());
                continue;
            }
            Watch watch = entry.getValue();
            Worksite site = register.byId(watch.worksiteId());
            if (site == null) {
                gone.add(entry.getKey());
                continue;
            }
            int age = watch.lastSent() + SCAN_TICKS;
            boolean changed = site.ordersRevision() != watch.revision();
            if (!changed && age < STOCK_TICKS) {
                WATCHING.put(entry.getKey(), new Watch(watch.worksiteId(), watch.revision(), age));
                continue;
            }
            OrdersOpener.open(player, site);
            WATCHING.put(entry.getKey(), new Watch(watch.worksiteId(), site.ordersRevision(), 0));
        }
        gone.forEach(WATCHING::remove);
    }
}
