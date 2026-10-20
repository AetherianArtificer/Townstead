package com.aetherianartificer.townstead.pheno.field;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks active {@link Cloud} fields and advances them each server tick, dropping those that have
 * dissipated. Single-threaded (spawned and ticked on the server thread), so no synchronization is
 * needed. Cleared on server stop, matching the transient lifetime of ability cooldowns.
 */
public final class CloudManager {

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private CloudManager() {}

    public static void spawn(Cloud cloud) {
        CLOUDS.add(cloud);
    }

    public static void tick(MinecraftServer server) {
        if (CLOUDS.isEmpty()) return;
        Iterator<Cloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            Cloud cloud = it.next();
            if (cloud.level().getServer() != server || !cloud.tick()) it.remove();
        }
    }

    /** Summed local-climate offsets of the fields covering a position. Server thread only. */
    public static float climateOffset(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (CLOUDS.isEmpty() || !level.getServer().isSameThread()) return 0f;
        float total = 0f;
        for (Cloud cloud : CLOUDS) if (cloud.level() == level) total += cloud.climateOffsetAt(pos);
        return total;
    }

    public static void clear() {
        CLOUDS.clear();
    }
}
