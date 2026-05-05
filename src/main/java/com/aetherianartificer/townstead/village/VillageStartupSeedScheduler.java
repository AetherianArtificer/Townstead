package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.dock.DockDuplicatePurger;
import com.aetherianartificer.townstead.dock.DockLocationIndex;
import com.aetherianartificer.townstead.recognition.BuildingRecognitionTracker;
import com.aetherianartificer.townstead.spirit.SpiritReconciler;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Defers village startup baselining over ticks instead of doing one large pass
 * during ServerStarted.
 */
public final class VillageStartupSeedScheduler {
    private static final long MAX_NANOS_PER_TICK = 2_000_000L;
    private static final int MAX_VILLAGES_PER_TICK = 1;
    private static final Queue<Job> QUEUE = new ArrayDeque<>();

    private VillageStartupSeedScheduler() {}

    public static void enqueue(MinecraftServer server) {
        QUEUE.clear();
        if (server == null) return;
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            for (Village village : manager) {
                QUEUE.add(new Job(level, village));
                count++;
            }
        }
        if (count > 0) {
            Townstead.LOGGER.debug("[StartupSeed] queued {} villages for deferred seeding", count);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || QUEUE.isEmpty()) return;
        long deadline = System.nanoTime() + MAX_NANOS_PER_TICK;
        int processed = 0;
        while (!QUEUE.isEmpty() && processed < MAX_VILLAGES_PER_TICK && System.nanoTime() < deadline) {
            Job job = QUEUE.poll();
            seed(job.level, job.village);
            processed++;
        }
    }

    public static void clear() {
        QUEUE.clear();
    }

    private static void seed(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        DockDuplicatePurger.purgeAll(village);
        DockLocationIndex.rebuildVillage(level, village);
        BuildingRecognitionTracker.seed(level, village);
        SpiritReconciler.seed(level, village);
    }

    private record Job(ServerLevel level, Village village) {}
}
