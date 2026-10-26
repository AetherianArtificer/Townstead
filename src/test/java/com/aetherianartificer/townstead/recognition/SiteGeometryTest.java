package com.aetherianartificer.townstead.recognition;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The extent rules, on a hand-built grid. These are the rules that decide how far an open-air
 * building reaches, which is what shipped wrong once and surfaced three layers away as villagers
 * wandering into a paddock, so they are checked here rather than inferred from a running world.
 */
class SiteGeometryTest {
    @Test
    void aDeckWalkFollowsItsOwnRunAndStopsAtTheShore() {
        // A jetty running out over water: eleven cells at y=64, nothing else qualifying.
        Set<Long> deck = new HashSet<>();
        for (int z = 0; z <= 10; z++) deck.add(SiteGeometry.pack(0, 64, z));
        // A second, separate jetty four blocks away must not join it.
        Set<Long> other = new HashSet<>();
        for (int z = 0; z <= 10; z++) other.add(SiteGeometry.pack(4, 64, z));
        Set<Long> all = new HashSet<>(deck);
        all.addAll(other);

        Set<Long> walked = SiteGeometry.surface(List.of(SiteGeometry.pack(0, 64, 5)),
                (x, y, z) -> all.contains(SiteGeometry.pack(x, y, z)), 2, 0, 5, 32, 4096);

        assertEquals(deck, walked);
    }

    @Test
    void aDeckWalkStepsOverAChangeInHeight() {
        Set<Long> deck = Set.of(
                SiteGeometry.pack(0, 64, 0), SiteGeometry.pack(0, 64, 1),
                SiteGeometry.pack(0, 65, 2), SiteGeometry.pack(0, 65, 3));

        Set<Long> walked = SiteGeometry.surface(List.of(SiteGeometry.pack(0, 64, 0)),
                (x, y, z) -> deck.contains(SiteGeometry.pack(x, y, z)), 2, 0, 0, 32, 4096);

        assertEquals(deck, walked);
    }

    @Test
    void belongingIsBeingAboveOrBesideTheGroundAtAnyHeight() {
        Set<Long> footprint = SiteGeometry.columnsNear(
                List.of(SiteGeometry.pack(5, 64, 5)), SiteRequirements.LINK);

        // A lantern on a mast over the site counts, however tall the mast is.
        assertTrue(footprint.contains(SiteGeometry.column(5, 5)));
        assertTrue(footprint.contains(SiteGeometry.column(7, 5)));
        // Anything past the link does not, which is what a radius failed to say.
        assertFalse(footprint.contains(SiteGeometry.column(8, 5)));
    }
}
