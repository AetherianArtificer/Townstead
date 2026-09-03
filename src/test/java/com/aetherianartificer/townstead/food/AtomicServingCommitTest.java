package com.aetherianartificer.townstead.food;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicServingCommitTest {
    @Test
    void neverRunsEffectsBeforeTheSourceCommits() {
        AtomicBoolean ran = new AtomicBoolean();
        AtomicServingCommit.Outcome<String> outcome = AtomicServingCommit.execute(
                () -> false, () -> { ran.set(true); return "finished"; });

        assertEquals(AtomicServingCommit.Status.REFUSED, outcome.status());
        assertFalse(outcome.committed());
        assertFalse(ran.get());
        assertNull(outcome.value());
    }

    @Test
    void reservesBeforeRunningTheNativeFinishContract() {
        List<String> order = new ArrayList<>();
        AtomicServingCommit.Outcome<String> outcome = AtomicServingCommit.execute(
                () -> { order.add("commit"); return true; },
                () -> { order.add("finish"); return "remainder"; });

        assertEquals(List.of("commit", "finish"), order);
        assertEquals(AtomicServingCommit.Status.COMMITTED, outcome.status());
        assertTrue(outcome.committed());
        assertEquals("remainder", outcome.value());
    }

    @Test
    void anExceptionAfterCommitCanNeverInviteASecondServing() {
        AtomicServingCommit.Outcome<String> outcome = AtomicServingCommit.execute(
                () -> true, () -> { throw new IllegalStateException("native failure"); });

        assertEquals(AtomicServingCommit.Status.COMMITTED_ERROR, outcome.status());
        assertTrue(outcome.committed());
        assertNull(outcome.value());
        assertTrue(outcome.detail().contains("native failure"));
    }

    @Test
    void anAmbiguousSourceExceptionAlsoFailsClosedAgainstRetry() {
        AtomicBoolean finished = new AtomicBoolean();
        AtomicServingCommit.Outcome<String> outcome = AtomicServingCommit.execute(
                () -> { throw new IllegalStateException("source failure"); },
                () -> { finished.set(true); return "finished"; });

        assertEquals(AtomicServingCommit.Status.COMMITTED_ERROR, outcome.status());
        assertTrue(outcome.committed());
        assertFalse(finished.get(), "native effects cannot run after an uncertain reservation");
        assertTrue(outcome.detail().contains("source commit threw"));
    }
}
