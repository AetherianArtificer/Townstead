package com.aetherianartificer.townstead.fatigue;

import net.minecraft.world.entity.schedule.Activity;

public record RestContext(
        boolean fatigueEnabled,
        Activity scheduleActivity,
        int fatigue,
        boolean collapsed,
        boolean sleeping,
        boolean hasAttackTarget,
        boolean hurtByMob,
        boolean hasHome,
        boolean hasValidSleepingBed,
        boolean guardRole
) {
    public boolean isScheduledRest() {
        return scheduleActivity == Activity.REST;
    }

    public boolean isDrowsyOrWorse() {
        return fatigueEnabled && fatigue >= FatigueData.DROWSY_THRESHOLD;
    }
}
