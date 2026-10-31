package com.aetherianartificer.townstead.recognition;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aetherianartificer.townstead.recognition.WalkEnclosure.BARRIER;
import static com.aetherianartificer.townstead.recognition.WalkEnclosure.GROUND;
import static com.aetherianartificer.townstead.recognition.WalkEnclosure.SOLID;
import static com.aetherianartificer.townstead.recognition.WalkEnclosure.UNLOADED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The walk rules on hand-built ground. The old single-level fill passed these flat cases and
 * failed the slope, which is the shape most real pens stand on.
 */
class WalkEnclosureTest {
    /** Everything not named is open air, and all of it is loaded. */
    private static final class Grid implements WalkEnclosure.World {
        private final Map<Long, Integer> kinds = new HashMap<>();
        int reads;

        Grid set(int x, int y, int z, int kind) {
            kinds.put(SiteGeometry.pack(x, y, z), kind);
            return this;
        }

        Grid clear(int x, int y, int z) {
            kinds.remove(SiteGeometry.pack(x, y, z));
            return this;
        }

        Grid ground(int x0, int z0, int x1, int z1, int y) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) set(x, y, z, GROUND);
            }
            return this;
        }

        /** A hollow rectangle at {@code y}. */
        Grid ring(int x0, int z0, int x1, int z1, int y, int kind) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, kind);
                set(x, y, z1, kind);
            }
            for (int z = z0; z <= z1; z++) {
                set(x0, y, z, kind);
                set(x1, y, z, kind);
            }
            return this;
        }

        @Override
        public int kind(int x, int y, int z) {
            reads++;
            return kinds.getOrDefault(SiteGeometry.pack(x, y, z), WalkEnclosure.OPEN);
        }
    }

    /** Ground at y=63, a 7x7 fence ring at y=64, so the inside is the 5x5 within it. */
    private static Grid paddock() {
        return new Grid().ground(-20, -20, 30, 30, 63).ring(2, 2, 8, 8, 64, BARRIER);
    }

    private static WalkEnclosure.Region walk(Grid grid, int x, int y, int z) {
        return WalkEnclosure.enclose(grid, SiteGeometry.pack(x, y, z), 32, 4096, null);
    }

    @Test
    void aClosedRingEnclosesItsInteriorAndKeepsItsFences() {
        WalkEnclosure.Region region = walk(paddock(), 5, 64, 5);

        assertEquals(WalkEnclosure.Status.FOUND, region.status());
        assertEquals(25, region.interior().size());
        assertTrue(region.ring().contains(SiteGeometry.pack(2, 64, 5)));
        assertTrue(region.ring().contains(SiteGeometry.pack(8, 64, 5)));
        assertFalse(region.cells().contains(SiteGeometry.pack(9, 64, 5)));
    }

    @Test
    void aGapIsOpenAndTheWalkSaysWhere() {
        WalkEnclosure.Region region = walk(paddock().clear(5, 64, 2), 5, 64, 5);

        assertEquals(WalkEnclosure.Status.ESCAPED, region.status());
        assertEquals(SiteGeometry.pack(5, 64, 2), region.gap(), "the missing fence");
    }

    @Test
    void aWideGapIsFoundAtItsEdgeOfThePen() {
        Grid grid = paddock().clear(4, 64, 8).clear(5, 64, 8).clear(6, 64, 8);
        WalkEnclosure.Region region = walk(grid, 5, 64, 5);

        assertEquals(WalkEnclosure.Status.ESCAPED, region.status());
        assertEquals(8, SiteGeometry.z(region.gap()));
    }

    @Test
    void openGroundIsNeverAnEnclosure() {
        Grid field = new Grid().ground(-40, -40, 40, 40, 63);

        assertEquals(WalkEnclosure.Status.ESCAPED, walk(field, 0, 64, 0).status());
    }

    @Test
    void groundThatIsNotLoadedIsNotAVerdict() {
        WalkEnclosure.Region region = walk(paddock().set(5, 64, 6, UNLOADED), 5, 64, 5);

        assertEquals(WalkEnclosure.Status.UNLOADED, region.status());
    }

    @Test
    void anInteriorPastItsLimitIsTooLarge() {
        WalkEnclosure.Region region = WalkEnclosure.enclose(paddock(), SiteGeometry.pack(5, 64, 5), 32, 8, null);

        assertEquals(WalkEnclosure.Status.TOO_LARGE, region.status());
    }

    @Test
    void aPenOnASlopeIsClosed() {
        // The ground climbs one block at x=5; the fence follows it, standing on each step.
        Grid grid = new Grid();
        for (int x = -20; x <= 30; x++) {
            int top = x >= 5 ? 64 : 63;
            for (int z = -20; z <= 30; z++) {
                for (int y = 60; y <= top; y++) grid.set(x, y, z, GROUND);
            }
        }
        for (int x = 2; x <= 8; x++) {
            int fenceY = x >= 5 ? 65 : 64;
            grid.set(x, fenceY, 2, BARRIER).set(x, fenceY, 8, BARRIER);
        }
        for (int z = 2; z <= 8; z++) grid.set(2, 64, z, BARRIER).set(8, 65, z, BARRIER);

        WalkEnclosure.Region region = walk(grid, 3, 64, 5);

        assertEquals(WalkEnclosure.Status.FOUND, region.status(), "both levels belong to one pen");
        assertEquals(25, region.interior().size());
    }

    @Test
    void aFenceDownhillStillStopsTheWalk() {
        // The pen sits on a ledge; its far fence stands a block lower than the ledge.
        Grid grid = new Grid().ground(-20, -20, 30, 30, 62);
        grid.ground(-20, -20, 7, 30, 63);
        grid.ring(2, 2, 7, 8, 64, BARRIER);
        for (int z = 3; z <= 7; z++) grid.set(7, 64, z, WalkEnclosure.OPEN).set(8, 63, z, BARRIER);

        WalkEnclosure.Region region = walk(grid, 4, 64, 5);

        assertEquals(WalkEnclosure.Status.FOUND, region.status());
    }

    @Test
    void aPlacedRowOneBlockHighIsAWall() {
        Grid grid = paddock();
        for (int x = 2; x <= 8; x++) grid.set(x, 64, 2, SOLID);

        assertEquals(WalkEnclosure.Status.FOUND, walk(grid, 5, 64, 5).status());
    }

    @Test
    void aNaturalRidgeOneBlockHighIsClimbed() {
        Grid grid = paddock();
        for (int x = 2; x <= 8; x++) grid.set(x, 64, 2, GROUND);

        assertEquals(WalkEnclosure.Status.ESCAPED, walk(grid, 5, 64, 5).status());
    }

    @Test
    void naturalGroundTwoBlocksHighIsAWall() {
        Grid grid = paddock();
        for (int x = 2; x <= 8; x++) grid.set(x, 64, 2, GROUND).set(x, 65, 2, GROUND);

        assertEquals(WalkEnclosure.Status.FOUND, walk(grid, 5, 64, 5).status());
    }

    @Test
    void fencesTouchingOnlyAtACornerCloseTheRing() {
        // A diamond: every fence meets the next one corner to corner.
        Grid grid = new Grid().ground(-20, -20, 20, 20, 63);
        int r = 4;
        for (int i = 0; i <= r; i++) {
            grid.set(i, 64, r - i, BARRIER).set(-i, 64, r - i, BARRIER)
                    .set(i, 64, i - r, BARRIER).set(-i, 64, i - r, BARRIER);
        }

        WalkEnclosure.Region region = walk(grid, 0, 64, 0);

        assertEquals(WalkEnclosure.Status.FOUND, region.status());
    }

    @Test
    void walkingOffIntoNothingIsGettingOut() {
        Grid grid = new Grid().ground(0, 0, 4, 4, 63);

        assertEquals(WalkEnclosure.Status.ESCAPED, walk(grid, 2, 64, 2).status());
    }

    @Test
    void leavingTheBoundsOfAKnownPenIsGettingOutAtOnce() {
        Grid grid = paddock().clear(5, 64, 2);
        WalkEnclosure.Region region = WalkEnclosure.enclose(grid, SiteGeometry.pack(5, 64, 5), 32, 4096,
                new WalkEnclosure.Bounds(1, 1, 9, 9));

        assertEquals(WalkEnclosure.Status.ESCAPED, region.status());
        assertTrue(grid.reads < 1000, "stopped at the pen's edge, not the walk's radius: " + grid.reads);
    }

    @Test
    void aRootBesideTheRingIsFoundFirst() {
        Grid grid = paddock();
        LongList roots = WalkEnclosure.roots(grid, LongArrayList.of(SiteGeometry.pack(2, 64, 5)));

        assertEquals(WalkEnclosure.Status.FOUND,
                WalkEnclosure.enclose(grid, roots.getLong(0), 32, 4096, null).status(),
                "the first root tried must be inside the ring, not outside it");
    }

    @Test
    void extendingAFenceLineCannotCloseAnything() {
        Grid grid = new Grid().ground(-20, -20, 20, 20, 63);
        for (int z = 0; z <= 5; z++) grid.set(0, 64, z, BARRIER);

        assertEquals(1, WalkEnclosure.localSides(grid, 0, 64, 5).size());
    }

    @Test
    void theLastFenceOfARingPartsTheGround() {
        Grid grid = paddock();

        LongList sides = WalkEnclosure.localSides(grid, 5, 64, 2);

        assertEquals(2, sides.size(), "inside and outside");
    }

    @Test
    void closingARingFindsThePenForAboutItsOwnSize() {
        Grid grid = new Grid().ground(-60, -60, 60, 60, 63).ring(2, 2, 8, 8, 64, BARRIER);
        LongList sides = WalkEnclosure.localSides(grid, 5, 64, 2);
        grid.reads = 0;

        List<WalkEnclosure.Region> closed = WalkEnclosure.closedSides(grid, sides, 48, 4096);

        assertEquals(1, closed.size());
        assertEquals(WalkEnclosure.Status.FOUND, closed.get(0).status());
        assertEquals(25, closed.get(0).interior().size());
        assertTrue(grid.reads < 2000, "the open side stops once the closed one settles: " + grid.reads);
    }

    @Test
    void sidesThatMeetCloseNothing() {
        Grid grid = paddock().clear(5, 64, 8);
        LongList sides = WalkEnclosure.localSides(grid, 5, 64, 2);

        assertTrue(WalkEnclosure.closedSides(grid, sides, 32, 4096).isEmpty());
    }

    @Test
    void aPaddockClaimsItsOwnFencesAndNotTheNeighbouringFarmsLine() {
        Grid grid = paddock();
        for (int x = 11; x <= 24; x++) grid.set(x, 64, 5, BARRIER);
        WalkEnclosure.Region region = walk(grid, 5, 64, 5);
        Set<Long> cells = new HashSet<>(region.cells());
        Set<Long> footprint = SiteGeometry.columnsNear(cells, SiteRequirements.LINK);

        assertTrue(footprint.contains(SiteGeometry.column(2, 5)), "its own fence");
        for (int distance = 11; distance <= 24; distance++) {
            assertFalse(footprint.contains(SiteGeometry.column(distance, 5)),
                    "a fence " + distance + " blocks out belongs to whatever built it, not this pen");
        }
    }
}
