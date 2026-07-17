package com.aetherianartificer.townstead.chronicle.store;

import com.aetherianartificer.townstead.chronicle.model.Arc;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the SQLite archive end to end: append through the async writer,
 * query with participations and params intact, and survive a close/reopen.
 */
class ChronicleDatabaseRoundTripTest {

    @TempDir
    Path tempDir;

    private static final UUID COOK = UUID.randomUUID();
    private static final UUID WITNESS = UUID.randomUUID();

    private ChronicleEvent event(long day, int village, long arcId, UUID subject) {
        return new ChronicleEvent(
                0L,
                rl("townstead:feast_prepared"),
                day,
                day * 24000L,
                rl("minecraft:overworld"),
                42L,
                village,
                "work.cooking",
                1.5f,
                ChronicleEvent.REACH_VILLAGE,
                ChronicleEvent.NONE,
                arcId,
                false,
                List.of(
                        new Participation("cook", ChronicleRef.villager(subject, "Bram")),
                        new Participation(Participation.ROLE_WITNESS, ChronicleRef.villager(WITNESS, "Gareth"))),
                Map.of("dish", "farmersdelight:stew"));
    }

    @Test
    void roundTripAndReopen() throws Exception {
        Path dbFile = tempDir.resolve("chronicles.db");
        long nextId = 1;

        ChronicleDatabase db = ChronicleDatabase.open(dbFile);
        assertTrue(db.stats().available(), "archive should open in a plain JVM");

        db.appendArc(new Arc(1L, "founding", 7, Arc.STATUS_OPEN, 100L, 100L, Map.of()));
        db.appendEvent(event(100L, 7, 1L, COOK).withId(nextId++));
        db.appendEvent(event(101L, 7, ChronicleEvent.NONE, COOK).withId(nextId++));
        db.appendEvent(event(102L, 9, ChronicleEvent.NONE, UUID.randomUUID()).withId(nextId++));
        assertTrue(db.flushBlocking(5_000L), "flush should drain the queue");

        List<ChronicleEvent> bySubject = db.bySubject(COOK, 0L, 10).get(5, TimeUnit.SECONDS);
        assertEquals(2, bySubject.size());
        assertEquals(2L, bySubject.get(0).eventId(), "newest first");
        assertEquals("work.cooking", bySubject.get(0).category());
        assertEquals("farmersdelight:stew", bySubject.get(0).params().get("dish"));

        List<ChronicleEvent> byVillage = db.byVillage(rl("minecraft:overworld"), 9, 0L, 10)
                .get(5, TimeUnit.SECONDS);
        assertEquals(1, byVillage.size());
        assertEquals(3L, byVillage.get(0).eventId());

        List<ChronicleEvent> byArc = db.byArc(1L, 10).get(5, TimeUnit.SECONDS);
        assertEquals(1, byArc.size());

        Optional<ChronicleEvent> byId = db.byId(1L).get(5, TimeUnit.SECONDS);
        assertTrue(byId.isPresent());
        ChronicleEvent loaded = byId.get();
        assertEquals(2, loaded.participations().size());
        Participation cook = loaded.participations().stream()
                .filter(p -> "cook".equals(p.role())).findFirst().orElseThrow();
        assertEquals(COOK, cook.ref().uuid());
        assertEquals("Bram", cook.ref().displayName());
        assertEquals(ChronicleRef.Kind.VILLAGER, cook.ref().kind());

        db.close();

        ChronicleDatabase reopened = ChronicleDatabase.open(dbFile);
        assertTrue(reopened.stats().available());
        List<ChronicleEvent> persisted = reopened.bySubject(COOK, 0L, 10).get(5, TimeUnit.SECONDS);
        assertEquals(2, persisted.size(), "events must survive close/reopen");
        reopened.close();
    }

    @Test
    void witnessQueriesFindTheEventToo() throws Exception {
        ChronicleDatabase db = ChronicleDatabase.open(tempDir.resolve("witness.db"));
        db.appendEvent(event(50L, 3, ChronicleEvent.NONE, COOK).withId(1L));
        assertTrue(db.flushBlocking(5_000L));
        List<ChronicleEvent> seen = db.bySubject(WITNESS, 0L, 10).get(5, TimeUnit.SECONDS);
        assertEquals(1, seen.size(), "witness participation must be subject-queryable");
        db.close();
    }

    private static ResourceLocation rl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
