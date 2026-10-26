package com.aetherianartificer.townstead.recognition;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Village layouts that recognition has got wrong before, built from block facts: a field over a
 * cave pool read as a pier, a house read as a pen, a pen on a frozen lake missed. Each scenario
 * fails if the rule it guards is loosened.
 */
class RecognitionScenariosTest {
    private static final int MAX_DROP = 6;
    private static final int DOCK_CELLS = 5;

    /** What the rules read about a block. {@code top} is negative for no collision. */
    private record Block(double top, boolean full, String fluid, boolean barrier, boolean door,
                         boolean terrain, boolean natural) {
        static final Block AIR = new Block(-1, false, "none", false, false, false, false);
        static final Block WATER = new Block(-1, false, "water", false, false, false, false);
        static final Block DIRT = new Block(1, true, "none", false, false, true, false);
        static final Block STONE = new Block(1, true, "none", false, false, true, false);
        static final Block FARMLAND = new Block(0.9375, false, "none", false, false, true, false);
        static final Block PLANKS = new Block(1, true, "none", false, false, false, false);
        static final Block BRICKS = new Block(1, true, "none", false, false, false, false);
        static final Block ICE = new Block(1, true, "none", false, false, false, true);
        static final Block FENCE = new Block(1.5, false, "none", true, false, false, false);
        static final Block WALL = new Block(1.5, false, "none", true, false, false, false);
        static final Block DOOR = new Block(1, false, "none", false, true, false, false);
    }

    /** Air everywhere not set, all of it loaded. */
    private static final class Grid implements WalkEnclosure.World, SiteGeometry.DeckView {
        private final Map<Long, Block> blocks = new HashMap<>();

        Grid fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) blocks.put(SiteGeometry.pack(x, y, z), block);
                }
            }
            return this;
        }

        Grid set(int x, int y, int z, Block block) {
            return fill(x, y, z, x, y, z, block);
        }

        /** A hollow rectangle at {@code y}. */
        Grid ring(int x0, int z0, int x1, int z1, int y, Block block) {
            fill(x0, y, z0, x1, y, z0, block);
            fill(x0, y, z1, x1, y, z1, block);
            fill(x0, y, z0, x0, y, z1, block);
            return fill(x1, y, z0, x1, y, z1, block);
        }

        Block at(int x, int y, int z) {
            return blocks.getOrDefault(SiteGeometry.pack(x, y, z), Block.AIR);
        }

        @Override
        public int kind(int x, int y, int z) {
            Block b = at(x, y, z);
            return WalkEnclosure.classify(b.barrier(), b.door(), "lava".equals(b.fluid()),
                    "water".equals(b.fluid()), b.top(), b.terrain());
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return at(x, y, z).top() >= 0;
        }

        @Override
        public boolean full(int x, int y, int z) {
            return at(x, y, z).full();
        }

        @Override
        public boolean natural(int x, int y, int z) {
            return at(x, y, z).natural();
        }

        @Override
        public boolean liquid(int x, int y, int z) {
            return "water".equals(at(x, y, z).fluid());
        }

        @Override
        public boolean fluid(int x, int y, int z) {
            return !"none".equals(at(x, y, z).fluid());
        }
    }

    /** Dirt to y=62 over a wide area, so standing cells are at y=63. */
    private static Grid ground() {
        return new Grid().fill(-30, 55, -30, 30, 62, 30, Block.DIRT);
    }

    private static List<WalkEnclosure.Region> walks(Grid grid, int x, int y, int z) {
        LongArrayList seeds = new LongArrayList();
        seeds.add(SiteGeometry.pack(x, y, z));
        LongList roots = WalkEnclosure.roots(grid, seeds);
        List<WalkEnclosure.Region> regions = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            regions.add(WalkEnclosure.enclose(grid, roots.getLong(i), 32, 4096, null));
        }
        return regions;
    }

    private static boolean closed(List<WalkEnclosure.Region> regions) {
        return regions.stream().anyMatch(region -> region.status() == WalkEnclosure.Status.FOUND);
    }

    /** A connected deck big enough for the smallest dock, starting at the cell. */
    private static boolean dockAt(Grid grid, int x, int y, int z) {
        if (!SiteGeometry.isDeck(grid, x, y, z, MAX_DROP)) return false;
        Set<Long> deck = SiteGeometry.surface(List.of(SiteGeometry.pack(x, y, z)),
                (cx, cy, cz) -> SiteGeometry.isDeck(grid, cx, cy, cz, MAX_DROP), 0, x, z, 32, 8192);
        return deck.size() >= DOCK_CELLS && SiteGeometry.bordersOpenLiquid(grid, deck, MAX_DROP);
    }

    @Test
    void aFieldOverACavePoolIsNotAPier() {
        // Farmland on dirt, with a pool sealed four blocks down.
        Grid grid = ground().fill(0, 57, 0, 8, 59, 8, Block.WATER).fill(0, 62, 0, 8, 62, 8, Block.FARMLAND);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) assertFalse(SiteGeometry.isDeck(grid, x, 62, z, MAX_DROP));
        }
    }

    @Test
    void aDeckOverOpenWaterIsAPier() {
        // A lake cut into the ground, and a 3x4 plank deck over its edge.
        Grid grid = ground().fill(0, 58, 0, 10, 62, 10, Block.WATER).fill(0, 62, 0, 2, 62, 3, Block.PLANKS);
        assertTrue(dockAt(grid, 1, 62, 1));
    }

    @Test
    void aFloorOverASealedCisternIsNotAPier() {
        // The same deck over water, but the water is walled in under it on every side.
        Grid grid = ground().fill(0, 58, 0, 2, 61, 3, Block.WATER).fill(0, 62, 0, 2, 62, 3, Block.PLANKS);
        assertTrue(SiteGeometry.isDeck(grid, 1, 62, 1, MAX_DROP), "each plank is over water");
        assertFalse(dockAt(grid, 1, 62, 1), "but no open water meets the deck");
    }

    @Test
    void aHouseWithADoorIsNotAPen() {
        Grid grid = ground();
        for (int y = 63; y <= 65; y++) grid.ring(0, 0, 6, 6, y, Block.BRICKS);
        grid.fill(0, 66, 0, 6, 66, 6, Block.BRICKS).set(3, 63, 0, Block.DOOR).set(3, 64, 0, Block.DOOR);
        assertFalse(closed(walks(grid, 3, 63, 3)));
    }

    @Test
    void aWalledFieldIsAPenOnlyOnceItsGapIsClosed() {
        Grid grid = ground().ring(0, 0, 8, 8, 63, Block.WALL).set(4, 63, 8, Block.AIR);
        assertFalse(closed(walks(grid, 4, 63, 4)), "the gap lets an animal out");
        grid.set(4, 63, 8, Block.FENCE);
        assertTrue(closed(walks(grid, 4, 63, 4)));
    }

    /** The farmland of the field holding the seed, linked across gaps of up to two blocks. */
    private static Set<Long> field(Set<Long> farmland, int x, int y, int z) {
        return SiteGeometry.patch(List.of(SiteGeometry.pack(x, y, z)),
                (cx, cy, cz) -> farmland.contains(SiteGeometry.pack(cx, cy, cz)), 0, 2, x, z, 32, 8192);
    }

    private static Set<Long> rows(int x0, int z0, int x1, int z1, int y) {
        Set<Long> cells = new java.util.HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) cells.add(SiteGeometry.pack(x, y, z));
        }
        return cells;
    }

    @Test
    void aWaterChannelDoesNotSplitAField() {
        // Two blocks of rows either side of a one-block channel: one field.
        Set<Long> farmland = rows(0, 0, 8, 3, 62);
        farmland.addAll(rows(0, 5, 8, 8, 62));
        assertTrue(field(farmland, 0, 62, 0).size() == farmland.size());
    }

    @Test
    void aFieldAcrossThePathIsAnotherFarm() {
        // A four-block lane between two fields keeps them two farms.
        Set<Long> farmland = rows(0, 0, 8, 3, 62);
        farmland.addAll(rows(0, 8, 8, 11, 62));
        assertTrue(field(farmland, 0, 62, 0).size() == 9 * 4);
    }

    @Test
    void overlappingCandidatesKeepOnlyTheBest() {
        // Ranked best first: a farm, a pen on most of the same ground, and a pen of its own.
        record Site(String name, Set<Long> ground) {}
        Site farm = new Site("farm", rows(0, 0, 9, 9, 0));
        Site fencedFarm = new Site("pen", rows(1, 1, 9, 9, 0));
        Site pen = new Site("pen", rows(20, 0, 25, 5, 0));
        List<Site> kept = SiteGeometry.select(List.of(farm, fencedFarm, pen),
                (better, worse) -> SiteGeometry.overlapShare(better.ground(), worse.ground()) > 0.5);
        assertTrue(kept.equals(List.of(farm, pen)), "kept " + kept.stream().map(Site::name).toList());
    }

    @Test
    void neighbouringPensSharingAFenceAreBothKept() {
        // Two 7x7 pens (ring included) sharing their middle fence line: one shared column in seven.
        Set<Long> west = rows(0, 0, 6, 6, 0);
        Set<Long> east = rows(6, 0, 12, 6, 0);
        assertTrue(SiteGeometry.overlapShare(west, east) < 0.5);
        assertTrue(SiteGeometry.select(List.of(west, east),
                (better, worse) -> SiteGeometry.overlapShare(better, worse) > 0.5).size() == 2);
    }

    /** The ground a villager can walk from the cell, as the ground site walks it. */
    private static Set<Long> plaza(Grid grid, int x, int y, int z) {
        return SiteGeometry.surface(List.of(SiteGeometry.pack(x, y, z)),
                (cx, cy, cz) -> WalkEnclosure.walkable(grid, cx, cy, cz), 0, x, z, 16, 8192);
    }

    @Test
    void aStallBehindAWallIsNotOnTheSquare() {
        // A plaza with a wall across it, the wall running right to the reach's edge.
        Grid grid = ground();
        for (int y = 63; y <= 65; y++) grid.fill(-20, y, 5, 20, y, 5, Block.BRICKS);
        Set<Long> square = plaza(grid, 0, 63, 0);
        assertTrue(square.contains(SiteGeometry.pack(0, 63, 4)), "the near side of the wall");
        assertFalse(square.contains(SiteGeometry.pack(0, 63, 6)), "the far side is another place");
    }

    @Test
    void theSquareDoesNotCrossWaterOrAFence() {
        Grid grid = ground().fill(-20, 59, 5, 20, 62, 7, Block.WATER).fill(-20, 63, -5, 20, 63, -5, Block.FENCE);
        Set<Long> square = plaza(grid, 0, 63, 0);
        assertFalse(square.contains(SiteGeometry.pack(0, 63, 9)), "across the stream");
        assertFalse(square.contains(SiteGeometry.pack(0, 63, -7)), "past the fence");
        assertTrue(square.contains(SiteGeometry.pack(0, 63, 4)));
    }

    @Test
    void aCavernIsNotABuildingButADugInRoomIs() {
        assertTrue(CaveRooms.isCavern(95, 100, 4000), "a big room under rock");
        assertFalse(CaveRooms.isCavern(95, 100, 300), "a small room dug into a hillside");
        assertFalse(CaveRooms.isCavern(80, 100, 4000), "a big hall under a built roof, partly");
        assertFalse(CaveRooms.isCavern(0, 0, 4000), "no roof read");
    }

    @Test
    void onlySomeoneAmidFencesIsBuildingAPen() {
        // A ring with its gate missing surrounds whoever stands in it.
        Grid pen = ground().ring(0, 0, 8, 8, 63, Block.FENCE).set(4, 63, 8, Block.AIR);
        assertTrue(WalkEnclosure.fencedSides(pen, 4, 63, 4, 16) >= 3);
        // A field beside one fence line, and a house, surround nobody.
        Grid field = ground().fill(-10, 63, 5, 10, 63, 5, Block.FENCE);
        assertTrue(WalkEnclosure.fencedSides(field, 0, 63, 0, 16) == 1);
        Grid house = ground();
        for (int y = 63; y <= 65; y++) house.ring(0, 0, 6, 6, y, Block.BRICKS);
        house.ring(-3, -3, 9, 9, 63, Block.FENCE);
        assertTrue(WalkEnclosure.fencedSides(house, 3, 63, 3, 16) == 0, "the walls hide the yard fence");
    }

    @Test
    void aPenOnAFrozenLakeIsClosed() {
        Grid grid = new Grid().fill(-30, 58, -30, 30, 61, 30, Block.WATER).fill(-30, 62, -30, 30, 62, 30, Block.ICE)
                .ring(0, 0, 5, 8, 63, Block.FENCE);
        List<WalkEnclosure.Region> regions = walks(grid, 2, 63, 4);
        assertTrue(closed(regions));
        WalkEnclosure.Region pen = regions.stream()
                .filter(region -> region.status() == WalkEnclosure.Status.FOUND).findFirst().orElseThrow();
        assertTrue(pen.interior().size() == 4 * 7, "the ring's own ground, was " + pen.interior().size());
    }
}
