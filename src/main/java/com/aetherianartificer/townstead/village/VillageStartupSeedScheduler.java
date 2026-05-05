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
    private static long DIAG_ENQUEUED_AT_NANOS = 0L;
    private static int DIAG_QUEUED_TOTAL = 0;
    private static int DIAG_PROCESSED_TOTAL = 0;

    private VillageStartupSeedScheduler() {}

    public static void enqueue(MinecraftServer server) {
        QUEUE.clear();
        DIAG_PROCESSED_TOTAL = 0;
        if (server == null) return;
        long t0 = System.nanoTime();
        int count = 0;
        int dimensions = 0;
        for (ServerLevel level : server.getAllLevels()) {
            VillageManager manager = VillageManager.get(level);
            int dimCount = 0;
            for (Village village : manager) {
                QUEUE.add(new Job(level, village));
                count++;
                dimCount++;
            }
            if (dimCount > 0) {
                dimensions++;
                Townstead.LOGGER.info("[TS-Diag/Seed] enqueue dimension={} villages={}",
                        level.dimension().location(), dimCount);
            }
        }
        DIAG_QUEUED_TOTAL = count;
        DIAG_ENQUEUED_AT_NANOS = System.nanoTime();
        Townstead.LOGGER.info("[TS-Diag/Seed] enqueue total villages={} dimensions={} setupUs={}",
                count, dimensions, (DIAG_ENQUEUED_AT_NANOS - t0) / 1_000L);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || QUEUE.isEmpty()) return;
        long tickStart = System.nanoTime();
        long deadline = tickStart + MAX_NANOS_PER_TICK;
        int processed = 0;
        while (!QUEUE.isEmpty() && processed < MAX_VILLAGES_PER_TICK && System.nanoTime() < deadline) {
            Job job = QUEUE.poll();
            seed(job.level, job.village);
            processed++;
            DIAG_PROCESSED_TOTAL++;
        }
        long elapsed = System.nanoTime() - tickStart;
        Townstead.LOGGER.info("[TS-Diag/Seed] tick processed={} totalSeeded={}/{} queueLeft={} elapsedUs={}",
                processed, DIAG_PROCESSED_TOTAL, DIAG_QUEUED_TOTAL, QUEUE.size(), elapsed / 1_000L);
        if (QUEUE.isEmpty() && DIAG_QUEUED_TOTAL > 0) {
            long totalMs = (System.nanoTime() - DIAG_ENQUEUED_AT_NANOS) / 1_000_000L;
            Townstead.LOGGER.info("[TS-Diag/Seed] drain complete villages={} totalElapsedMs={}",
                    DIAG_QUEUED_TOTAL, totalMs);
            DIAG_QUEUED_TOTAL = 0;
        }
    }

    public static void clear() {
        QUEUE.clear();
        DIAG_QUEUED_TOTAL = 0;
        DIAG_PROCESSED_TOTAL = 0;
    }

    private static void seed(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        long t0 = System.nanoTime();
        DockDuplicatePurger.purgeAll(village);
        long t1 = System.nanoTime();
        DockLocationIndex.rebuildVillage(level, village);
        long t2 = System.nanoTime();
        BuildingRecognitionTracker.seed(level, village);
        long t3 = System.nanoTime();
        SpiritReconciler.seed(level, village);
        long t4 = System.nanoTime();
        Townstead.LOGGER.info("[TS-Diag/Seed] village={} buildings={} purgeUs={} dockIdxUs={} recogUs={} spiritUs={} totalUs={}",
                village.getId(), village.getBuildings().size(),
                (t1 - t0) / 1_000L, (t2 - t1) / 1_000L, (t3 - t2) / 1_000L,
                (t4 - t3) / 1_000L, (t4 - t0) / 1_000L);
    }

    private record Job(ServerLevel level, Village village) {}
}
