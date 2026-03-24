package com.aetherianartificer.townstead.ai.work;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class WorkTargetFailures {
    private final Map<Long, Integer> retries = new HashMap<>();
    private final Map<Long, Long> blacklistUntil = new HashMap<>();

    public boolean isBlacklisted(@Nullable BlockPos pos, long gameTime) {
        if (pos == null) return false;
        Long until = blacklistUntil.get(pos.asLong());
        if (until == null) return false;
        if (until <= gameTime) {
            blacklistUntil.remove(pos.asLong());
            return false;
        }
        return true;
    }

    public boolean recordFailure(BlockPos pos, long gameTime, int maxRetries, int blacklistTicks) {
        long key = pos.asLong();
        int retry = retries.getOrDefault(key, 0) + 1;
        if (retry >= maxRetries) {
            retries.remove(key);
            blacklistUntil.put(key, gameTime + blacklistTicks);
            return true;
        }
        retries.put(key, retry);
        return false;
    }

    public void clear(@Nullable BlockPos pos) {
        if (pos == null) return;
        long key = pos.asLong();
        retries.remove(key);
        blacklistUntil.remove(key);
    }

    public void reset() {
        retries.clear();
        blacklistUntil.clear();
    }
}
