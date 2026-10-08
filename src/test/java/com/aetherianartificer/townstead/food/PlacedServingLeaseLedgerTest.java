package com.aetherianartificer.townstead.food;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlacedServingLeaseLedgerTest {
    private static final PlacedServingLeaseLedger.SourceKey SOURCE =
            new PlacedServingLeaseLedger.SourceKey("test:room", 42L, "shared_pitcher");

    @Test
    void sharedReservationsConserveEveryPortionAndDuplicateSipIsIdempotent() {
        PlacedServingLeaseLedger ledger = new PlacedServingLeaseLedger();
        assertTrue(ledger.open(SOURCE, 6));
        var first = ledger.acquire(SOURCE, UUID.randomUUID(), 2, 200, 0);
        var second = ledger.acquire(SOURCE, UUID.randomUUID(), 3, 200, 0);
        assertNotNull(first);
        assertNotNull(second);
        assertConserved(ledger, 6, 6, 1, 5, 0);

        UUID operation = UUID.randomUUID();
        AtomicInteger nativeCalls = new AtomicInteger();
        var committed = ledger.consume(first.id(), operation, 1, 1, amount -> {
            nativeCalls.incrementAndGet();
            return PlacedServingLeaseLedger.Commit.COMMITTED;
        });
        assertEquals(PlacedServingLeaseLedger.Status.COMMITTED, committed.status());
        var duplicate = ledger.consume(first.id(), operation, 1, 2, amount -> {
            nativeCalls.incrementAndGet();
            return PlacedServingLeaseLedger.Commit.COMMITTED;
        });
        assertEquals(PlacedServingLeaseLedger.Status.DUPLICATE, duplicate.status());
        assertEquals(1, nativeCalls.get());
        assertConserved(ledger, 6, 5, 1, 4, 1);

        assertTrue(ledger.release(first.id()));
        assertConserved(ledger, 6, 5, 2, 3, 1);
        assertTrue(ledger.release(second.id()));
        assertConserved(ledger, 6, 5, 5, 0, 1);
    }

    @Test
    void refusalPreservesLeaseWhileAmbiguousCommitBurnsExactlyOnePortion() {
        PlacedServingLeaseLedger ledger = new PlacedServingLeaseLedger();
        assertTrue(ledger.open(SOURCE, 3));
        var lease = ledger.acquire(SOURCE, UUID.randomUUID(), 2, 20, 0);
        assertNotNull(lease);

        var refused = ledger.consume(lease.id(), UUID.randomUUID(), 1, 1,
                amount -> PlacedServingLeaseLedger.Commit.REFUSED);
        assertEquals(PlacedServingLeaseLedger.Status.REFUSED, refused.status());
        assertConserved(ledger, 3, 3, 1, 2, 0);

        var ambiguous = ledger.consume(lease.id(), UUID.randomUUID(), 1, 2,
                amount -> { throw new IllegalStateException("native source changed before failure"); });
        assertEquals(PlacedServingLeaseLedger.Status.COMMITTED_ERROR, ambiguous.status());
        assertEquals(1, ambiguous.consumed());
        assertConserved(ledger, 3, 2, 1, 1, 1);
    }

    @Test
    void interruptionExpiryReturnsOnlyUnconsumedReservation() {
        PlacedServingLeaseLedger ledger = new PlacedServingLeaseLedger();
        assertTrue(ledger.open(SOURCE, 4));
        var lease = ledger.acquire(SOURCE, UUID.randomUUID(), 3, 10, 100);
        assertNotNull(lease);
        ledger.consume(lease.id(), UUID.randomUUID(), 1, 101,
                amount -> PlacedServingLeaseLedger.Commit.COMMITTED);

        assertEquals(1, ledger.cleanup(111));
        assertConserved(ledger, 4, 3, 3, 0, 1);
        assertTrue(ledger.active().isEmpty());
    }

    private static void assertConserved(PlacedServingLeaseLedger ledger, int initial,
                                        int remaining, int available, int reserved, int consumed) {
        var snapshot = ledger.snapshot(SOURCE);
        assertNotNull(snapshot);
        assertEquals(initial, snapshot.initial());
        assertEquals(remaining, snapshot.remaining());
        assertEquals(available, snapshot.available());
        assertEquals(reserved, snapshot.reserved());
        assertEquals(consumed, snapshot.consumed());
        assertTrue(snapshot.conserved());
    }
}
