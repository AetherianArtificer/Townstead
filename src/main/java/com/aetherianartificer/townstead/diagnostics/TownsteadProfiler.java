package com.aetherianartificer.townstead.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Low-overhead runtime profiler for server-thread Townstead slices.
 * Disabled by default; when enabled it aggregates counts and elapsed nanos.
 */
public final class TownsteadProfiler {
    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();
    private static volatile boolean enabled;

    private TownsteadProfiler() {}

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void clear() {
        COUNTERS.clear();
    }

    public static void record(String name, long nanos) {
        if (!enabled || nanos < 0L) return;
        Counter counter = COUNTERS.computeIfAbsent(name, ignored -> new Counter());
        counter.calls.increment();
        counter.nanos.add(nanos);
    }

    public static <T extends Throwable> void time(String name, ThrowingRunnable<T> runnable) throws T {
        if (!enabled) {
            runnable.run();
            return;
        }
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            record(name, System.nanoTime() - start);
        }
    }

    public static Snapshot snapshot() {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<String, Counter> entry : COUNTERS.entrySet()) {
            long calls = entry.getValue().calls.sum();
            long nanos = entry.getValue().nanos.sum();
            rows.add(new Row(entry.getKey(), calls, nanos));
        }
        rows.sort(Comparator.comparingLong(Row::nanos).reversed());
        return new Snapshot(enabled, List.copyOf(rows));
    }

    @FunctionalInterface
    public interface ThrowingRunnable<T extends Throwable> {
        void run() throws T;
    }

    private static final class Counter {
        final LongAdder calls = new LongAdder();
        final LongAdder nanos = new LongAdder();
    }

    public record Row(String name, long calls, long nanos) {
        public double millis() {
            return nanos / 1_000_000.0;
        }

        public double microsPerCall() {
            return calls <= 0L ? 0.0 : nanos / 1_000.0 / calls;
        }
    }

    public record Snapshot(boolean enabled, List<Row> rows) {}
}
