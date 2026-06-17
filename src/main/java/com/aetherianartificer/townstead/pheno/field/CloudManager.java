package com.aetherianartificer.townstead.pheno.field;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks active {@link Cloud} fields and advances them each server tick, dropping those that have
 * dissipated. Single-threaded (spawned and ticked on the server thread), so no synchronization is
 * needed. Cleared on server stop, matching the transient lifetime of cooldowns and resources.
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

    public static void clear() {
        CLOUDS.clear();
    }
}
