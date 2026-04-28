package com.aetherianartificer.townstead.leatherworking;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Surfaces missing-supply chat from the leatherworker's active job. Mirrors
 * {@code ButcheryComplaintsTicker} but lives next to the profession's other
 * code so the dependency is one-way: tickers may consult jobs, jobs never
 * import complaint code.
 *
 * <p>Each registered {@link LeatherworkerJob} contributes its own dialogue
 * key via {@link LeatherworkerJob#missingSupplyDialogueKey}. Throttled to
 * one complaint per villager per {@value #COMPLAINT_INTERVAL_TICKS} ticks.
 */
public final class LeatherworkerComplaintsTicker {
    static final long COMPLAINT_INTERVAL_TICKS = 1200L; // 1 in-game minute
    private static final double PLAYER_RANGE = 24.0;
    static final String LAST_COMPLAINT_KEY = "townstead_lastLeatherworkerComplaint";

    private LeatherworkerComplaintsTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.getVillagerData().getProfession() != VillagerProfession.LEATHERWORKER) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!onWorkShift(villager, level)) return;
        if (level.getNearestPlayer(villager, PLAYER_RANGE) == null) return;

        long gameTime = level.getGameTime();
        if (onThrottle(villager, gameTime)) return;

        String reasonKey = pickReason(level, villager);
        if (reasonKey == null) return;

        String variantKey = reasonKey + "/" + (1 + level.random.nextInt(3));
        villager.sendChatToAllAround(variantKey);
        markComplained(villager, gameTime);
    }

    private static String pickReason(ServerLevel level, VillagerEntityMCA villager) {
        for (LeatherworkerJob job : LeatherworkerJobs.all()) {
            if (!job.isAvailable()) continue;
            String key = job.missingSupplyDialogueKey(level, villager);
            if (key != null) return key;
        }
        return null;
    }

    private static boolean onWorkShift(VillagerEntityMCA villager, ServerLevel level) {
        Brain<?> brain = villager.getBrain();
        long dayTime = level.getDayTime() % 24000L;
        return brain.getSchedule().getActivityAt((int) dayTime) == Activity.WORK;
    }

    private static boolean onThrottle(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(LAST_COMPLAINT_KEY);
        return gameTime - last < COMPLAINT_INTERVAL_TICKS;
    }

    private static void markComplained(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        data.putLong(LAST_COMPLAINT_KEY, gameTime);
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        data.putLong(LAST_COMPLAINT_KEY, gameTime);
        villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }
}
