package com.aetherianartificer.townstead.chronicle;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.arc.ArcManager;
import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.store.ChronicleDatabase;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.store.ChronicleStore;
import com.aetherianartificer.townstead.chronicle.store.RecentEventsBuffer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point for the Chronicles system.
 *
 * <p>Truth firewall: {@code record}/{@code count}/event queries are the truth
 * side (mechanics may read them); memories and sentiment are the belief side
 * (populated on learn, Phase 4) and must never feed mechanical unlocks.</p>
 */
public final class Chronicles {

    private static volatile @Nullable ChronicleStore store;
    private static final RecentEventsBuffer BUFFER = new RecentEventsBuffer();

    private Chronicles() {}

    // ---- lifecycle (wired in Townstead server events) ----

    public static void onServerStarted(MinecraftServer server) {
        Path dbFile = server.getWorldPath(LevelResource.ROOT)
                .resolve("data").resolve("townstead_chronicles.db");
        ChronicleStore opened = ChronicleDatabase.open(dbFile);
        store = opened;
        com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache.setLoader(
                opened.stats().available()
                        ? knower -> opened.knownStories(knower, 96)
                        : null);
    }

    public static void onServerStopping(MinecraftServer server) {
        ChronicleStore s = store;
        store = null;
        BUFFER.clear();
        ArcManager.clearAll();
        com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache.setLoader(null);
        com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache.clearAll();
        if (s != null) s.close();
    }

    /** Once per calendar day, from the WorldCalendarTicker rollover. */
    public static void onDayRollover(MinecraftServer server) {
        ChronicleSavedData.get(server).decayDaily();
        ChronicleStore s = store;
        if (s != null) s.requestCheckpoint();
    }

    public static boolean archiveAvailable() {
        ChronicleStore s = store;
        return s != null && s.stats().available();
    }

    // ---- truth side ----

    /**
     * Records a ground-truth event. Assigns the id, updates the village digest
     * counts, buffers it for aggregate derivation, and queues the durable
     * append. Synchronous and cheap; safe on the tick thread.
     */
    public static long record(MinecraftServer server, ChronicleEvent draft) {
        ChronicleSavedData data = ChronicleSavedData.get(server);
        ChronicleEvent event = draft.withId(data.assignEventId());
        if (event.villageId() != ChronicleEvent.VILLAGE_NONE && !event.category().isEmpty()) {
            data.historyFor(new VillageKey(event.dimension(), event.villageId()))
                    .bumpCount(event.category());
            data.setDirty();
        }
        BUFFER.add(event);
        ChronicleStore s = store;
        if (s != null) s.appendEvent(event);
        return event.eventId();
    }

    /** Adds a notable entry to a village's public digest (server-resolved headline). */
    public static void recordDigestEntry(MinecraftServer server, VillageKey village,
                                         VillageHistory.Entry entry) {
        ChronicleSavedData data = ChronicleSavedData.get(server);
        data.historyFor(village).addEntry(entry);
        data.setDirty();
    }

    public static void addCounter(MinecraftServer server, UUID subject, String key, int amount) {
        ChronicleSavedData.get(server).addCounter(subject, key, amount);
    }

    /** Exact, compaction-proof event counter — the Careers unlock contract. */
    public static int count(MinecraftServer server, UUID subject, String key) {
        return ChronicleSavedData.get(server).counter(subject, key);
    }

    public static void appendArc(MinecraftServer server, Arc arc) {
        ChronicleStore s = store;
        if (s != null) s.appendArc(arc);
    }

    public static void appendAccount(MinecraftServer server,
                                     com.aetherianartificer.townstead.chronicle.model.Account account) {
        ChronicleStore s = store;
        if (s != null) s.appendAccount(account);
    }

    // ---- truth-side queries (async, archive-backed) ----

    public static CompletableFuture<List<ChronicleEvent>> bySubject(UUID subject, long beforeEventId, int limit) {
        ChronicleStore s = store;
        return s == null ? CompletableFuture.completedFuture(List.of())
                : s.bySubject(subject, beforeEventId, limit).exceptionally(t -> List.of());
    }

    public static CompletableFuture<List<ChronicleEvent>> byVillage(ResourceLocation dimension, int villageId,
                                                                    long beforeEventId, int limit) {
        ChronicleStore s = store;
        return s == null ? CompletableFuture.completedFuture(List.of())
                : s.byVillage(dimension, villageId, beforeEventId, limit).exceptionally(t -> List.of());
    }

    public static CompletableFuture<List<ChronicleEvent>> byDay(long worldDay, int limit) {
        ChronicleStore s = store;
        return s == null ? CompletableFuture.completedFuture(List.of())
                : s.byDay(worldDay, limit).exceptionally(t -> List.of());
    }

    public static CompletableFuture<List<ChronicleEvent>> byArc(long arcId, int limit) {
        ChronicleStore s = store;
        return s == null ? CompletableFuture.completedFuture(List.of())
                : s.byArc(arcId, limit).exceptionally(t -> List.of());
    }

    public static CompletableFuture<Optional<ChronicleEvent>> byId(long eventId) {
        ChronicleStore s = store;
        return s == null ? CompletableFuture.completedFuture(Optional.empty()) : s.byId(eventId);
    }

    public static CompletableFuture<List<com.aetherianartificer.townstead.chronicle.model.Account>> accountsByKnower(
            UUID knower, int limit) {
        ChronicleStore s = store;
        return s == null ? CompletableFuture.completedFuture(List.of())
                : s.accountsByKnower(knower, limit).exceptionally(t -> List.of());
    }

    // ---- belief side (hot tier; populated by the Phase 4 knowledge engine) ----

    public static List<VillagerMemory> memories(MinecraftServer server, UUID knower) {
        return ChronicleSavedData.get(server).memoriesFor(knower);
    }

    public static VillageHistory history(MinecraftServer server, VillageKey village) {
        return ChronicleSavedData.get(server).historyFor(village);
    }

    public static float sentiment(MinecraftServer server, UUID from, UUID toward) {
        return ChronicleSavedData.get(server).sentiment(from, toward);
    }

    // ---- diagnostics ----

    public static RecentEventsBuffer buffer() {
        return BUFFER;
    }

    public static ChronicleStore.Stats storeStats() {
        ChronicleStore s = store;
        return s == null ? new ChronicleStore.Stats(false, 0, 0, 0, 0) : s.stats();
    }

    public static Map<String, Integer> countersFor(MinecraftServer server, UUID subject) {
        return ChronicleSavedData.get(server).countersFor(subject);
    }

    public static void logStartupState() {
        Townstead.LOGGER.debug("[Chronicles] archive available: {}", archiveAvailable());
    }
}
