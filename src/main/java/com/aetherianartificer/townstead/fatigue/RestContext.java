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
        boolean guardRole,
        boolean restOverrideActive
) {
    public boolean isScheduledRest() {
        return scheduleActivity == Activity.REST;
    }

    public boolean isDrowsyOrWorse() {
        return fatigueEnabled && fatigue >= FatigueData.DROWSY_THRESHOLD;
    }

    /**
     * Whether the villager still needs rest before waking.
     * During fatigue-forced rest (override active), requires full recovery
     * to prevent oscillation — especially with time acceleration mods.
     * During normal scheduled rest, just checks drowsy threshold.
     */
    public boolean needsMoreRest() {
        if (!fatigueEnabled) return false;
        if (restOverrideActive) return fatigue > 0;
        return fatigue >= FatigueData.DROWSY_THRESHOLD;
    }
}
