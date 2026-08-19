package com.aetherianartificer.townstead.chronicle.store;

import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import net.minecraft.resources.ResourceLocation;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * MVStore-backed chronicle archive. One writer thread batches queued changes
 * into durable commits; one reader thread serves indexed lookups. The storage
 * lock makes a committed batch and all of its secondary-index updates visible
 * to readers as one unit.
 *
 * <p>Records use a small, explicitly versioned binary codec instead of Java
 * serialization so saves remain stable when model classes evolve. MVStore is
 * pure Java, so the shipped mod does not carry SQLite's platform binaries.</p>
 */
public final class ChronicleDatabase implements ChronicleStore {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("townstead");

    private static final int SCHEMA_VERSION = 1;
    private static final int RECORD_VERSION = 1;
    private static final int QUEUE_CAPACITY = 65_536;
    private static final int BATCH_SIZE = 512;
    private static final long BATCH_WAIT_MS = 2_000L;
    private static final int MAX_COLLECTION_SIZE = 16_384;
    private static final int MAX_STRING_BYTES = 16 * 1024 * 1024;

    private static final String META_SCHEMA = "schema";
    private static final String META_EVENT_MAX = "eventMax";
    private static final String META_ARC_MAX = "arcMax";
    private static final String META_ACCOUNT_MAX = "accountMax";

    private sealed interface Op permits EventOp, ArcOp, AccountOp, CheckpointOp, FlushOp {}
    private record EventOp(ChronicleEvent event) implements Op {}
    private record ArcOp(Arc arc) implements Op {}
    private record AccountOp(Account account) implements Op {}
    private record CheckpointOp() implements Op {}
    private record FlushOp(CompletableFuture<Void> done) implements Op {}

    private final Path dbFile;
    private final BlockingQueue<Op> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final ReentrantReadWriteLock storageLock = new ReentrantReadWriteLock();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();

    private @Nullable MVStore mvStore;
    private @Nullable MVMap<Long, byte[]> events;
    private @Nullable MVMap<Long, byte[]> arcs;
    private @Nullable MVMap<Long, byte[]> accounts;
    private @Nullable MVMap<String, Long> eventsBySubject;
    private @Nullable MVMap<String, Long> eventsByVillage;
    private @Nullable MVMap<String, Long> eventsByDay;
    private @Nullable MVMap<String, Long> eventsByArc;
    private @Nullable MVMap<String, Long> accountsByKnower;
    private @Nullable MVMap<String, Long> accountsByStory;
    private @Nullable MVMap<String, Long> meta;
    private @Nullable Thread writerThread;
    private @Nullable ExecutorService readExecutor;
    private volatile IdMaxima idMaxima = IdMaxima.NONE;

    private ChronicleDatabase(Path dbFile) {
        this.dbFile = dbFile;
    }

    /** Opens (or degrades). Never throws: a failed open returns a disabled store. */
    public static ChronicleDatabase open(Path dbFile) {
        ChronicleDatabase db = new ChronicleDatabase(dbFile);
        try {
            Files.createDirectories(dbFile.getParent());
            db.mvStore = new MVStore.Builder()
                    .fileName(dbFile.toString())
                    .compress()
                    .autoCommitDisabled()
                    .open();
            db.openMaps();
            db.validateSchema();
            db.readIdMaxima();
            db.open.set(true);
            db.writerThread = new Thread(db::writerLoop, "Townstead-Chronicle-Writer");
            db.writerThread.setDaemon(true);
            db.writerThread.start();
            db.readExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Townstead-Chronicle-Reader");
                t.setDaemon(true);
                return t;
            });
            LOGGER.info("[Chronicles] MVStore archive opened at {}", dbFile);
        } catch (Throwable t) {
            LOGGER.error("[Chronicles] Failed to open archive at {}; running without event history", dbFile, t);
            db.shutdownStore();
        }
        return db;
    }

    private void openMaps() {
        MVStore store = requireStore();
        events = store.openMap("events");
        arcs = store.openMap("arcs");
        accounts = store.openMap("accounts");
        eventsBySubject = store.openMap("event.subject");
        eventsByVillage = store.openMap("event.village");
        eventsByDay = store.openMap("event.day");
        eventsByArc = store.openMap("event.arc");
        accountsByKnower = store.openMap("account.knower");
        accountsByStory = store.openMap("account.story");
        meta = store.openMap("meta");
    }

    private void validateSchema() {
        MVMap<String, Long> metadata = requireMeta();
        Long schema = metadata.get(META_SCHEMA);
        if (schema == null) {
            metadata.put(META_SCHEMA, (long) SCHEMA_VERSION);
            requireStore().commit();
            requireStore().sync();
        } else if (schema != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported Chronicle archive schema " + schema
                    + " (expected " + SCHEMA_VERSION + ")");
        }
    }

    private void readIdMaxima() {
        MVMap<String, Long> metadata = requireMeta();
        idMaxima = new IdMaxima(
                metadata.getOrDefault(META_EVENT_MAX, 0L),
                metadata.getOrDefault(META_ARC_MAX, 0L),
                metadata.getOrDefault(META_ACCOUNT_MAX, 0L));
    }

    // ---- writes ----

    @Override
    public void appendEvent(ChronicleEvent event) {
        if (!open.get()) return;
        if (!queue.offer(new EventOp(event))) {
            if (event.keep()) {
                try {
                    if (queue.offer(new EventOp(event), 100, TimeUnit.MILLISECONDS)) return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            warnDropped();
        }
    }

    @Override
    public void appendArc(Arc arc) {
        if (open.get() && !queue.offer(new ArcOp(arc))) warnDropped();
    }

    @Override
    public void appendAccount(Account account) {
        if (open.get() && !queue.offer(new AccountOp(account))) warnDropped();
    }

    private void warnDropped() {
        long total = dropped.incrementAndGet();
        if (total == 1 || total % 1_000 == 0) {
            LOGGER.warn("[Chronicles] Write queue full; dropped {} records so far", total);
        }
    }

    @Override
    public void requestCheckpoint() {
        if (open.get()) queue.offer(new CheckpointOp());
    }

    private void writerLoop() {
        List<Op> batch = new ArrayList<>(BATCH_SIZE);
        while (open.get() || !queue.isEmpty()) {
            batch.clear();
            try {
                Op first = queue.poll(BATCH_WAIT_MS, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                writeBatch(batch);
            } catch (Throwable t) {
                int lost = dataOpCount(batch);
                LOGGER.error("[Chronicles] Archive write batch failed ({} records lost)", lost, t);
                dropped.addAndGet(lost);
                for (Op op : batch) {
                    if (op instanceof FlushOp flush) flush.done().completeExceptionally(t);
                }
            }
        }
    }

    private void writeBatch(List<Op> batch) {
        boolean checkpoint = false;
        long eventsWritten = 0L;
        List<FlushOp> flushes = new ArrayList<>(0);
        storageLock.writeLock().lock();
        try {
            for (Op op : batch) {
                if (op instanceof EventOp eventOp) {
                    putEvent(eventOp.event());
                    eventsWritten++;
                } else if (op instanceof ArcOp arcOp) {
                    putArc(arcOp.arc());
                } else if (op instanceof AccountOp accountOp) {
                    putAccount(accountOp.account());
                } else if (op instanceof CheckpointOp) {
                    checkpoint = true;
                } else if (op instanceof FlushOp flush) {
                    flushes.add(flush);
                }
            }
            requireStore().commit();
            if (checkpoint || !flushes.isEmpty()) requireStore().sync();
            readIdMaxima();
            written.addAndGet(eventsWritten);
        } catch (Throwable t) {
            try {
                requireStore().rollback();
            } catch (Throwable rollbackFailure) {
                t.addSuppressed(rollbackFailure);
            }
            throw t;
        } finally {
            storageLock.writeLock().unlock();
        }
        for (FlushOp flush : flushes) flush.done().complete(null);
    }

    private void putEvent(ChronicleEvent event) {
        MVMap<Long, byte[]> eventMap = require(events);
        byte[] previous = eventMap.put(event.eventId(), encodeEvent(event));
        if (previous != null) removeEventIndexes(decodeEvent(previous));
        indexEvent(event, event.eventId());
        updateMaximum(META_EVENT_MAX, event.eventId());
    }

    private void indexEvent(ChronicleEvent event, long id) {
        for (Participation participation : event.participations()) {
            UUID uuid = participation.ref().uuid();
            if (uuid != null) require(eventsBySubject).put(subjectPrefix(uuid) + sortableLong(id), id);
        }
        require(eventsByVillage).put(villagePrefix(event.dimension(), event.villageId()) + sortableLong(id), id);
        require(eventsByDay).put(dayPrefix(event.worldDay()) + sortableLong(id), id);
        if (event.arcId() > 0) require(eventsByArc).put(arcPrefix(event.arcId()) + sortableLong(id), id);
    }

    private void removeEventIndexes(ChronicleEvent event) {
        long id = event.eventId();
        for (Participation participation : event.participations()) {
            UUID uuid = participation.ref().uuid();
            if (uuid != null) require(eventsBySubject).remove(subjectPrefix(uuid) + sortableLong(id));
        }
        require(eventsByVillage).remove(villagePrefix(event.dimension(), event.villageId()) + sortableLong(id));
        require(eventsByDay).remove(dayPrefix(event.worldDay()) + sortableLong(id));
        if (event.arcId() > 0) require(eventsByArc).remove(arcPrefix(event.arcId()) + sortableLong(id));
    }

    private void putArc(Arc arc) {
        require(arcs).put(arc.arcId(), encodeArc(arc));
        updateMaximum(META_ARC_MAX, arc.arcId());
    }

    private void putAccount(Account account) {
        MVMap<Long, byte[]> accountMap = require(accounts);
        byte[] previous = accountMap.put(account.accountId(), encodeAccount(account));
        if (previous != null) removeAccountIndexes(decodeAccount(previous));
        long id = account.accountId();
        require(accountsByKnower).put(knowerPrefix(account.knower()) + sortableLong(id), id);
        if (account.storyEventId() > 0) {
            require(accountsByStory).put(storyPrefix(account.storyEventId()) + sortableLong(id), id);
        }
        updateMaximum(META_ACCOUNT_MAX, id);
    }

    private void removeAccountIndexes(Account account) {
        long id = account.accountId();
        require(accountsByKnower).remove(knowerPrefix(account.knower()) + sortableLong(id));
        if (account.storyEventId() > 0) {
            require(accountsByStory).remove(storyPrefix(account.storyEventId()) + sortableLong(id));
        }
    }

    private void updateMaximum(String key, long value) {
        MVMap<String, Long> metadata = requireMeta();
        if (value > metadata.getOrDefault(key, 0L)) metadata.put(key, value);
    }

    private static int dataOpCount(List<Op> batch) {
        int count = 0;
        for (Op op : batch) {
            if (op instanceof EventOp || op instanceof ArcOp || op instanceof AccountOp) count++;
        }
        return count;
    }

    // ---- reads ----

    @Override
    public CompletableFuture<List<ChronicleEvent>> bySubject(UUID subject, long beforeEventId, int limit) {
        return readAsync(() -> eventsFromIndex(require(eventsBySubject), subjectPrefix(subject),
                beforeEventId, limit, true));
    }

    @Override
    public CompletableFuture<List<ChronicleEvent>> byVillage(ResourceLocation dimension, int villageId,
                                                             long beforeEventId, int limit) {
        return readAsync(() -> eventsFromIndex(require(eventsByVillage), villagePrefix(dimension, villageId),
                beforeEventId, limit, true));
    }

    @Override
    public CompletableFuture<List<ChronicleEvent>> byDay(long worldDay, int limit) {
        return readAsync(() -> eventsFromIndex(require(eventsByDay), dayPrefix(worldDay), 0L, limit, true));
    }

    @Override
    public CompletableFuture<List<ChronicleEvent>> byArc(long arcId, int limit) {
        return readAsync(() -> eventsFromIndex(require(eventsByArc), arcPrefix(arcId), 0L, limit, false));
    }

    @Override
    public CompletableFuture<Optional<ChronicleEvent>> byId(long eventId) {
        return readAsync(() -> {
            byte[] bytes = require(events).get(eventId);
            return bytes == null ? Optional.empty() : Optional.of(decodeEvent(bytes));
        });
    }

    private List<ChronicleEvent> eventsFromIndex(MVMap<String, Long> index, String prefix,
                                                  long beforeId, int limit, boolean reverse) {
        List<ChronicleEvent> result = new ArrayList<>();
        Iterator<String> keys = keyIterator(index, scanStart(prefix, beforeId, reverse), reverse);
        int boundedLimit = clampLimit(limit);
        while (keys.hasNext() && result.size() < boundedLimit) {
            String key = keys.next();
            if (!key.startsWith(prefix)) break;
            Long id = index.get(key);
            byte[] bytes = id == null ? null : require(events).get(id);
            if (bytes != null) result.add(decodeEvent(bytes));
        }
        return result;
    }

    @Override
    public CompletableFuture<List<Account>> accountsByKnower(UUID knower, int limit) {
        return readAsync(() -> accountsFromIndex(require(accountsByKnower), knowerPrefix(knower), limit, true));
    }

    @Override
    public CompletableFuture<List<Account>> accountsByStory(long storyEventId, int limit) {
        return readAsync(() -> accountsFromIndex(require(accountsByStory), storyPrefix(storyEventId), limit, false));
    }

    private List<Account> accountsFromIndex(MVMap<String, Long> index, String prefix, int limit, boolean reverse) {
        List<Account> result = new ArrayList<>();
        String start = reverse ? prefix + sortableLong(Long.MAX_VALUE) : prefix;
        Iterator<String> keys = keyIterator(index, start, reverse);
        int boundedLimit = clampLimit(limit);
        while (keys.hasNext() && result.size() < boundedLimit) {
            String key = keys.next();
            if (!key.startsWith(prefix)) break;
            Long id = index.get(key);
            byte[] bytes = id == null ? null : require(accounts).get(id);
            if (bytes != null) result.add(decodeAccount(bytes));
        }
        return result;
    }

    @Override
    public CompletableFuture<List<KnownStory>> knownStories(UUID knower, int limit) {
        return readAsync(() -> {
            List<KnownStory> result = new ArrayList<>();
            String prefix = knowerPrefix(knower);
            MVMap<String, Long> knowerIndex = require(accountsByKnower);
            Iterator<String> keys = knowerIndex.keyIteratorReverse(prefix + sortableLong(Long.MAX_VALUE));
            int boundedLimit = clampLimit(limit);
            while (keys.hasNext() && result.size() < boundedLimit) {
                String key = keys.next();
                if (!key.startsWith(prefix)) break;
                Long accountId = require(accountsByKnower).get(key);
                byte[] accountBytes = accountId == null ? null : require(accounts).get(accountId);
                if (accountBytes == null) continue;
                Account account = decodeAccount(accountBytes);
                if (account.storyEventId() <= 0) continue;
                byte[] eventBytes = require(events).get(account.storyEventId());
                if (eventBytes == null) continue;
                ChronicleEvent event = decodeEvent(eventBytes);
                result.add(new KnownStory(event.eventId(), account.accountId(), account.fidelity(),
                        account.learnedDay(), event.templateId().toString(), event.worldDay(),
                        event.villageId(), event.magnitude(), event.reach(), account.overlayJson()));
            }
            return result;
        });
    }

    private <T> CompletableFuture<T> readAsync(Supplier<T> query) {
        ExecutorService executor = readExecutor;
        if (!open.get() || executor == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("chronicle archive unavailable"));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            storageLock.readLock().lock();
            try {
                if (!open.get()) throw new IllegalStateException("chronicle archive unavailable");
                future.complete(query.get());
            } catch (Throwable t) {
                LOGGER.debug("[Chronicles] Archive read failed", t);
                future.completeExceptionally(t);
            } finally {
                storageLock.readLock().unlock();
            }
        });
        return future;
    }

    // ---- lifecycle and stats ----

    @Override
    public boolean flushBlocking(long timeoutMs) {
        if (!open.get()) return true;
        FlushOp flush = new FlushOp(new CompletableFuture<>());
        if (!queue.offer(flush)) return false;
        try {
            flush.done().get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[Chronicles] Archive flush failed or timed out with {} records queued", queue.size());
            return false;
        }
    }

    @Override
    public Stats stats() {
        long bytes = 0L;
        try {
            if (Files.exists(dbFile)) bytes = Files.size(dbFile);
        } catch (Exception ignored) {
        }
        return new Stats(open.get(), queue.size(), written.get(), dropped.get(), bytes);
    }

    @Override
    public IdMaxima idMaxima() {
        return idMaxima;
    }

    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) return;
        flushBlocking(10_000L);
        open.set(false);
        Thread writer = writerThread;
        if (writer != null) {
            writer.interrupt();
            try {
                writer.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ExecutorService executor = readExecutor;
        if (executor != null) executor.shutdownNow();
        storageLock.writeLock().lock();
        try {
            MVStore store = mvStore;
            if (store != null && !store.isClosed()) {
                store.commit();
                store.sync();
                store.close();
            }
        } catch (Throwable t) {
            LOGGER.warn("[Chronicles] Archive close failed", t);
        } finally {
            mvStore = null;
            storageLock.writeLock().unlock();
        }
        LOGGER.info("[Chronicles] Archive closed ({} events written, {} records dropped)",
                written.get(), dropped.get());
    }

    private void shutdownStore() {
        open.set(false);
        MVStore store = mvStore;
        mvStore = null;
        if (store != null) {
            try {
                store.closeImmediately();
            } catch (Throwable ignored) {
            }
        }
    }

    // ---- ordered index keys ----

    private static String subjectPrefix(UUID subject) {
        return "s|" + subject + "|";
    }

    private static String villagePrefix(ResourceLocation dimension, int villageId) {
        return "v|" + dimension + "|" + sortableInt(villageId) + "|";
    }

    private static String dayPrefix(long worldDay) {
        return "d|" + sortableLong(worldDay) + "|";
    }

    private static String arcPrefix(long arcId) {
        return "r|" + sortableLong(arcId) + "|";
    }

    private static String knowerPrefix(UUID knower) {
        return "k|" + knower + "|";
    }

    private static String storyPrefix(long storyId) {
        return "t|" + sortableLong(storyId) + "|";
    }

    private static String scanStart(String prefix, long beforeId, boolean reverse) {
        if (!reverse) return prefix;
        long highest = beforeId <= 0 ? Long.MAX_VALUE : beforeId - 1;
        return prefix + sortableLong(highest);
    }

    private static String sortableLong(long value) {
        return String.format(Locale.ROOT, "%016x", value ^ Long.MIN_VALUE);
    }

    private static String sortableInt(int value) {
        return String.format(Locale.ROOT, "%08x", value ^ Integer.MIN_VALUE);
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 256));
    }

    private static <V> Iterator<String> keyIterator(MVMap<String, V> map, String start, boolean reverse) {
        return reverse ? map.keyIteratorReverse(start) : map.keyIterator(start);
    }

    // ---- stable record codec ----

    private static byte[] encodeEvent(ChronicleEvent event) {
        return encode(out -> {
            out.writeInt(RECORD_VERSION);
            out.writeLong(event.eventId());
            writeString(out, event.templateId().toString());
            out.writeLong(event.worldDay());
            out.writeLong(event.gameTime());
            writeString(out, event.dimension().toString());
            out.writeLong(event.packedPos());
            out.writeInt(event.villageId());
            writeString(out, event.category());
            out.writeFloat(event.magnitude());
            out.writeInt(event.reach());
            out.writeLong(event.causeEventId());
            out.writeLong(event.arcId());
            out.writeBoolean(event.keep());
            out.writeInt(event.participations().size());
            for (Participation participation : event.participations()) {
                writeString(out, participation.role());
                writeRef(out, participation.ref());
            }
            writeStringMap(out, event.params());
        });
    }

    private static ChronicleEvent decodeEvent(byte[] bytes) {
        try (DataInputStream in = input(bytes)) {
            requireRecordVersion(in);
            long id = in.readLong();
            ResourceLocation template = parseRl(readString(in));
            long day = in.readLong();
            long gameTime = in.readLong();
            ResourceLocation dimension = parseRl(readString(in));
            long packedPos = in.readLong();
            int village = in.readInt();
            String category = readString(in);
            float magnitude = in.readFloat();
            int reach = in.readInt();
            long causeId = in.readLong();
            long arcId = in.readLong();
            boolean keep = in.readBoolean();
            int count = readCount(in);
            List<Participation> participations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                participations.add(new Participation(readString(in), readRef(in)));
            }
            return new ChronicleEvent(id, template, day, gameTime, dimension, packedPos,
                    village, category, magnitude, reach, causeId, arcId, keep,
                    participations, readStringMap(in));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Invalid Chronicle event record", e);
        }
    }

    private static byte[] encodeArc(Arc arc) {
        return encode(out -> {
            out.writeInt(RECORD_VERSION);
            out.writeLong(arc.arcId());
            writeString(out, arc.type());
            out.writeInt(arc.villageId());
            out.writeInt(arc.status());
            out.writeLong(arc.startDay());
            out.writeLong(arc.endDay());
            writeStringMap(out, arc.params());
        });
    }

    private static byte[] encodeAccount(Account account) {
        return encode(out -> {
            out.writeInt(RECORD_VERSION);
            out.writeLong(account.accountId());
            out.writeLong(account.storyEventId());
            writeUuid(out, account.knower());
            writeString(out, account.channel());
            out.writeLong(account.sourceAccountId());
            out.writeFloat(account.fidelity());
            out.writeLong(account.learnedDay());
            writeNullableString(out, account.overlayJson());
        });
    }

    private static Account decodeAccount(byte[] bytes) {
        try (DataInputStream in = input(bytes)) {
            requireRecordVersion(in);
            return new Account(in.readLong(), in.readLong(), readUuid(in), readString(in),
                    in.readLong(), in.readFloat(), in.readLong(), readNullableString(in));
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Invalid Chronicle account record", e);
        }
    }

    private static void writeRef(DataOutputStream out, ChronicleRef ref) throws IOException {
        out.writeInt(ref.kind().ordinal());
        writeNullableUuid(out, ref.uuid());
        out.writeInt(ref.intA());
        out.writeInt(ref.intB());
        writeNullableString(out, ref.str());
        writeString(out, ref.displayName());
    }

    private static ChronicleRef readRef(DataInputStream in) throws IOException {
        return new ChronicleRef(ChronicleRef.Kind.byOrdinal(in.readInt()), readNullableUuid(in),
                in.readInt(), in.readInt(), readNullableString(in), readString(in));
    }

    private static void writeStringMap(DataOutputStream out, Map<String, String> values) throws IOException {
        out.writeInt(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            writeString(out, entry.getKey());
            writeString(out, entry.getValue());
        }
    }

    private static Map<String, String> readStringMap(DataInputStream in) throws IOException {
        int count = readCount(in);
        if (count == 0) return Map.of();
        Map<String, String> values = new HashMap<>(count);
        for (int i = 0; i < count; i++) values.put(readString(in), readString(in));
        return values;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("Invalid string length " + length);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException("Truncated string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeNullableString(DataOutputStream out, @Nullable String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) writeString(out, value);
    }

    private static @Nullable String readNullableString(DataInputStream in) throws IOException {
        return in.readBoolean() ? readString(in) : null;
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeNullableUuid(DataOutputStream out, @Nullable UUID uuid) throws IOException {
        out.writeBoolean(uuid != null);
        if (uuid != null) writeUuid(out, uuid);
    }

    private static @Nullable UUID readNullableUuid(DataInputStream in) throws IOException {
        return in.readBoolean() ? readUuid(in) : null;
    }

    private static int readCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_COLLECTION_SIZE) throw new IOException("Invalid collection size " + count);
        return count;
    }

    private static void requireRecordVersion(DataInputStream in) throws IOException {
        int version = in.readInt();
        if (version != RECORD_VERSION) throw new IOException("Unsupported record version " + version);
    }

    private interface Encoder {
        void write(DataOutputStream out) throws IOException;
    }

    private static byte[] encode(Encoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                encoder.write(out);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode Chronicle record", e);
        }
    }

    private static DataInputStream input(byte[] bytes) {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    private static ResourceLocation parseRl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }

    private MVStore requireStore() {
        MVStore value = mvStore;
        if (value == null) throw new IllegalStateException("Chronicle archive is closed");
        return value;
    }

    private MVMap<String, Long> requireMeta() {
        return require(meta);
    }

    private static <K, V> MVMap<K, V> require(@Nullable MVMap<K, V> map) {
        if (map == null) throw new IllegalStateException("Chronicle archive is not initialized");
        return map;
    }
}
