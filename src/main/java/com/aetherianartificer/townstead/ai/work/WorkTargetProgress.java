package com.aetherianartificer.townstead.ai.work;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

public final class WorkTargetProgress {
    private long currentTargetKey = Long.MIN_VALUE;
    private double lastDistanceSq = Double.MAX_VALUE;
    private int stuckTicks;

    public void reset() {
        currentTargetKey = Long.MIN_VALUE;
        lastDistanceSq = Double.MAX_VALUE;
        stuckTicks = 0;
    }

    public boolean record(@Nullable BlockPos targetPos, double distSq, double epsilon, int stuckTickThreshold) {
        if (targetPos == null) {
            reset();
            return false;
        }
        long key = targetPos.asLong();
        if (currentTargetKey != key) {
            currentTargetKey = key;
            lastDistanceSq = distSq;
            stuckTicks = 0;
            return false;
        }

        if (distSq >= (lastDistanceSq - epsilon)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastDistanceSq = distSq;
        }
        return stuckTicks >= stuckTickThreshold;
    }
}
