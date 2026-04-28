package com.aetherianartificer.townstead.leatherworking;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-villager throttled supply restocking for leatherworkers. Walks the
 * registered {@link LeatherworkerJob} list and lets each job pull one of
 * its own missing inputs from nearby storage, mirroring
 * {@code ButcherToolAcquisitionTicker}.
 */
public final class LeatherworkerSupplyAcquisitionTicker {
    private static final int PULL_INTERVAL_TICKS = 60;

    private static final Map<UUID, Long> NEXT_PULL_TICK = new ConcurrentHashMap<>();

    private LeatherworkerSupplyAcquisitionTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.getVillagerData().getProfession() != VillagerProfession.LEATHERWORKER) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!onWorkShift(villager, level)) return;

        long gameTime = level.getGameTime();
        Long next = NEXT_PULL_TICK.get(villager.getUUID());
        if (next != null && gameTime < next) return;
        NEXT_PULL_TICK.put(villager.getUUID(), gameTime + PULL_INTERVAL_TICKS);

        // Drain finished leather first: it accumulates fastest when racks
        // cure faster than the leatherworker can deposit during COLLECT,
        // and players expect storage to fill up rather than the villager's
        // pack. Done before the per-job pull pass so even pull-throttled
        // ticks still empty the leatherworker.
        if (com.aetherianartificer.townstead.compat.butchery.ButcheryCompat.isLoaded()) {
            com.aetherianartificer.townstead.compat.butchery.SkinRackJob.drainLeatherToStorage(level, villager);
        }

        for (LeatherworkerJob job : LeatherworkerJobs.all()) {
            if (!job.isAvailable()) continue;
            if (job.tryPullMissingSupply(level, villager)) return;
        }
    }

    public static void forget(VillagerEntityMCA villager) {
        NEXT_PULL_TICK.remove(villager.getUUID());
    }

    private static boolean onWorkShift(VillagerEntityMCA villager, ServerLevel level) {
        Brain<?> brain = villager.getBrain();
        long dayTime = level.getDayTime() % 24000L;
        return brain.getSchedule().getActivityAt((int) dayTime) == Activity.WORK;
    }
}
