package com.aetherianartificer.townstead.work.producer;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Keeps the ordinary producer behavior alive after a shift boundary only long enough to make its
 * already-committed physical station safe. It does not cook, collect, path, or understand a mod;
 * the existing WORK behavior remains the sole owner of that lifecycle. The live station session
 * is the event/situation predicate: once collection releases it, the scheduled activity resumes.
 */
public final class FinishCommittedProductionTask extends Behavior<VillagerEntityMCA> {
    private static final int LEASE_TICKS = 1200;

    public FinishCommittedProductionTask() {
        super(ImmutableMap.of(), LEASE_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        return shouldWindDown(level, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return shouldWindDown(level, villager);
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        keepWorkActive(villager);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // The brain reapplies its schedule periodically. Reassert WORK while—and only while—the
        // committed session still exists so that producer state can reach collection.
        keepWorkActive(villager);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (ProducerStationSessions.hasOwnedFinishingSession(level, villager.getUUID())) return;
        Activity scheduled = scheduled(villager);
        if (scheduled != Activity.WORK) villager.getBrain().setActiveActivityIfPossible(scheduled);
    }

    private static boolean shouldWindDown(ServerLevel level, VillagerEntityMCA villager) {
        if (scheduled(villager) == Activity.WORK) return false;
        if (villager.getVillagerBrain().isPanicking() || villager.getLastHurtByMob() != null) return false;
        return ProducerStationSessions.hasOwnedFinishingSession(level, villager.getUUID());
    }

    private static Activity scheduled(VillagerEntityMCA villager) {
        return com.aetherianartificer.townstead.shift.VillagerSchedules.currentActivity(villager);
    }

    private static void keepWorkActive(VillagerEntityMCA villager) {
        villager.getBrain().setActiveActivityIfPossible(Activity.WORK);
    }
}
