package com.aetherianartificer.townstead.hangout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.aetherianartificer.townstead.hangout.HangoutApproaches.Reachability.*;
import static org.junit.jupiter.api.Assertions.*;

class HangoutApproachesTest {
    @Test
    void secondVisitorUsesAnotherApproachToAnAdjacentSeat() {
        var claims = new HangoutClaims();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var shared = approach("shared");
        var alternate = approach("alternate");
        var firstSeat = HangoutClaims.seat("minecraft:overworld", 1, 0);
        var secondSeat = HangoutClaims.seat("minecraft:overworld", 2, 0);
        assertTrue(claims.tryClaimAll(first, List.of(firstSeat, shared), 0, 100));

        var selected = HangoutApproaches.select(List.of(shared, alternate),
                candidate -> claims.available(candidate, second, 1), candidate -> {
                    assertNotEquals(shared, candidate, "reserved approaches need no pathfinding");
                    return REACHABLE;
                });
        assertEquals(alternate, selected);
        assertTrue(claims.tryClaimAll(second, List.of(secondSeat, selected), 1, 100));
        assertTrue(claims.owns(first, firstSeat, 1));
        assertTrue(claims.owns(second, secondSeat, 1));

        claims.releaseKind(first, "approach");
        assertTrue(claims.available(shared, second, 2));
        assertFalse(claims.available(firstSeat, second, 2));
        claims.release(second);
        assertTrue(claims.available(secondSeat, first, 2));
        assertTrue(claims.available(alternate, first, 2));
    }

    @Test
    void exhaustedApproachesAllowTryingTheNextSeatWithoutClaims() {
        var claims = new HangoutClaims();
        var visitor = UUID.randomUUID();
        var busy = approach("busy");
        assertTrue(claims.tryClaimAll(UUID.randomUUID(), List.of(busy), 0, 100));
        var result = HangoutApproaches.select(List.of(busy, approach("blocked")),
                candidate -> claims.available(candidate, visitor, 1), candidate -> UNREACHABLE);
        assertNull(result);
        assertEquals(1, claims.size());
        var nextSeatApproach = approach("next-seat");
        assertEquals(nextSeatApproach, HangoutApproaches.select(List.of(nextSeatApproach),
                candidate -> claims.available(candidate, visitor, 1), candidate -> REACHABLE));
    }

    @Test
    void reachableApproachesWinOverPartialPathsAndPartialPathsRemainAFallback() {
        assertEquals("reachable", HangoutApproaches.select(List.of("partial", "reachable"),
                candidate -> true, candidate -> candidate.equals("partial") ? PARTIAL : REACHABLE));
        assertEquals("partial", HangoutApproaches.select(List.of("blocked", "partial", "later"),
                candidate -> true, candidate -> candidate.equals("blocked") ? UNREACHABLE : PARTIAL));
        assertNull(HangoutApproaches.select(List.of("reserved-partial"),
                candidate -> false, candidate -> PARTIAL));
    }

    private static HangoutClaims.Key approach(String value) {
        return new HangoutClaims.Key("minecraft:overworld", "approach", value);
    }
}
