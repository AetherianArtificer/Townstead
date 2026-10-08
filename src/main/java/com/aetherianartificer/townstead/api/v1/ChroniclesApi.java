package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.ChronicleEventView;
import com.aetherianartificer.townstead.api.v1.model.Cursor;
import com.aetherianartificer.townstead.api.v1.model.KnownStoryView;
import com.aetherianartificer.townstead.api.v1.model.MemoryView;
import com.aetherianartificer.townstead.api.v1.model.Page;
import com.aetherianartificer.townstead.api.v1.model.VillageDigestView;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * The Chronicles archive: what happened (truth) and what villagers believe happened (belief).
 *
 * <p>Methods on this interface read truth and may drive mechanics. Methods on {@link Beliefs}
 * read belief and must only ever drive narrative: a distorted account can never change what
 * happened or what anyone earned. Archive reads complete on a Townstead reader thread; hop back
 * with {@code server.execute} before touching game state.
 */
public interface ChroniclesApi {

    /** Hard page ceiling for every paged read. */
    int MAX_PAGE = 256;

    boolean archiveAvailable();

    /** Exact, compaction-proof counter for one subject and key. */
    int counter(MinecraftServer server, UUID subject, String key);

    /** Every counter for a subject. Internal cooldown stamps are not included. */
    Map<String, Integer> counters(MinecraftServer server, UUID subject);

    /** Newest first. */
    CompletableFuture<Page<ChronicleEventView>> bySubject(UUID subject, Cursor cursor, int limit);

    /** Newest first. */
    CompletableFuture<Page<ChronicleEventView>> byVillage(VillageId village, Cursor cursor, int limit);

    /** Newest first, within one calendar day. */
    CompletableFuture<Page<ChronicleEventView>> byDay(long worldDay, Cursor cursor, int limit);

    /** Newest first, {@code fromDay} to {@code toDay} inclusive. */
    CompletableFuture<Page<ChronicleEventView>> byDayRange(long fromDay, long toDay, Cursor cursor, int limit);

    CompletableFuture<Optional<ChronicleEventView>> byId(long eventId);

    /** Synchronous scan of the most recent events still in memory, newest first. */
    List<ChronicleEventView> recent(Predicate<ChronicleEventView> filter, int limit);

    /** A village's public digest: headlines and per-category counts. */
    Optional<VillageDigestView> digest(MinecraftServer server, VillageId village, Cursor cursor, int limit);

    /**
     * Records a moment on behalf of another mod. The trigger key must carry the caller's own
     * namespace; templates for it live in the caller's datapack under trigger type
     * {@code external}. Returns the event id when a template produced a story.
     */
    OptionalLong emit(ServerLevel level, LivingEntity actor, LivingEntity other, String triggerKey,
                      float magnitude, Map<String, String> params);

    /** Increments a counter under the caller's own namespace. {@code townstead:} keys are refused. */
    boolean addCounter(MinecraftServer server, UUID subject, String key, int amount);

    Beliefs beliefs();

    /** Belief-side reads. Narrative only; never gate a mechanic on these. */
    interface Beliefs {

        /** Stories a knower has heard, newest first, with how faithfully they heard them. */
        CompletableFuture<Page<KnownStoryView>> knownStories(UUID knower, Cursor cursor, int limit);

        List<MemoryView> memories(MinecraftServer server, UUID knower);

        /** Directed feeling of one villager toward another, zero when none. */
        float sentiment(MinecraftServer server, UUID from, UUID toward);
    }
}
