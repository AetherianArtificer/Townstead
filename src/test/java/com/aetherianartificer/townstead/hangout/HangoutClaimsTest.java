package com.aetherianartificer.townstead.hangout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangoutClaimsTest {
    @Test
    void linkedClaimsAreAtomicAndReleasedAsOneSession() {
        HangoutClaims ledger = new HangoutClaims();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        HangoutClaims.Key participant = key("participant", "alice");
        HangoutClaims.Key chair = key("spot", "chair-1");
        HangoutClaims.Key linked = key("linked_spot", "chair-block-1");

        assertTrue(ledger.tryClaimAll(first, List.of(participant, chair, linked), 10L, 20L));
        assertFalse(ledger.tryClaimAll(second, List.of(key("participant", "bob"), linked), 11L, 20L));
        assertEquals(3, ledger.size(), "failed atomic claim must not retain its independent participant");

        ledger.release(first);
        assertEquals(0, ledger.size());
        assertTrue(ledger.tryClaimAll(second, List.of(key("participant", "bob"), linked), 12L, 20L));
    }

    @Test
    void expiryRecoversAWholeAbandonedSession() {
        HangoutClaims ledger = new HangoutClaims();
        UUID abandoned = UUID.randomUUID();
        UUID recovered = UUID.randomUUID();
        HangoutClaims.Key venue = key("venue", "tavern#0");

        assertTrue(ledger.tryClaimAll(abandoned, List.of(venue), 100L, 10L));
        assertFalse(ledger.available(venue, recovered, 109L));
        assertTrue(ledger.available(venue, recovered, 110L));
        assertEquals(0, ledger.size());
    }

    private static HangoutClaims.Key key(String kind, String value) {
        return new HangoutClaims.Key("minecraft:overworld", kind, value);
    }
}
