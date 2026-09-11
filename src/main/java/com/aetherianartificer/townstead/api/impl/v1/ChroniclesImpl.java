package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.ChroniclesApi;
import com.aetherianartificer.townstead.api.v1.model.ChronicleEventView;
import com.aetherianartificer.townstead.api.v1.model.Cursor;
import com.aetherianartificer.townstead.api.v1.model.KnownStoryView;
import com.aetherianartificer.townstead.api.v1.model.MemoryView;
import com.aetherianartificer.townstead.api.v1.model.Page;
import com.aetherianartificer.townstead.api.v1.model.VillageDigestView;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.net.ChronicleDigestPager;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.store.ChronicleStore;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

final class ChroniclesImpl implements ChroniclesApi {

    /** Trigger type for moments recorded through the API; templates bind to it by key. */
    static final String EXTERNAL_TRIGGER_TYPE = "external";

    private final Beliefs beliefs = new BeliefsImpl();

    @Override
    public boolean archiveAvailable() {
        return Chronicles.archiveAvailable();
    }

    @Override
    public int counter(MinecraftServer server, UUID subject, String key) {
        try {
            return server == null || subject == null || key == null ? 0 : Chronicles.count(server, subject, key);
        } catch (Throwable t) {
            ApiSupport.swallow("chronicles.counter", t);
            return 0;
        }
    }

    @Override
    public Map<String, Integer> counters(MinecraftServer server, UUID subject) {
        try {
            if (server == null || subject == null) return Map.of();
            Map<String, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry : Chronicles.countersFor(server, subject).entrySet()) {
                if (entry.getKey().startsWith("cd:")) continue;
                out.put(entry.getKey(), entry.getValue());
            }
            return Map.copyOf(out);
        } catch (Throwable t) {
            ApiSupport.swallow("chronicles.counters", t);
            return Map.of();
        }
    }

    @Override
    public CompletableFuture<Page<ChronicleEventView>> bySubject(UUID subject, Cursor cursor, int limit) {
        if (subject == null || cursor == null || cursor.isEnd()) return CompletableFuture.completedFuture(Page.empty());
        int size = clamp(limit);
        return Chronicles.bySubject(subject, cursor.value(), size + 1).thenApply(events -> page(events, size));
    }

    @Override
    public CompletableFuture<Page<ChronicleEventView>> byVillage(VillageId village, Cursor cursor, int limit) {
        if (village == null || cursor == null || cursor.isEnd()) return CompletableFuture.completedFuture(Page.empty());
        int size = clamp(limit);
        return Chronicles.byVillage(village.dimension(), village.villageId(), cursor.value(), size + 1)
                .thenApply(events -> page(events, size));
    }

    @Override
    public CompletableFuture<Page<ChronicleEventView>> byDay(long worldDay, Cursor cursor, int limit) {
        return byDayRange(worldDay, worldDay, cursor, limit);
    }

    @Override
    public CompletableFuture<Page<ChronicleEventView>> byDayRange(long fromDay, long toDay, Cursor cursor, int limit) {
        if (cursor == null || cursor.isEnd() || fromDay > toDay) return CompletableFuture.completedFuture(Page.empty());
        int size = clamp(limit);
        return Chronicles.byDayRange(fromDay, toDay, cursor.value(), size + 1).thenApply(events -> page(events, size));
    }

    @Override
    public CompletableFuture<Optional<ChronicleEventView>> byId(long eventId) {
        return Chronicles.byId(eventId).thenApply(event -> event.map(ChroniclesImpl::view))
                .exceptionally(t -> Optional.empty());
    }

    @Override
    public List<ChronicleEventView> recent(Predicate<ChronicleEventView> filter, int limit) {
        List<ChronicleEventView> out = new ArrayList<>();
        try {
            int size = clamp(limit);
            List<ChronicleEvent> snapshot = Chronicles.buffer().snapshot();
            for (int i = snapshot.size() - 1; i >= 0 && out.size() < size; i--) {
                ChronicleEventView view = view(snapshot.get(i));
                if (filter == null || filter.test(view)) out.add(view);
            }
        } catch (Throwable t) {
            ApiSupport.swallow("chronicles.recent", t);
        }
        return out;
    }

    @Override
    public Optional<VillageDigestView> digest(MinecraftServer server, VillageId village, Cursor cursor, int limit) {
        try {
            if (server == null || village == null) return Optional.empty();
            VillageHistory history = ChronicleSavedData.get(server)
                    .historyIfPresent(new VillageKey(village.dimension(), village.villageId()));
            if (history == null) return Optional.empty();
            Cursor at = cursor == null ? Cursor.START : cursor;
            if (at.isEnd()) {
                return Optional.of(new VillageDigestView(village, Page.empty(), history.counts()));
            }
            ChronicleDigestPager.Page paged = ChronicleDigestPager.page(history.entries(), at.value(), clamp(limit));
            List<VillageDigestView.Entry> entries = new ArrayList<>();
            for (VillageHistory.Entry entry : paged.entries()) {
                entries.add(new VillageDigestView.Entry(entry.worldDay(), entry.eventId(), entry.templateId(),
                        entry.headlineLiteral(), entry.headlineLangKey(), entry.params()));
            }
            Page<VillageDigestView.Entry> page = new Page<>(entries, paged.hasMore(),
                    paged.hasMore() ? new Cursor(paged.nextCursor()) : Cursor.END);
            return Optional.of(new VillageDigestView(village, page, history.counts()));
        } catch (Throwable t) {
            ApiSupport.swallow("chronicles.digest", t);
            return Optional.empty();
        }
    }

    @Override
    public OptionalLong emit(ServerLevel level, LivingEntity actor, LivingEntity other, String triggerKey,
                             float magnitude, Map<String, String> params) {
        try {
            if (level == null || actor == null || !externalKey(triggerKey)) return OptionalLong.empty();
            Map<String, String> safeParams = params == null ? Map.of() : Map.copyOf(params);
            Chronicles.addCounter(level.getServer(), actor.getUUID(), triggerKey, 1);
            return ChronicleEmitter.emit(level, new ChronicleEventTemplate.TriggerKey(EXTERNAL_TRIGGER_TYPE, triggerKey),
                    actor, other, magnitude, safeParams);
        } catch (Throwable t) {
            ApiSupport.swallow("chronicles.emit", t);
            return OptionalLong.empty();
        }
    }

    @Override
    public boolean addCounter(MinecraftServer server, UUID subject, String key, int amount) {
        try {
            if (server == null || subject == null || amount <= 0 || !externalKey(key)) return false;
            Chronicles.addCounter(server, subject, key, amount);
            return true;
        } catch (Throwable t) {
            ApiSupport.swallow("chronicles.addCounter", t);
            return false;
        }
    }

    @Override
    public Beliefs beliefs() {
        return beliefs;
    }

    /** A namespaced key that is not Townstead's own and not a cooldown stamp. */
    static boolean externalKey(String key) {
        if (key == null || key.isBlank()) return false;
        int colon = key.indexOf(':');
        if (colon <= 0 || colon == key.length() - 1) return false;
        String namespace = key.substring(0, colon).toLowerCase(Locale.ROOT);
        return !namespace.equals("townstead") && !namespace.equals("cd") && !namespace.equals("minecraft");
    }

    static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_PAGE));
    }

    static Page<ChronicleEventView> page(List<ChronicleEvent> events, int size) {
        return page(events, size, ChroniclesImpl::view, ChronicleEventView::eventId);
    }

    static <S, T> Page<T> page(List<S> overfetched, int size, Function<S, T> convert, Function<T, Long> cursorOf) {
        boolean more = overfetched.size() > size;
        List<T> items = new ArrayList<>();
        for (int i = 0; i < Math.min(size, overfetched.size()); i++) items.add(convert.apply(overfetched.get(i)));
        Cursor next = more && !items.isEmpty() ? new Cursor(cursorOf.apply(items.get(items.size() - 1))) : Cursor.END;
        return new Page<>(items, more, next);
    }

    static ChronicleEventView view(ChronicleEvent event) {
        List<ChronicleEventView.Participant> participants = new ArrayList<>();
        for (Participation participation : event.participations()) {
            ChronicleRef ref = participation.ref();
            participants.add(new ChronicleEventView.Participant(participation.role(),
                    ref.kind().name().toLowerCase(Locale.ROOT), Optional.ofNullable(ref.uuid()), ref.intA(), ref.intB(),
                    ref.str(), ref.displayName()));
        }
        Optional<VillageId> village = event.villageId() == ChronicleEvent.VILLAGE_NONE
                ? Optional.empty() : Optional.of(new VillageId(event.dimension(), event.villageId()));
        return new ChronicleEventView(event.eventId(), event.templateId().toString(), event.worldDay(),
                event.gameTime(), event.dimension(), BlockPos.of(event.packedPos()), village, event.category(),
                event.magnitude(), reach(event.reach()), event.causeEventId(), event.arcId(), event.keep(),
                participants, event.params());
    }

    static String reach(int reach) {
        return switch (reach) {
            case ChronicleEvent.REACH_WITNESSES -> "witnesses";
            case ChronicleEvent.REACH_VILLAGE -> "village";
            case ChronicleEvent.REACH_WORLD -> "world";
            default -> "none";
        };
    }

    private static final class BeliefsImpl implements Beliefs {
        @Override
        public CompletableFuture<Page<KnownStoryView>> knownStories(UUID knower, Cursor cursor, int limit) {
            if (knower == null || cursor == null || cursor.isEnd()) return CompletableFuture.completedFuture(Page.empty());
            int size = clamp(limit);
            return Chronicles.knownStories(knower, cursor.value(), size + 1)
                    .thenApply(stories -> page(stories, size, BeliefsImpl::view, KnownStoryView::accountId));
        }

        @Override
        public List<MemoryView> memories(MinecraftServer server, UUID knower) {
            List<MemoryView> out = new ArrayList<>();
            try {
                if (server == null || knower == null) return out;
                for (VillagerMemory memory : Chronicles.memories(server, knower)) {
                    out.add(new MemoryView(memory.memoryKey(), Optional.ofNullable(memory.otherParty()),
                            memory.firstDay(), memory.lastDay(), memory.count(), memory.strength(), memory.valence(),
                            memory.source(), memory.episodic(), memory.params()));
                }
            } catch (Throwable t) {
                ApiSupport.swallow("chronicles.beliefs.memories", t);
            }
            return out;
        }

        @Override
        public float sentiment(MinecraftServer server, UUID from, UUID toward) {
            try {
                return server == null || from == null || toward == null ? 0f : Chronicles.sentiment(server, from, toward);
            } catch (Throwable t) {
                ApiSupport.swallow("chronicles.beliefs.sentiment", t);
                return 0f;
            }
        }

        static KnownStoryView view(ChronicleStore.KnownStory story) {
            return new KnownStoryView(story.storyEventId(), story.accountId(), story.fidelity(), story.learnedDay(),
                    story.channel(), story.templateId(), story.eventDay(), story.villageId(), story.magnitude(),
                    reach(story.reach()));
        }
    }
}
