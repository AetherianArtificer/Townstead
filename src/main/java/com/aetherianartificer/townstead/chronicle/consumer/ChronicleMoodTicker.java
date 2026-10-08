package com.aetherianartificer.townstead.chronicle.consumer;

import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Converges MCA mood toward the belief-driven target: accumulated on-learn
 * impacts (hot tier, decaying daily). Only the delta between target and the
 * stored applied amount ever touches {@code modifyMoodValue}, so decay and
 * future discredits reverse cleanly — no permanent mood inflation.
 */
public final class ChronicleMoodTicker {

    private static final int STRIDE_TICKS = 100;

    private ChronicleMoodTicker() {}

    public static void tick(VillagerEntityMCA villager, long gameTime) {
        if ((gameTime + villager.getId()) % STRIDE_TICKS != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;

        ChronicleSavedData data = ChronicleSavedData.get(level.getServer());
        UUID uuid = villager.getUUID();
        float target = data.moodTarget(uuid);
        float applied = data.appliedMoodDrift(uuid);
        float diff = target - applied;
        if (diff < 1f && diff > -1f) return;

        int delta = diff > 0 ? (int) Math.floor(diff) : (int) Math.ceil(diff);
        villager.getVillagerBrain().modifyMoodValue(delta);
        data.setAppliedMoodDrift(uuid, applied + delta);
    }
}
