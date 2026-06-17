package com.aetherianartificer.townstead.pheno.action;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs actions after a tick delay (the {@code delay} meta). Entries are checked each
 * server tick against their level's game time; the action is skipped if its actor has
 * since been removed. Single-threaded (scheduled and ticked on the server thread), so no
 * synchronization is needed. Cleared on server stop.
 */
public final class ActionScheduler {

    private record Entry(ServerLevel level, long dueTick, Action action, ActionContext ctx) {}

    private static final List<Entry> PENDING = new ArrayList<>();

    private ActionScheduler() {}

    public static void schedule(ActionContext ctx, int delayTicks, Action action) {
        if (delayTicks <= 0 || !(ctx.entity().level() instanceof ServerLevel level)) {
            action.run(ctx);
            return;
        }
        PENDING.add(new Entry(level, level.getGameTime() + delayTicks, action, ctx));
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        Iterator<Entry> it = PENDING.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.level().getGameTime() < entry.dueTick()) continue;
            it.remove();
            if (!entry.ctx().entity().isRemoved()) entry.action().run(entry.ctx());
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
