package com.aetherianartificer.townstead.fatigue;

public record RestDecision(
        SleepReason reason,
        SleepBlockReason blockReason,
        boolean shouldSeekBed,
        boolean shouldOverrideScheduleToRest,
        boolean shouldWake,
        boolean shouldHoldGuardAtRest
) {
    public boolean isBlocked() {
        return blockReason != SleepBlockReason.NONE;
    }
}
