package com.aetherianartificer.townstead.chronicle.knowledge;

import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.store.ChronicleStore;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Per-knower in-memory index of recently-known notable stories. Spread logic
 * (gossip, digest absorption, Share News) reads only this — never the archive
 * on the tick thread. Loaded lazily off-thread; a knower is "not ready" until
 * its archive seed lands, so spread quietly skips them for a few ticks.
 */
public final class KnownStoriesCache {

    private static final int MAX_ENTRIES_PER_KNOWER = 96;
    private static final int LOAD_LIMIT = 96;

    /** Everything spread needs, denormalized so no archive read is required. */
    public static final class Entry {
        public final long storyEventId;
        public final long accountId;
        public final float fidelity;
        public final long learnedDay;
        public final ResourceLocation templateId;
        public final long eventDay;
        public final int villageId;
        public final float magnitude;
        public final int reach;
        public final DistortionOverlay overlay;

        public Entry(long storyEventId, long accountId, float fidelity, long learnedDay,
                     ResourceLocation templateId, long eventDay, int villageId,
                     float magnitude, int reach, DistortionOverlay overlay) {
            this.storyEventId = storyEventId;
            this.accountId = accountId;
            this.fidelity = fidelity;
            this.learnedDay = learnedDay;
            this.templateId = templateId;
            this.eventDay = eventDay;
            this.villageId = villageId;
            this.magnitude = magnitude;
            this.reach = reach;
            this.overlay = overlay;
        }
    }

    private static final class Known {
        volatile boolean ready;
        final Map<Long, Entry> byStory = new HashMap<>();
    }

    private static final Map<UUID, Known> CACHE = new ConcurrentHashMap<>();
    private static volatile @Nullable Function<UUID, java.util.concurrent.CompletableFuture<List<ChronicleStore.KnownStory>>> loader;

    private KnownStoriesCache() {}

    /** Wired by Chronicles on server start; null when the archive is unavailable. */
    public static void setLoader(@Nullable Function<UUID, java.util.concurrent.CompletableFuture<List<ChronicleStore.KnownStory>>> storyLoader) {
        loader = storyLoader;
    }

    /** True when the knower's seed has loaded (kicks the load on first ask). */
    public static boolean ready(UUID knower) {
        Known known = CACHE.get(knower);
        if (known != null) return known.ready;
        Known fresh = new Known();
        if (CACHE.putIfAbsent(knower, fresh) != null) return false;
        var load = loader;
        if (load == null) {
            fresh.ready = true;   // no archive: start empty but usable
            return true;
        }
        load.apply(knower).whenComplete((stories, error) -> {
            if (stories != null) {
                synchronized (fresh) {
                    for (ChronicleStore.KnownStory story : stories) {
                        fresh.byStory.putIfAbsent(story.storyEventId(), new Entry(
                                story.storyEventId(), story.accountId(), story.fidelity(),
                                story.learnedDay(), parseRl(story.templateId()), story.eventDay(),
                                story.villageId(), story.magnitude(), story.reach(),
                                DistortionOverlay.fromJson(story.overlayJson())));
                    }
                }
            }
            fresh.ready = true;
        });
        return false;
    }

    public static boolean knows(UUID knower, long storyEventId) {
        Known known = CACHE.get(knower);
        if (known == null || !known.ready) return true; // unknown state: pretend known, never double-teach
        synchronized (known) {
            return known.byStory.containsKey(storyEventId);
        }
    }

    public static void add(UUID knower, Entry entry) {
        Known known = CACHE.computeIfAbsent(knower, ignored -> {
            Known fresh = new Known();
            fresh.ready = true;
            return fresh;
        });
        synchronized (known) {
            known.byStory.put(entry.storyEventId, entry);
            if (known.byStory.size() > MAX_ENTRIES_PER_KNOWER) {
                long oldest = Long.MAX_VALUE;
                long oldestId = ChronicleEvent.NONE;
                for (Entry e : known.byStory.values()) {
                    if (e.learnedDay < oldest) {
                        oldest = e.learnedDay;
                        oldestId = e.storyEventId;
                    }
                }
                if (oldestId != ChronicleEvent.NONE) known.byStory.remove(oldestId);
            }
        }
    }

    /** Snapshot of the knower's entries (empty until ready). */
    public static List<Entry> entries(UUID knower) {
        Known known = CACHE.get(knower);
        if (known == null || !known.ready) return List.of();
        synchronized (known) {
            return new ArrayList<>(known.byStory.values());
        }
    }

    public static int size() {
        return CACHE.size();
    }

    public static void clearAll() {
        CACHE.clear();
    }

    private static ResourceLocation parseRl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }

    static int loadLimit() {
        return LOAD_LIMIT;
    }
}
