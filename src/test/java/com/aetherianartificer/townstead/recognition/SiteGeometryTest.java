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
    /** A world of solid cells; everything not named is open air, and all of it is loaded. */
    private static final class Grid {
        private final Set<Long> solid = new HashSet<>();
        private final Set<Long> unloaded = new HashSet<>();

        Grid solid(int x, int y, int z) {
            solid.add(SiteGeometry.pack(x, y, z));
            return this;
        }

        Grid ground(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) solid(x, y, z);
            }
            return this;
        }

        /** A hollow rectangle of fence at {@code y}, the usual shape of a paddock. */
        Grid ring(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) {
                solid(x, y, z0);
                solid(x, y, z1);
            }
            for (int z = z0; z <= z1; z++) {
                solid(x0, y, z);
                solid(x1, y, z);
            }
            return this;
        }

        Grid open(int x, int y, int z) {
            solid.remove(SiteGeometry.pack(x, y, z));
            return this;
        }

        Grid unloaded(int x, int y, int z) {
            unloaded.add(SiteGeometry.pack(x, y, z));
            return this;
        }

        SiteGeometry.CellTest loaded() {
            return (x, y, z) -> !unloaded.contains(SiteGeometry.pack(x, y, z));
        }

        SiteGeometry.CellTest openness() {
            return (x, y, z) -> !solid.contains(SiteGeometry.pack(x, y, z));
        }
    }

    /** Ground at y=63, a 7x7 fence ring at y=64, so the interior is the 5x5 inside it. */
    private static Grid paddock() {
        return new Grid().ground(0, 0, 20, 20, 63).ring(2, 2, 8, 8, 64);
    }

    private static SiteGeometry.Region fill(Grid grid, int x, int y, int z, int radius) {
        return SiteGeometry.enclosure(SiteGeometry.pack(x, y, z),
                grid.loaded(), grid.openness(), radius, 4096);
    }

    @Test
    void aClosedRingEnclosesItsInteriorAndKeepsItsFences() {
        SiteGeometry.Region region = fill(paddock(), 5, 64, 5, 32);

        assertEquals(SiteGeometry.Status.FOUND, region.status());
        assertEquals(25, region.interior(), "5x5 of standing room inside a 7x7 ring");
        // The ring comes back too, because its fences are the building's own ingredients.
        assertTrue(region.cells().contains(SiteGeometry.pack(2, 64, 5)));
        assertTrue(region.cells().contains(SiteGeometry.pack(8, 64, 5)));
        assertFalse(region.cells().contains(SiteGeometry.pack(9, 64, 5)));
    }

    @Test
    void aGapInTheRingIsNotAnEnclosure() {
        SiteGeometry.Region region = fill(paddock().open(5, 64, 2), 5, 64, 5, 32);

        assertEquals(SiteGeometry.Status.ESCAPED, region.status());
        assertTrue(region.cells().isEmpty());
    }

    @Test
    void openGroundIsNeverAnEnclosure() {
        Grid field = new Grid().ground(0, 0, 60, 60, 63);

        assertEquals(SiteGeometry.Status.ESCAPED, fill(field, 30, 64, 30, 16).status());
    }

    @Test
    void groundThatIsNotLoadedIsNotAVerdict() {
        SiteGeometry.Region region = fill(paddock().unloaded(5, 64, 6), 5, 64, 5, 32);

        // Distinct from ESCAPED: a building must not be demolished because a chunk was away.
        assertEquals(SiteGeometry.Status.UNLOADED, region.status());
    }

    @Test
    void anInteriorPastItsLimitIsNotAnEnclosure() {
        SiteGeometry.Region region = SiteGeometry.enclosure(SiteGeometry.pack(5, 64, 5),
                paddock().loaded(), paddock().openness(), 32, 8);

        assertEquals(SiteGeometry.Status.ESCAPED, region.status());
    }

    @Test
    void aRootBesideTheRingIsFoundBeforeOneAboveIt() {
        Grid grid = paddock();
        // Seeded from a fence, the way the tier pass seeds from a building's recorded blocks.
        List<Long> roots = SiteGeometry.enclosureRoots(
                List.of(SiteGeometry.pack(2, 64, 5)), grid.loaded(), grid.openness());

        assertEquals(SiteGeometry.Status.FOUND,
                SiteGeometry.enclosure(roots.getFirst(), grid.loaded(), grid.openness(), 32, 4096).status(),
                "the first root tried must be inside the ring, not on top of it");
    }

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

    @Test
    void aPaddockClaimsItsOwnFencesAndNotTheNeighbouringFarmsLine() {
        // The shape that shipped wrong: a small pen, and a fence line running away across the
        // village. A radius took in both; the site's own ground must take in only the pen.
        Grid grid = paddock();
        SiteGeometry.Region region = fill(grid, 5, 64, 5, 32);
        Set<Long> footprint = SiteGeometry.columnsNear(region.cells(), SiteRequirements.LINK);

        assertTrue(footprint.contains(SiteGeometry.column(2, 5)), "its own fence");
        for (int distance = 11; distance <= 24; distance++) {
            assertFalse(footprint.contains(SiteGeometry.column(distance, 5)),
                    "a fence " + distance + " blocks out belongs to whatever built it, not this pen");
        }
    }
}
