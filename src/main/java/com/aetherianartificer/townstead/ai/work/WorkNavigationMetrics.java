package com.aetherianartificer.townstead.ai.work;

import java.util.concurrent.atomic.LongAdder;

public final class WorkNavigationMetrics {
    private static final LongAdder SNAPSHOT_REBUILDS = new LongAdder();
    private static final LongAdder PATH_ATTEMPTS = new LongAdder();
    private static final LongAdder PATH_SUCCESSES = new LongAdder();
    private static final LongAdder PATH_FAILURES = new LongAdder();

    private WorkNavigationMetrics() {}

    public static void recordSnapshotRebuild() {
        SNAPSHOT_REBUILDS.increment();
    }

    public static void recordPathAttempt(boolean success) {
        PATH_ATTEMPTS.increment();
        if (success) {
            PATH_SUCCESSES.increment();
        } else {
            PATH_FAILURES.increment();
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                SNAPSHOT_REBUILDS.sum(),
                PATH_ATTEMPTS.sum(),
                PATH_SUCCESSES.sum(),
                PATH_FAILURES.sum()
        );
    }

    public record Snapshot(long snapshotRebuilds, long pathAttempts, long pathSuccesses, long pathFailures) {}
}
