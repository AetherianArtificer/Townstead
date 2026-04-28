package com.aetherianartificer.townstead.leatherworking;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
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

        for (LeatherworkerJob job : LeatherworkerJobs.all()) {
            if (!job.isAvailable()) continue;
            boolean pulled = job.tryPullMissingSupply(level, villager);
            if (debugEnabled()) {
                Townstead.LOGGER.info("[LeatherworkerSupply] t={} villager={} job={} pulled={}",
                        level.getGameTime(), villager.getStringUUID(),
                        job.getClass().getSimpleName(), pulled);
            }
            if (pulled) return;
        }
    }

    private static boolean debugEnabled() {
        try {
            return TownsteadConfig.DEBUG_VILLAGER_AI.get();
        } catch (Throwable ignored) {
            return false;
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
