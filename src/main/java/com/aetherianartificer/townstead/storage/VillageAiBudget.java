package com.aetherianartificer.townstead.storage;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bounds expensive shared refresh work per scope per tick so large villages
 * consume cached snapshots more often instead of stampeding rebuilds.
 */
public final class VillageAiBudget {
    private static final Map<BudgetKey, BudgetState> STATE = new ConcurrentHashMap<>();
    private static final LongAdder GRANTED = new LongAdder();
    private static final LongAdder THROTTLED = new LongAdder();

    private VillageAiBudget() {}

    public static boolean tryConsume(ServerLevel level, String scope, int budgetPerTick) {
        if (level == null || scope == null || budgetPerTick <= 0) return true;
        BudgetKey key = new BudgetKey(level.dimension().location().toString(), scope);
        long gameTime = level.getGameTime();
        BudgetState state = STATE.computeIfAbsent(key, ignored -> new BudgetState());
        synchronized (state) {
            if (state.tick != gameTime) {
                state.tick = gameTime;
                state.used = 0;
            }
            if (state.used >= budgetPerTick) {
                THROTTLED.increment();
                return false;
            }
            state.used++;
            GRANTED.increment();
            return true;
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(GRANTED.sum(), THROTTLED.sum());
    }

    private record BudgetKey(String dimensionId, String scope) {}

    private static final class BudgetState {
        private long tick = Long.MIN_VALUE;
        private int used;
    }

    public record Snapshot(long granted, long throttled) {}
}
