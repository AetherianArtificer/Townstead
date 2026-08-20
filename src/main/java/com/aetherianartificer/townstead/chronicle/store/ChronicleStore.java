package com.aetherianartificer.townstead.chronicle.store;

import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The chronicle archive: ground-truth events, participations, and arcs.
 * Writes are fire-and-forget (queued off-thread); reads are async. The tick
 * thread must never block on this interface.
 *
 * <p>This is also the storage seam: the default implementation is backed by
 * a small persistent key/value store, while another local or remote backend
 * can slot in behind it.</p>
 */
public interface ChronicleStore extends AutoCloseable {

    /** Queues an event (id already assigned) for durable append. */
    void appendEvent(ChronicleEvent event);

    /** Queues an arc insert-or-update. */
    void appendArc(Arc arc);

    /** Queues an account (belief-tier row) for durable append. */
    void appendAccount(com.aetherianartificer.townstead.chronicle.model.Account account);

    CompletableFuture<List<com.aetherianartificer.townstead.chronicle.model.Account>> accountsByKnower(
            UUID knower, int limit);

    CompletableFuture<List<com.aetherianartificer.townstead.chronicle.model.Account>> accountsByStory(
            long storyEventId, int limit);

    /** Known-story cache seed: accounts joined with their event's spread-relevant columns. */
    CompletableFuture<List<KnownStory>> knownStories(UUID knower, int limit);

    record KnownStory(long storyEventId, long accountId, float fidelity, long learnedDay,
                      String templateId, long eventDay, int villageId, float magnitude,
                      int reach, String overlayJson) {}

    CompletableFuture<List<ChronicleEvent>> bySubject(UUID subject, long beforeEventId, int limit);

    CompletableFuture<List<ChronicleEvent>> byVillage(ResourceLocation dimension, int villageId,
                                                      long beforeEventId, int limit);

    CompletableFuture<List<ChronicleEvent>> byDay(long worldDay, int limit);

    CompletableFuture<List<ChronicleEvent>> byArc(long arcId, int limit);

    CompletableFuture<Optional<ChronicleEvent>> byId(long eventId);

    /** Requests a durable commit so world-folder saves include current archive data. */
    void requestCheckpoint();

    /** Drains the write queue before shutdown; returns false on timeout. */
    boolean flushBlocking(long timeoutMs);

    Stats stats();

    /** Highest durable ids observed when the archive opened. */
    IdMaxima idMaxima();

    @Override
    void close();

    record Stats(boolean available, long queued, long written, long dropped, long dbFileBytes) {}
    record IdMaxima(long eventId, long arcId, long accountId) {
        public static final IdMaxima NONE = new IdMaxima(0L, 0L, 0L);
    }
}
