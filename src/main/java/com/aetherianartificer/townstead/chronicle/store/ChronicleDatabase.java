package com.aetherianartificer.townstead.chronicle.store;

import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

/**
 * SQLite-backed chronicle archive. One writer thread owns the write
 * connection and batches queued ops into transactions; one reader thread owns
 * a second connection (WAL allows readers concurrent with the writer). If the
 * database cannot open (native extraction failure, corrupt file), the store
 * degrades: appends drop with one error log, reads resolve empty — the hot
 * tier keeps gameplay intact.
 */
public final class ChronicleDatabase implements ChronicleStore {

    // Own logger (not Townstead.LOGGER) so unit tests can load this class
    // without initializing the mod entry class.
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("townstead");

    private static final int QUEUE_CAPACITY = 65536;
    private static final int BATCH_SIZE = 512;
    private static final long BATCH_WAIT_MS = 2000L;
    private static final Gson GSON = new Gson();
    private static final Type PARAMS_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private sealed interface Op permits EventOp, ArcOp, AccountOp, CheckpointOp, FlushOp {}
    private record EventOp(ChronicleEvent event) implements Op {}
    private record ArcOp(Arc arc) implements Op {}
    private record AccountOp(com.aetherianartificer.townstead.chronicle.model.Account account) implements Op {}
    private record CheckpointOp() implements Op {}
    private record FlushOp(CompletableFuture<Void> done) implements Op {}

    private final Path dbFile;
    private final BlockingQueue<Op> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final Map<String, Integer> internCache = new HashMap<>();

    private @Nullable Connection writeConnection;
    private @Nullable Connection readConnection;
    private @Nullable Thread writerThread;
    private @Nullable ExecutorService readExecutor;

    private ChronicleDatabase(Path dbFile) {
        this.dbFile = dbFile;
    }

    /** Opens (or degrades). Never throws: a failed open returns a disabled store. */
    public static ChronicleDatabase open(Path dbFile) {
        ChronicleDatabase db = new ChronicleDatabase(dbFile);
        try {
            Files.createDirectories(dbFile.getParent());
            db.writeConnection = ChronicleSqlite.open("jdbc:sqlite:" + dbFile);
            db.readConnection = ChronicleSqlite.open("jdbc:sqlite:" + dbFile);
            db.applyPragmas(db.writeConnection, true);
            db.applyPragmas(db.readConnection, false);
            db.createSchema();
            db.open.set(true);
            db.writerThread = new Thread(db::writerLoop, "Townstead-Chronicle-Writer");
            db.writerThread.setDaemon(true);
            db.writerThread.start();
            db.readExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Townstead-Chronicle-Reader");
                t.setDaemon(true);
                return t;
            });
            LOGGER.info("[Chronicles] Archive opened at {}", dbFile);
        } catch (Throwable t) {
            LOGGER.error("[Chronicles] Failed to open archive at {}; running without event history", dbFile, t);
            db.shutdownConnections();
        }
        return db;
    }

    private void applyPragmas(Connection c, boolean writer) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA busy_timeout=5000");
            if (!writer) s.execute("PRAGMA query_only=ON");
        }
    }

    private void createSchema() throws SQLException {
        try (Statement s = writeConnection.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS str(id INTEGER PRIMARY KEY, s TEXT UNIQUE NOT NULL)");
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS event(
                      id INTEGER PRIMARY KEY,
                      template INTEGER NOT NULL,
                      world_day INTEGER NOT NULL,
                      game_time INTEGER NOT NULL,
                      dim INTEGER NOT NULL,
                      pos INTEGER NOT NULL,
                      village INTEGER NOT NULL,
                      category INTEGER NOT NULL,
                      magnitude REAL NOT NULL,
                      reach INTEGER NOT NULL,
                      cause_event_id INTEGER,
                      arc_id INTEGER,
                      keep INTEGER NOT NULL,
                      params TEXT)""");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_event_village ON event(village, id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_event_day ON event(world_day)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_event_arc ON event(arc_id) WHERE arc_id IS NOT NULL");
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS participation(
                      event_id INTEGER NOT NULL,
                      role TEXT NOT NULL,
                      kind INTEGER NOT NULL,
                      uuid BLOB,
                      int_a INTEGER NOT NULL DEFAULT 0,
                      int_b INTEGER NOT NULL DEFAULT 0,
                      str TEXT,
                      name TEXT NOT NULL)""");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_part_uuid ON participation(uuid, event_id) WHERE uuid IS NOT NULL");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_part_event ON participation(event_id)");
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS arc(
                      id INTEGER PRIMARY KEY,
                      type TEXT NOT NULL,
                      village INTEGER NOT NULL,
                      status INTEGER NOT NULL,
                      start_day INTEGER NOT NULL,
                      end_day INTEGER NOT NULL,
                      params TEXT)""");
            // Belief tier (accounts) — schema reserved now so Phase 4 needs no migration.
            s.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS account(
                      id INTEGER PRIMARY KEY,
                      story_event_id INTEGER,
                      knower BLOB NOT NULL,
                      channel TEXT NOT NULL,
                      source_account_id INTEGER,
                      fidelity REAL NOT NULL,
                      learned_day INTEGER NOT NULL,
                      claim TEXT)""");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_account_knower ON account(knower, learned_day)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_account_story ON account(story_event_id)");
            s.executeUpdate("CREATE INDEX IF NOT EXISTS idx_account_knower_story ON account(knower, story_event_id)");
        }
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
            long total = dropped.incrementAndGet();
            if (total == 1 || total % 1000 == 0) {
                LOGGER.warn("[Chronicles] Write queue full; dropped {} events so far", total);
            }
        }
    }

    @Override
    public void appendArc(Arc arc) {
        if (!open.get()) return;
        if (!queue.offer(new ArcOp(arc))) dropped.incrementAndGet();
    }

    @Override
    public void appendAccount(com.aetherianartificer.townstead.chronicle.model.Account account) {
        if (!open.get()) return;
        if (!queue.offer(new AccountOp(account))) dropped.incrementAndGet();
    }

    @Override
    public void requestCheckpoint() {
        if (!open.get()) return;
        queue.offer(new CheckpointOp());
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
                LOGGER.error("[Chronicles] Archive write batch failed ({} ops lost)", batch.size(), t);
                dropped.addAndGet(batch.size());
            }
        }
    }

    private void writeBatch(List<Op> batch) throws SQLException {
        Connection c = writeConnection;
        if (c == null) return;
        boolean checkpoint = false;
        List<FlushOp> flushes = new ArrayList<>(0);
        c.setAutoCommit(false);
        try (PreparedStatement insertEvent = c.prepareStatement(
                "INSERT OR REPLACE INTO event(id,template,world_day,game_time,dim,pos,village,category,magnitude,reach,cause_event_id,arc_id,keep,params) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
             PreparedStatement insertPart = c.prepareStatement(
                "INSERT INTO participation(event_id,role,kind,uuid,int_a,int_b,str,name) VALUES (?,?,?,?,?,?,?,?)");
             PreparedStatement insertArc = c.prepareStatement(
                "INSERT OR REPLACE INTO arc(id,type,village,status,start_day,end_day,params) VALUES (?,?,?,?,?,?,?)");
             PreparedStatement insertAccount = c.prepareStatement(
                "INSERT OR REPLACE INTO account(id,story_event_id,knower,channel,source_account_id,fidelity,learned_day,claim) VALUES (?,?,?,?,?,?,?,?)")) {
            for (Op op : batch) {
                if (op instanceof EventOp eventOp) {
                    ChronicleEvent event = eventOp.event();
                    insertEvent.setLong(1, event.eventId());
                    insertEvent.setInt(2, intern(c, event.templateId().toString()));
                    insertEvent.setLong(3, event.worldDay());
                    insertEvent.setLong(4, event.gameTime());
                    insertEvent.setInt(5, intern(c, event.dimension().toString()));
                    insertEvent.setLong(6, event.packedPos());
                    insertEvent.setInt(7, event.villageId());
                    insertEvent.setInt(8, intern(c, event.category()));
                    insertEvent.setFloat(9, event.magnitude());
                    insertEvent.setInt(10, event.reach());
                    setNullableLong(insertEvent, 11, event.causeEventId());
                    setNullableLong(insertEvent, 12, event.arcId());
                    insertEvent.setInt(13, event.keep() ? 1 : 0);
                    insertEvent.setString(14, event.params().isEmpty() ? null : GSON.toJson(event.params()));
                    insertEvent.addBatch();
                    for (Participation p : event.participations()) {
                        insertPart.setLong(1, event.eventId());
                        insertPart.setString(2, p.role());
                        insertPart.setInt(3, p.ref().kind().ordinal());
                        insertPart.setBytes(4, p.ref().uuid() == null ? null : uuidBytes(p.ref().uuid()));
                        insertPart.setInt(5, p.ref().intA());
                        insertPart.setInt(6, p.ref().intB());
                        insertPart.setString(7, p.ref().str());
                        insertPart.setString(8, p.ref().displayName());
                        insertPart.addBatch();
                    }
                    written.incrementAndGet();
                } else if (op instanceof ArcOp arcOp) {
                    Arc arc = arcOp.arc();
                    insertArc.setLong(1, arc.arcId());
                    insertArc.setString(2, arc.type());
                    insertArc.setInt(3, arc.villageId());
                    insertArc.setInt(4, arc.status());
                    insertArc.setLong(5, arc.startDay());
                    insertArc.setLong(6, arc.endDay());
                    insertArc.setString(7, arc.params().isEmpty() ? null : GSON.toJson(arc.params()));
                    insertArc.addBatch();
                } else if (op instanceof AccountOp accountOp) {
                    var account = accountOp.account();
                    insertAccount.setLong(1, account.accountId());
                    setNullableLong(insertAccount, 2, account.storyEventId());
                    insertAccount.setBytes(3, uuidBytes(account.knower()));
                    insertAccount.setString(4, account.channel());
                    setNullableLong(insertAccount, 5, account.sourceAccountId());
                    insertAccount.setFloat(6, account.fidelity());
                    insertAccount.setLong(7, account.learnedDay());
                    insertAccount.setString(8, account.overlayJson());
                    insertAccount.addBatch();
                } else if (op instanceof CheckpointOp) {
                    checkpoint = true;
                } else if (op instanceof FlushOp flush) {
                    flushes.add(flush);
                }
            }
            insertEvent.executeBatch();
            insertPart.executeBatch();
            insertArc.executeBatch();
            insertAccount.executeBatch();
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
        if (checkpoint) {
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
        for (FlushOp flush : flushes) flush.done().complete(null);
    }

    /** Writer-thread only. */
    private int intern(Connection c, String value) throws SQLException {
        Integer cached = internCache.get(value);
        if (cached != null) return cached;
        try (PreparedStatement insert = c.prepareStatement("INSERT OR IGNORE INTO str(s) VALUES (?)")) {
            insert.setString(1, value);
            insert.executeUpdate();
        }
        try (PreparedStatement select = c.prepareStatement("SELECT id FROM str WHERE s=?")) {
            select.setString(1, value);
            try (ResultSet r = select.executeQuery()) {
                r.next();
                int id = r.getInt(1);
                internCache.put(value, id);
                return id;
            }
        }
    }

    // ---- reads ----

    @Override
    public CompletableFuture<List<ChronicleEvent>> bySubject(UUID subject, long beforeEventId, int limit) {
        return readAsync(c -> {
            String sql = """
                    SELECT e.*, t.s AS template_s, d.s AS dim_s, cat.s AS category_s FROM event e
                    JOIN participation p ON p.event_id = e.id
                    JOIN str t ON t.id = e.template
                    JOIN str d ON d.id = e.dim
                    JOIN str cat ON cat.id = e.category
                    WHERE p.uuid = ? AND (? <= 0 OR e.id < ?)
                    GROUP BY e.id ORDER BY e.id DESC LIMIT ?""";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setBytes(1, uuidBytes(subject));
                s.setLong(2, beforeEventId);
                s.setLong(3, beforeEventId);
                s.setInt(4, clampLimit(limit));
                return readEvents(c, s);
            }
        });
    }

    @Override
    public CompletableFuture<List<ChronicleEvent>> byVillage(ResourceLocation dimension, int villageId,
                                                             long beforeEventId, int limit) {
        return readAsync(c -> {
            String sql = """
                    SELECT e.*, t.s AS template_s, d.s AS dim_s, cat.s AS category_s FROM event e
                    JOIN str t ON t.id = e.template
                    JOIN str d ON d.id = e.dim
                    JOIN str cat ON cat.id = e.category
                    WHERE e.village = ? AND d.s = ? AND (? <= 0 OR e.id < ?)
                    ORDER BY e.id DESC LIMIT ?""";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setInt(1, villageId);
                s.setString(2, dimension.toString());
                s.setLong(3, beforeEventId);
                s.setLong(4, beforeEventId);
                s.setInt(5, clampLimit(limit));
                return readEvents(c, s);
            }
        });
    }

    @Override
    public CompletableFuture<List<ChronicleEvent>> byDay(long worldDay, int limit) {
        return readAsync(c -> {
            String sql = """
                    SELECT e.*, t.s AS template_s, d.s AS dim_s, cat.s AS category_s FROM event e
                    JOIN str t ON t.id = e.template
                    JOIN str d ON d.id = e.dim
                    JOIN str cat ON cat.id = e.category
                    WHERE e.world_day = ? ORDER BY e.id DESC LIMIT ?""";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setLong(1, worldDay);
                s.setInt(2, clampLimit(limit));
                return readEvents(c, s);
            }
        });
    }

    @Override
    public CompletableFuture<List<ChronicleEvent>> byArc(long arcId, int limit) {
        return readAsync(c -> {
            String sql = """
                    SELECT e.*, t.s AS template_s, d.s AS dim_s, cat.s AS category_s FROM event e
                    JOIN str t ON t.id = e.template
                    JOIN str d ON d.id = e.dim
                    JOIN str cat ON cat.id = e.category
                    WHERE e.arc_id = ? ORDER BY e.id ASC LIMIT ?""";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setLong(1, arcId);
                s.setInt(2, clampLimit(limit));
                return readEvents(c, s);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<ChronicleEvent>> byId(long eventId) {
        return readAsync(c -> {
            String sql = """
                    SELECT e.*, t.s AS template_s, d.s AS dim_s, cat.s AS category_s FROM event e
                    JOIN str t ON t.id = e.template
                    JOIN str d ON d.id = e.dim
                    JOIN str cat ON cat.id = e.category
                    WHERE e.id = ?""";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setLong(1, eventId);
                List<ChronicleEvent> events = readEvents(c, s);
                return events.isEmpty() ? Optional.<ChronicleEvent>empty() : Optional.of(events.get(0));
            }
        }).exceptionally(t -> Optional.empty());
    }

    @Override
    public CompletableFuture<List<com.aetherianartificer.townstead.chronicle.model.Account>> accountsByKnower(
            UUID knower, int limit) {
        return readAsync(c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT * FROM account WHERE knower = ? ORDER BY id DESC LIMIT ?")) {
                s.setBytes(1, uuidBytes(knower));
                s.setInt(2, clampLimit(limit));
                return readAccounts(s);
            }
        });
    }

    @Override
    public CompletableFuture<List<com.aetherianartificer.townstead.chronicle.model.Account>> accountsByStory(
            long storyEventId, int limit) {
        return readAsync(c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "SELECT * FROM account WHERE story_event_id = ? ORDER BY id ASC LIMIT ?")) {
                s.setLong(1, storyEventId);
                s.setInt(2, clampLimit(limit));
                return readAccounts(s);
            }
        });
    }

    @Override
    public CompletableFuture<List<KnownStory>> knownStories(UUID knower, int limit) {
        return readAsync(c -> {
            String sql = """
                    SELECT a.story_event_id, a.id AS account_id, a.fidelity, a.learned_day, a.claim,
                           t.s AS template_s, e.world_day, e.village, e.magnitude, e.reach
                    FROM account a
                    JOIN event e ON e.id = a.story_event_id
                    JOIN str t ON t.id = e.template
                    WHERE a.knower = ? ORDER BY a.id DESC LIMIT ?""";
            try (PreparedStatement s = c.prepareStatement(sql)) {
                s.setBytes(1, uuidBytes(knower));
                s.setInt(2, clampLimit(limit));
                List<KnownStory> stories = new ArrayList<>();
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        stories.add(new KnownStory(
                                r.getLong("story_event_id"),
                                r.getLong("account_id"),
                                r.getFloat("fidelity"),
                                r.getLong("learned_day"),
                                r.getString("template_s"),
                                r.getLong("world_day"),
                                r.getInt("village"),
                                r.getFloat("magnitude"),
                                r.getInt("reach"),
                                r.getString("claim")));
                    }
                }
                return stories;
            }
        });
    }

    private List<com.aetherianartificer.townstead.chronicle.model.Account> readAccounts(
            PreparedStatement statement) throws SQLException {
        List<com.aetherianartificer.townstead.chronicle.model.Account> accounts = new ArrayList<>();
        try (ResultSet r = statement.executeQuery()) {
            while (r.next()) {
                long storyId = r.getLong("story_event_id");
                if (r.wasNull()) storyId = ChronicleEvent.NONE;
                long sourceId = r.getLong("source_account_id");
                if (r.wasNull()) sourceId = ChronicleEvent.NONE;
                accounts.add(new com.aetherianartificer.townstead.chronicle.model.Account(
                        r.getLong("id"), storyId, bytesUuid(r.getBytes("knower")),
                        r.getString("channel"), sourceId, r.getFloat("fidelity"),
                        r.getLong("learned_day"), r.getString("claim")));
            }
        }
        return accounts;
    }

    private interface ReadQuery<T> {
        T run(Connection c) throws SQLException;
    }

    private <T> CompletableFuture<T> readAsync(ReadQuery<T> query) {
        ExecutorService executor = readExecutor;
        Connection c = readConnection;
        if (!open.get() || executor == null || c == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("chronicle archive unavailable"));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(query.run(c));
            } catch (Throwable t) {
                LOGGER.debug("[Chronicles] Archive read failed", t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private List<ChronicleEvent> readEvents(Connection c, PreparedStatement statement) throws SQLException {
        List<ChronicleEvent> events = new ArrayList<>();
        try (ResultSet r = statement.executeQuery()) {
            while (r.next()) {
                long causeId = r.getLong("cause_event_id");
                if (r.wasNull()) causeId = ChronicleEvent.NONE;
                long arcId = r.getLong("arc_id");
                if (r.wasNull()) arcId = ChronicleEvent.NONE;
                String paramsJson = r.getString("params");
                Map<String, String> params = paramsJson == null
                        ? Map.of() : GSON.fromJson(paramsJson, PARAMS_TYPE);
                events.add(new ChronicleEvent(
                        r.getLong("id"),
                        parseRl(r.getString("template_s")),
                        r.getLong("world_day"),
                        r.getLong("game_time"),
                        parseRl(r.getString("dim_s")),
                        r.getLong("pos"),
                        r.getInt("village"),
                        r.getString("category_s"),
                        r.getFloat("magnitude"),
                        r.getInt("reach"),
                        causeId,
                        arcId,
                        r.getInt("keep") != 0,
                        List.of(),
                        params));
            }
        }
        return attachParticipations(c, events);
    }

    private List<ChronicleEvent> attachParticipations(Connection c, List<ChronicleEvent> events) throws SQLException {
        if (events.isEmpty()) return events;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < events.size(); i++) in.append(i == 0 ? "?" : ",?");
        Map<Long, List<Participation>> byEvent = new HashMap<>();
        try (PreparedStatement s = c.prepareStatement(
                "SELECT * FROM participation WHERE event_id IN (" + in + ")")) {
            for (int i = 0; i < events.size(); i++) s.setLong(i + 1, events.get(i).eventId());
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    byte[] uuid = r.getBytes("uuid");
                    ChronicleRef ref = new ChronicleRef(
                            ChronicleRef.Kind.byOrdinal(r.getInt("kind")),
                            uuid == null ? null : bytesUuid(uuid),
                            r.getInt("int_a"),
                            r.getInt("int_b"),
                            r.getString("str"),
                            r.getString("name"));
                    byEvent.computeIfAbsent(r.getLong("event_id"), ignored -> new ArrayList<>())
                            .add(new Participation(r.getString("role"), ref));
                }
            }
        }
        List<ChronicleEvent> result = new ArrayList<>(events.size());
        for (ChronicleEvent event : events) {
            List<Participation> parts = byEvent.get(event.eventId());
            result.add(parts == null ? event : new ChronicleEvent(
                    event.eventId(), event.templateId(), event.worldDay(), event.gameTime(),
                    event.dimension(), event.packedPos(), event.villageId(), event.category(),
                    event.magnitude(), event.reach(), event.causeEventId(), event.arcId(),
                    event.keep(), parts, event.params()));
        }
        return result;
    }

    // ---- lifecycle / stats ----

    @Override
    public boolean flushBlocking(long timeoutMs) {
        if (!open.get()) return true;
        FlushOp flush = new FlushOp(new CompletableFuture<>());
        if (!queue.offer(flush)) return false;
        try {
            flush.done().get(timeoutMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[Chronicles] Archive flush timed out with {} ops queued", queue.size());
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
        try (Connection c = writeConnection) {
            if (c != null) {
                try (Statement s = c.createStatement()) {
                    s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
            }
        } catch (Exception ignored) {
        }
        shutdownConnections();
        LOGGER.info("[Chronicles] Archive closed ({} events written, {} dropped)", written.get(), dropped.get());
    }

    private void shutdownConnections() {
        open.set(false);
        try {
            if (readConnection != null) readConnection.close();
        } catch (Exception ignored) {
        }
        readConnection = null;
        writeConnection = null;
    }

    // ---- helpers ----

    private static void setNullableLong(PreparedStatement s, int index, long value) throws SQLException {
        if (value <= 0) {
            s.setNull(index, java.sql.Types.INTEGER);
        } else {
            s.setLong(index, value);
        }
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 256));
    }

    static byte[] uuidBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    static UUID bytesUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static ResourceLocation parseRl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
