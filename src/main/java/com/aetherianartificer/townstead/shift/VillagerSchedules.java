package com.aetherianartificer.townstead.shift;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.schedule.Activity;

import java.util.UUID;

/**
 * The one place that answers "what is this villager scheduled to do right now". Server side
 * reads the brain schedule, which is authoritative for every source (vanilla default, the
 * {@link TownsteadSchedule} shift array, fatigue overrides). Client side mirrors it from the
 * synced shift store, which falls back to the vanilla default for unshifted villagers.
 */
public final class VillagerSchedules {

    private VillagerSchedules() {}

    /** Server-side: the brain schedule's activity at the current time of day. */
    public static Activity currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    public static boolean isWorking(VillagerEntityMCA villager) {
        return currentActivity(villager) == Activity.WORK;
    }

    /** Client-side mirror from the synced shift store. */
    public static Activity clientMirrorActivity(UUID villagerUuid, long dayTime) {
        int[] shifts = ShiftClientStore.get(villagerUuid);
        int hour = Math.floorMod((int) (dayTime % 24000L) / ShiftData.TICKS_PER_HOUR,
                ShiftData.HOURS_PER_DAY);
        int ordinal = shifts[hour];
        if (ordinal < 0 || ordinal >= ShiftData.ORDINAL_TO_ACTIVITY.length) return Activity.IDLE;
        return ShiftData.ORDINAL_TO_ACTIVITY[ordinal];
    }
}
