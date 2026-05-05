package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Static map: building-type id → spirit-id → point contribution.
 *
 * Populated by {@code CatalogDataLoader} as it scans both the inline
 * {@code townsteadSpirit} field on Townstead-authored building_type JSONs
 * and the companion {@code data/townstead/spirit/<type>.json} files that
 * annotate vanilla MCA building types without clobbering them.
 *
 * Readers ({@link VillageSpiritAggregator}, and the client Spirit page)
 * treat the map as immutable. Writers go through {@link #put} / {@link #clear}
 * during reload.
 */
public final class BuildingSpiritIndex {
    private static final Map<String, Map<String, Integer>> CONTRIBUTIONS = new ConcurrentHashMap<>();

    // Counters track how often callers ask for contributions outside of an
    // explicit prewarm. Lets us tell whether scans are dominating a hot path.
    private static final AtomicInteger DIAG_QUERIES = new AtomicInteger();
    private static final AtomicInteger DIAG_HITS = new AtomicInteger();
    private static final AtomicInteger DIAG_MISSES = new AtomicInteger();
    private static final AtomicLong DIAG_SCAN_NANOS = new AtomicLong();

    private BuildingSpiritIndex() {}

    /** Returns an immutable map of spirit id → points for the given building type, or an empty map. */
    public static Map<String, Integer> contributionsFor(String buildingType) {
        if (buildingType == null) return Map.of();
        DIAG_QUERIES.incrementAndGet();
        Map<String, Integer> existing = CONTRIBUTIONS.get(buildingType);
        if (existing != null) {
            DIAG_HITS.incrementAndGet();
            return existing;
        }
        long t0 = System.nanoTime();
        Map<String, Integer> computed = CONTRIBUTIONS.computeIfAbsent(buildingType,
                BuildingSpiritIndex::scanForContributions);
        long elapsed = System.nanoTime() - t0;
        DIAG_MISSES.incrementAndGet();
        DIAG_SCAN_NANOS.addAndGet(elapsed);
        Townstead.LOGGER.info("[TS-Diag/SpiritIdx] miss buildingType={} entries={} scanUs={} thread={}",
                buildingType, computed.size(), elapsed / 1_000L, Thread.currentThread().getName());
        return computed;
    }

    /**
     * Store a contribution map for a building type. Later calls for the same
     * building type overwrite — the loader is responsible for merge semantics
     * (last writer wins, which matches how other `CatalogDataLoader` fields
     * behave).
     */
    public static void put(String buildingType, Map<String, Integer> contributions) {
        if (buildingType == null || contributions == null || contributions.isEmpty()) return;
        CONTRIBUTIONS.put(buildingType, Map.copyOf(contributions));
    }

    public static void clear() {
        CONTRIBUTIONS.clear();
    }

    public static int size() {
        return CONTRIBUTIONS.size();
    }

    private static final AtomicBoolean ASYNC_PREWARM_RUNNING = new AtomicBoolean(false);

    /**
     * Synchronous prewarm. Use only when the caller can tolerate a multi-ms
     * stall — most callers should use {@link #prewarmAsync} instead.
     */
    public static void prewarm(Iterable<String> buildingTypes) {
        if (buildingTypes == null) return;
        long t0 = System.nanoTime();
        int hitsBefore = DIAG_HITS.get();
        int missesBefore = DIAG_MISSES.get();
        int seen = 0;
        for (String buildingType : buildingTypes) {
            contributionsFor(buildingType);
            seen++;
        }
        long elapsed = System.nanoTime() - t0;
        Townstead.LOGGER.info("[TS-Diag/SpiritIdx] prewarm types={} hits={} misses={} elapsedUs={} cacheSize={} thread={}",
                seen, DIAG_HITS.get() - hitsBefore, DIAG_MISSES.get() - missesBefore,
                elapsed / 1_000L, CONTRIBUTIONS.size(), Thread.currentThread().getName());
    }

    /**
     * Dispatch the spirit-companion JSON scan onto a worker thread. Returns
     * immediately. Subsequent {@link #contributionsFor} calls hit the cache
     * once the worker completes; callers that race ahead just take the
     * synchronous miss path. Concurrent calls coalesce.
     */
    public static void prewarmAsync(Iterable<String> buildingTypes) {
        if (buildingTypes == null) return;
        List<String> snapshot = new ArrayList<>();
        for (String t : buildingTypes) {
            if (t != null && !CONTRIBUTIONS.containsKey(t)) snapshot.add(t);
        }
        if (snapshot.isEmpty()) return;
        if (!ASYNC_PREWARM_RUNNING.compareAndSet(false, true)) {
            Townstead.LOGGER.info("[TS-Diag/SpiritIdx] prewarmAsync skip reason=alreadyRunning pending={}",
                    snapshot.size());
            return;
        }
        Townstead.LOGGER.info("[TS-Diag/SpiritIdx] prewarmAsync dispatch pending={} cacheSize={}",
                snapshot.size(), CONTRIBUTIONS.size());
        ForkJoinPool.commonPool().execute(() -> {
            try {
                long t0 = System.nanoTime();
                int hitsBefore = DIAG_HITS.get();
                int missesBefore = DIAG_MISSES.get();
                for (String buildingType : snapshot) {
                    contributionsFor(buildingType);
                }
                long elapsed = System.nanoTime() - t0;
                Townstead.LOGGER.info("[TS-Diag/SpiritIdx] prewarmAsync done types={} hits={} misses={} elapsedUs={} cacheSize={} thread={}",
                        snapshot.size(), DIAG_HITS.get() - hitsBefore, DIAG_MISSES.get() - missesBefore,
                        elapsed / 1_000L, CONTRIBUTIONS.size(), Thread.currentThread().getName());
            } catch (Throwable t) {
                Townstead.LOGGER.warn("[TS-Diag/SpiritIdx] prewarmAsync failed", t);
            } finally {
                ASYNC_PREWARM_RUNNING.set(false);
            }
        });
    }

    /** Returns a one-shot summary string for periodic logging. */
    public static String diagSummary() {
        return "queries=" + DIAG_QUERIES.get()
                + " hits=" + DIAG_HITS.get()
                + " misses=" + DIAG_MISSES.get()
                + " totalScanMs=" + (DIAG_SCAN_NANOS.get() / 1_000_000L)
                + " cacheSize=" + CONTRIBUTIONS.size();
    }

    private static Map<String, Integer> scanForContributions(String buildingType) {
        Map<String, Integer> scanned = scanClasspathCompanion(buildingType);
        if (scanned.isEmpty()) {
            scanned = scanInlineBuildingType(buildingType);
        }
        return scanned.isEmpty() ? Map.of() : Map.copyOf(scanned);
    }

    private static Map<String, Integer> scanClasspathCompanion(String buildingType) {
        try {
            ClassLoader cl = BuildingSpiritIndex.class.getClassLoader();
            if (cl == null) return Map.of();
            String relPath = "data/townstead/spirit/" + buildingType + ".json";
            Enumeration<URL> urls = cl.getResources(relPath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream();
                        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj == null || !obj.has("townsteadSpirit")) continue;
                    return parseSpiritMap(obj.getAsJsonObject("townsteadSpirit"));
                }
            }
        } catch (Exception ignored) {}
        return Map.of();
    }

    private static Map<String, Integer> scanInlineBuildingType(String buildingType) {
        String relPath = buildingType.startsWith("compat/")
                ? "townstead_compat/building_types/" + buildingType + ".json"
                : "data/mca/building_types/" + buildingType + ".json";
        return scanInlineSpiritJson(relPath);
    }

    private static Map<String, Integer> scanInlineSpiritJson(String relPath) {
        try {
            ClassLoader cl = BuildingSpiritIndex.class.getClassLoader();
            if (cl == null) return Map.of();
            Enumeration<URL> urls = cl.getResources(relPath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream();
                        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj == null || !obj.has("townsteadSpirit")) continue;
                    return parseSpiritMap(obj.getAsJsonObject("townsteadSpirit"));
                }
            }
        } catch (Exception ignored) {}
        return Map.of();
    }

    private static Map<String, Integer> parseSpiritMap(JsonObject spirit) {
        if (spirit == null || spirit.size() == 0) return Map.of();
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : spirit.entrySet()) {
            if (!SpiritRegistry.contains(e.getKey())) continue;
            try {
                int pts = e.getValue().getAsInt();
                if (pts > 0) out.put(e.getKey(), pts);
            } catch (Exception ignored) {}
        }
        return out;
    }
}
