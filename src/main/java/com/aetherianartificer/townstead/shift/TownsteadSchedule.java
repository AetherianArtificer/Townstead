package com.aetherianartificer.townstead.shift;

import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;

/**
 * A custom Schedule that reads activity assignments from a per-villager
 * 24-slot shift array instead of the vanilla timeline system.
 */
public class TownsteadSchedule extends Schedule {
    private final int[] shifts;

    public TownsteadSchedule(int[] shifts) {
        this.shifts = shifts.clone();
    }

    @Override
    public Activity getActivityAt(int timeOfDay) {
        int hour = Math.floorMod(timeOfDay / ShiftData.TICKS_PER_HOUR, ShiftData.HOURS_PER_DAY);
        int ord = shifts[hour];
        if (ord < 0 || ord >= ShiftData.ORDINAL_TO_ACTIVITY.length) {
            return Activity.IDLE;
        }
        return ShiftData.ORDINAL_TO_ACTIVITY[ord];
    }
}
