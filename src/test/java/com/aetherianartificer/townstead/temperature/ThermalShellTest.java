package com.aetherianartificer.townstead.temperature;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ThermalShellTest {
    private static final int STONE = ThermalWall.pack(ThermalConductance.Material.MASONRY, ThermalWall.FULL, false, false);
    private static final int WOOD = ThermalWall.pack(ThermalConductance.Material.WOOD, ThermalWall.FULL, false, false);
    private static final int OPEN_DOOR = ThermalWall.pack(ThermalConductance.Material.WOOD, ThermalWall.DOOR, true, true);
    private static final int CLOSED_DOOR = ThermalWall.pack(ThermalConductance.Material.WOOD, ThermalWall.DOOR, true, false);
    private static final double AIR = 1000;
    private static final ThermalWall.Table TABLE = new ThermalWall.Table(10, 1, 100, AIR, m -> 20000);
    /** Light walls, so a run reaches its true steady state inside one solve. */
    private static final ThermalWall.Table LIGHT = new ThermalWall.Table(10, 1, 100, AIR, m -> 200);

    /** A sparse world: anything unset reads as {@code fill}. */
    private static final class World implements ThermalShell.Cells {
        final Long2IntOpenHashMap blocks = new Long2IntOpenHashMap();
        final Long2LongOpenHashMap owners = new Long2LongOpenHashMap();
        int reads;
        World(int fill) { blocks.defaultReturnValue(fill); owners.defaultReturnValue(ThermalShell.NO_ROOM); }
        @Override public int read(long cell) { reads++; return blocks.get(cell); }
        @Override public long beyond(long from, long to) {
            return pack(2*x(to)-x(from), 2*y(to)-y(from), 2*z(to)-z(from));
        }
        @Override public long roomAt(long cell) { return owners.get(cell); }
        @Override public int x(long cell) { return ThermalShellTest.x(cell); }
        @Override public int y(long cell) { return ThermalShellTest.y(cell); }
        @Override public int z(long cell) { return ThermalShellTest.z(cell); }
        void box(int x0, int y0, int z0, int x1, int y1, int z1, int code) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) blocks.put(pack(x,y,z), code);
        }
    }
    private static long pack(int x, int y, int z) { return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512); }
    private static int x(long p) { return (int) (p >> 40) - 512; }
    private static int y(long p) { return (int) (p >> 20 & 0xFFFFF) - 512; }
    private static int z(long p) { return (int) (p & 0xFFFFF) - 512; }
    private static final int[][] DIRS = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    private record Room(long anchor, Set<Long> cells, List<ThermalRegionScan.Face> faces) {}
    private static Room room(World world, Set<Long> cells) {
        long anchor = Collections.min(cells);
        List<ThermalRegionScan.Face> faces = new ArrayList<>();
        for (long c : cells) {
            world.blocks.put(c, ThermalShell.INTERIOR);
            world.owners.put(c, anchor);
            for (var d : DIRS) {
                long n = pack(x(c)+d[0], y(c)+d[1], z(c)+d[2]);
                if (!cells.contains(n)) faces.add(new ThermalRegionScan.Face(c, n));
            }
        }
        return new Room(anchor, cells, faces);
    }
    private static Set<Long> box(int x0, int y0, int z0, int x1, int y1, int z1) {
        Set<Long> cells = new HashSet<>();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) cells.add(pack(x,y,z));
        return cells;
    }
    private static ThermalShell shell(World world, Room room) { return shell(world, room, TABLE, 0); }
    private static ThermalShell shell(World world, Room room, ThermalWall.Table table, double start) {
        ThermalShell shell = ThermalShell.build(room.anchor, room.faces, Set.of(), true, world, table);
        Arrays.fill(shell.temperature, start);
        return shell;
    }

    /** The network as RoomHeat assembles it: each pair's wall from the lower anchor's rays. */
    private record Net(List<ThermalNetwork.Node> nodes, List<ThermalNetwork.Link> links) {}
    private static Net lumped(List<Room> rooms, List<ThermalShell> shells, double[] air, double[] power, double outside) {
        List<ThermalNetwork.Node> nodes = new ArrayList<>();
        List<ThermalNetwork.Link> links = new ArrayList<>();
        Map<Long,Integer> index = new HashMap<>();
        for (int i = 0; i < rooms.size(); i++) {
            index.put(rooms.get(i).anchor, i);
            nodes.add(new ThermalNetwork.Node(rooms.get(i).cells.size()*AIR, air[i], power[i], shells.get(i).directOutside, outside));
        }
        for (int i = 0; i < rooms.size(); i++) {
            ThermalShell s = shells.get(i);
            for (int g = 0; g < s.groups; g++) {
                int other = s.endpoint[g] == ThermalShell.TO_ROOM ? index.getOrDefault(s.endpointRoom[g], -1) : -1;
                if (other >= 0 && other != i && rooms.get(other).anchor < rooms.get(i).anchor
                        && shells.get(other).neighbors.contains(rooms.get(i).anchor)) continue;
                double exposed = s.endpoint[g] == ThermalShell.TO_OUTSIDE || s.endpoint[g] == ThermalShell.TO_GROUND ? s.toEnd[g] : 0;
                int node = nodes.size();
                nodes.add(new ThermalNetwork.Node(s.capacity[g], s.temperature[g], 0, exposed, outside));
                links.add(new ThermalNetwork.Link(i, node, s.toRoom[g]));
                if (other >= 0) links.add(new ThermalNetwork.Link(node, other, s.toEnd[g]));
            }
        }
        return new Net(nodes, links);
    }

    /** The reference: one node per wall block, each physical face linked once, as the per-block network did. */
    private static Net perBlock(World world, List<Room> rooms, ThermalWall.Table table, double[] air, double[] power, double wallStart, double outside) {
        List<ThermalNetwork.Node> nodes = new ArrayList<>();
        List<ThermalNetwork.Link> links = new ArrayList<>();
        Map<Long,Integer> index = new HashMap<>(), solid = new HashMap<>();
        for (int i = 0; i < rooms.size(); i++) {
            index.put(rooms.get(i).anchor, i);
            nodes.add(new ThermalNetwork.Node(rooms.get(i).cells.size()*AIR, air[i], power[i], 0, outside));
        }
        Set<String> edges = new HashSet<>();
        double[] directOutside = new double[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            for (var face : rooms.get(i).faces) {
                long previous = face.inside(), pos = face.outside();
                int previousNode = i;
                double previousHalf = 0, tail = 0;
                for (int depth = 0; ; depth++) {
                    int code = world.blocks.get(pos);
                    if (code == ThermalShell.EXTERIOR || code == ThermalShell.INTERIOR) {
                        double g = previousHalf + tail > 0 ? 1 / (previousHalf + tail) : table.opening;
                        if (code == ThermalShell.EXTERIOR) {
                            if (edge(edges, previous, pos)) {
                                if (previousNode == i) directOutside[i] += g; else expose(nodes, previousNode, g, outside);
                            }
                        } else {
                            int other = index.getOrDefault(world.owners.get(pos), -1);
                            if (other >= 0 && previousNode != other && edge(edges, previous, pos)) links.add(new ThermalNetwork.Link(previousNode, other, g));
                        }
                        break;
                    }
                    if (depth == ThermalBoundary.MAX_DEPTH) { if (previousNode != i) expose(nodes, previousNode, 1 / (previousHalf + tail), outside); break; }
                    double half = .5 / table.conductance[code];
                    if (depth > 0) { tail += 2 * half; long stepTo = world.beyond(previous, pos); previous = pos; pos = stepTo; continue; }
                    Integer node = solid.get(pos);
                    if (node == null) {
                        node = nodes.size();
                        solid.put(pos, node);
                        nodes.add(new ThermalNetwork.Node(table.capacity[code], wallStart, 0, 0, outside));
                    }
                    if (edge(edges, previous, pos)) links.add(new ThermalNetwork.Link(previousNode, node, 1 / half));
                    previousNode = node; previousHalf = half;
                    long stepTo = world.beyond(previous, pos); previous = pos; pos = stepTo;
                }
            }
        }
        for (int i = 0; i < rooms.size(); i++) expose(nodes, i, directOutside[i], outside);
        return new Net(nodes, links);
    }
    private static boolean edge(Set<String> edges, long a, long b) { return edges.add(Math.min(a,b) + ":" + Math.max(a,b)); }
    private static void expose(List<ThermalNetwork.Node> nodes, int node, double g, double reservoir) {
        if (g <= 0) return;
        var old = nodes.get(node);
        double total = old.outsideConductance() + g;
        nodes.set(node, new ThermalNetwork.Node(old.capacity(), old.temperature(), old.power(), total,
                (old.outsideConductance()*old.outside() + g*reservoir)/total));
    }

    private static double[] run(Net net, double seconds) { return ThermalNetwork.advance(net.nodes, net.links, seconds).temperatures(); }

    private static World house() {
        // A stone house with a thick north wall, buried in nothing: air outside on every side.
        World world = new World(ThermalShell.EXTERIOR);
        world.box(-1,-1,-1, 9,5,9, STONE);
        world.box(-1,-1,10, 9,5,12, WOOD);
        return world;
    }

    @Test void lumpsReproduceTheSteadyFlowOfEveryRayExactly() {
        World world = house();
        Room room = room(world, box(0,0,0, 8,4,8));
        ThermalShell shell = shell(world, room, LIGHT, 20);
        double[] lumpedT = run(lumped(List.of(room), List.of(shell), new double[]{20}, new double[]{5000}, -5), 9000);
        double[] blockT = run(perBlock(world, List.of(room), LIGHT, new double[]{20}, new double[]{5000}, 20, -5), 9000);
        assertEquals(blockT[0], lumpedT[0], 1e-6);
    }

    @Test void aMixedRoomStaysWithinHalfADegreeThroughoutItsWarmup() {
        World world = house();
        Room room = room(world, box(0,0,0, 8,4,8));
        ThermalShell shell = shell(world, room);
        for (double seconds = 60; seconds <= 3600; seconds += 60) {
            double a = run(lumped(List.of(room), List.of(shell), new double[]{0}, new double[]{2500}, -5), seconds)[0];
            double b = run(perBlock(world, List.of(room), TABLE, new double[]{0}, new double[]{2500}, 0, -5), seconds)[0];
            assertEquals(b, a, 0.5, "after " + seconds + "s");
        }
    }

    @Test void aSharedWallConnectsTwoRoomsOnceAndBothReadAsTheReference() {
        World world = new World(ThermalShell.EXTERIOR);
        world.box(-1,-1,-1, 11,4,5, STONE);
        Room a = room(world, box(0,0,0, 4,3,4)), b = room(world, box(6,0,0, 10,3,4));
        ThermalShell sa = shell(world, a, LIGHT, 15), sb = shell(world, b, LIGHT, 15);
        assertTrue(sa.neighbors.contains(b.anchor));
        assertTrue(sb.neighbors.contains(a.anchor));
        double[] air = {30, 10}, power = {1000, 0};
        double[] x = run(lumped(List.of(a,b), List.of(sa,sb), air, power, 0), 10000);
        double[] y = run(perBlock(world, List.of(a,b), LIGHT, air, power, 15, 0), 10000);
        assertEquals(y[0], x[0], 1e-3);
        assertEquals(y[1], x[1], 1e-3);
    }

    @Test void aPillarInsideTheRoomStoresHeatButLeaksNone() {
        World world = new World(ThermalShell.EXTERIOR);
        Set<Long> cells = box(0,0,0, 4,0,4);
        cells.remove(pack(2,0,2));
        world.blocks.put(pack(2,0,2), STONE);
        world.box(-1,-1,-1, 5,1,-1, WOOD);
        Room room = room(world, cells);
        ThermalShell shell = shell(world, room, TABLE, 30);
        double[] t = run(lumped(List.of(room), List.of(shell), new double[]{30}, new double[]{0}, 30), 5000);
        assertEquals(30, t[0], 1e-9);
    }

    @Test void nodeCountFollowsMaterialsAndNeighborsNotSurface() {
        World small = new World(STONE), large = new World(STONE);
        ThermalShell a = shell(small, room(small, box(0,0,0, 3,3,3)));
        ThermalShell b = shell(large, room(large, box(0,0,0, 31,3,31)));
        assertEquals(a.groups, b.groups);
        assertTrue(b.groups <= 6, "buried stone is one lump per side: " + b.groups);
    }

    @Test void eachBuriedSideEndsInItsOwnRock() {
        World world = new World(STONE);
        ThermalShell shell = shell(world, room(world, box(0,0,0, 3,3,3)));
        int sides = 0;
        for (int i = 0; i < shell.groups; i++) {
            if (shell.endpoint[i] != ThermalShell.TO_GROUND) continue;
            sides++;
            double x = shell.endX[i], y = shell.endY[i], z = shell.endZ[i];
            boolean beyondOneFace = (x < 0 || x > 3 ? 1 : 0) + (y < 0 || y > 3 ? 1 : 0) + (z < 0 || z > 3 ? 1 : 0) == 1;
            assertTrue(beyondOneFace, "lump " + i + " ends at " + x + ", " + y + ", " + z);
            assertEquals(ThermalBoundary.MAX_DEPTH + 1, Math.max(Math.max(-x, x - 3), Math.max(Math.max(-y, y - 3), Math.max(-z, z - 3))), 1e-9,
                    "rays end where the read stops, past the wall");
        }
        assertEquals(6, sides);
    }

    @Test void onlyAThermalChangeOnARayStalesTheShell() {
        World world = house();
        Room room = room(world, box(0,0,0, 8,4,8));
        ThermalShell shell = shell(world, room);
        long wall = pack(-1,0,0);
        assertFalse(shell.changedBy(wall, STONE), "same class");
        assertTrue(shell.changedBy(wall, WOOD), "new material");
        assertTrue(shell.changedBy(wall, ThermalShell.EXTERIOR), "wall removed");
        assertFalse(shell.changedBy(pack(200,200,200), WOOD), "off every ray");
    }

    @Test void aSpaceThatChangesWithoutABlockUpdateIsFoundByTheCheck() {
        World world = house();
        Room room = room(world, box(0,0,0, 8,4,8));
        ThermalShell shell = shell(world, room);
        assertFalse(shell.spacesChanged(world));
        int reads = world.reads;
        shell.spacesChanged(world);
        assertTrue(world.reads - reads <= room.faces.size(), "the check reads one space per ray at most");
        // A roof rises over the air beyond the west wall: that air stops being outside.
        world.blocks.put(pack(-2,0,0), ThermalShell.INTERIOR);
        assertTrue(shell.spacesChanged(world));
    }

    @Test void aCutEdgeMeetsTheSpaceItsRoomIsInAndAWindowMeetsTheSky() {
        World world = new World(STONE);
        Room room = room(world, box(0,0,0, 4,0,4));
        long edge = pack(5,0,2), wall = pack(-1,0,2), sky = pack(-2,0,2);
        world.blocks.put(edge, ThermalShell.INTERIOR);
        world.blocks.put(wall, WOOD);
        world.blocks.put(sky, ThermalShell.EXTERIOR);
        ThermalShell shell = ThermalShell.build(room.anchor, room.faces, Set.of(edge), true, world, TABLE);
        assertEquals(TABLE.opening, shell.directSpace, 1e-9, "the cut edge is open to the same space");
        assertEquals(0, shell.directOutside, 1e-9);
        assertTrue(shell.reachesSky(), "the wall with sky behind it");
        boolean toSky = false;
        for (int i = 0; i < shell.groups; i++) toSky |= shell.endpoint[i] == ThermalShell.TO_OUTSIDE;
        assertTrue(toSky);

        World sealed = new World(STONE);
        Room buried = room(sealed, box(0,0,0, 4,0,4));
        assertFalse(shell(sealed, buried).reachesSky(), "buried walls never feel the sky");
    }

    @Test void readingInSlicesMatchesReadingAtOnce() {
        World world = house();
        Room room = room(world, box(0,0,0, 8,4,8));
        ThermalShell whole = shell(world, room);
        ThermalShell sliced = ThermalShell.begin(room.anchor, room.faces, Set.of(), true, world, TABLE);
        int slices = 1;
        while (!sliced.advance(7)) slices++;
        assertTrue(slices > 1);
        assertEquals(whole.groups, sliced.groups);
        for (int i = 0; i < whole.groups; i++) {
            assertEquals(whole.capacity[i], sliced.capacity[i]);
            assertEquals(whole.toRoom[i], sliced.toRoom[i], 1e-9);
            assertEquals(whole.toEnd[i], sliced.toEnd[i], 1e-9);
        }
    }

    private static ThermalRegionScan.Access access(World world) {
        return new ThermalRegionScan.Access() {
            @Override public ThermalRegionScan.Kind kind(long p) {
                return world.blocks.get(p) == ThermalShell.INTERIOR ? ThermalRegionScan.Kind.INTERIOR : ThermalRegionScan.Kind.BARRIER;
            }
            @Override public long[] neighbors(long p) {
                long[] out = new long[6];
                for (int i = 0; i < 6; i++) out[i] = pack(x(p)+DIRS[i][0], y(p)+DIRS[i][1], z(p)+DIRS[i][2]);
                return out;
            }
        };
    }

    @Test void aScanInSlicesFindsTheSameRoomAndNoticesWhatItRead() {
        World world = house();
        Room room = room(world, box(0,0,0, 8,4,8));
        var job = new ThermalRegionScan.Job(room.anchor, 4096, access(world), c -> false);
        int slices = 1;
        while (!job.advance(3)) slices++;
        assertTrue(slices > 1);
        assertEquals(room.cells, job.region().orElseThrow().cells());
        assertEquals(room.faces.size(), job.region().orElseThrow().faces().size());
        assertEquals(ThermalRegionScan.Kind.BARRIER, job.seen(pack(-1,0,0)));
        assertNull(job.seen(pack(200,200,200)));
    }

    @Test void aSpaceOverTheLimitBecomesAPartialRoomWithAFrontier() {
        World world = new World(STONE);
        Room room = room(world, box(0,0,0, 20,4,20));
        var job = new ThermalRegionScan.Job(room.anchor, 500, access(world), c -> false);
        while (!job.advance(16)) { }
        assertEquals(500, job.region().orElseThrow().cells().size());
        assertFalse(job.frontier().isEmpty());
    }

    @Test void anOpenedDoorIsItsOwnLumpWithAirNotWallMass() {
        World world = house();
        world.blocks.put(pack(-1,0,4), OPEN_DOOR);
        Room room = room(world, box(0,0,0, 8,4,8));
        ThermalShell open = shell(world, room);
        world.blocks.put(pack(-1,0,4), CLOSED_DOOR);
        ThermalShell closed = shell(world, room);
        assertEquals(AIR, java.util.stream.IntStream.range(0, open.groups).mapToDouble(i -> open.capacity[i]).filter(c -> c < 5000).sum());
        assertTrue(open.groups >= closed.groups);
    }

    /** The cap the live profile died under: 64 cave rooms of 4096 cells with rough walls. Prints what it measures. */
    @Test void capacityStaysFarBelowTheOldBudgetAtTheRoomCap() {
        Random random = new Random(42);
        World world = new World(STONE);
        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int ox = (i % 8) * 100 - 400, oz = (i / 8) * 100 - 400;
            Set<Long> cells = new HashSet<>();
            ArrayDeque<int[]> frontier = new ArrayDeque<>();
            frontier.add(new int[]{ox, 0, oz});
            while (cells.size() < 4096 && !frontier.isEmpty()) {
                int[] c = frontier.pollFirst();
                if (!cells.add(pack(c[0], c[1], c[2]))) continue;
                for (var d : DIRS) if (random.nextDouble() < 0.62 && Math.abs(c[1]+d[1]) < 40) frontier.add(new int[]{c[0]+d[0], c[1]+d[1], c[2]+d[2]});
            }
            rooms.add(room(world, cells));
        }
        int totalFaces = rooms.stream().mapToInt(r -> r.faces.size()).sum();
        // Warm up the compiler, as a long-running server would be.
        for (int warm = 0; warm < 8; warm++) for (Room r : rooms) shell(world, r);
        long worstRead = 0, sumRead = 0;
        List<ThermalShell> shells = new ArrayList<>();
        for (Room r : rooms) {
            long start = System.nanoTime();
            shells.add(shell(world, r));
            long took = System.nanoTime() - start;
            worstRead = Math.max(worstRead, took);
            sumRead += took;
        }
        double[] air = new double[64], power = new double[64];
        Arrays.fill(air, 12);
        power[0] = 5000;
        Net net = lumped(rooms, shells, air, power, 0);
        long worstStep = 0;
        for (int i = 0; i < 200; i++) {
            long start = System.nanoTime();
            ThermalNetwork.advance(net.nodes, net.links, 1);
            if (i >= 100) worstStep = Math.max(worstStep, System.nanoTime() - start);
        }
        // Slicing: the most a tick does is one check interval past its deadline.
        ThermalShell sliced = ThermalShell.begin(rooms.get(0).anchor, rooms.get(0).faces, Set.of(), true, world, TABLE);
        long worstSlice = 0;
        boolean done = false;
        while (!done) {
            long start = System.nanoTime();
            done = sliced.advance(64);
            worstSlice = Math.max(worstSlice, System.nanoTime() - start);
        }
        var access = new ThermalRegionScan.Access() {
            @Override public ThermalRegionScan.Kind kind(long p) {
                int code = world.blocks.get(p);
                return code == ThermalShell.INTERIOR ? ThermalRegionScan.Kind.INTERIOR : ThermalRegionScan.Kind.BARRIER;
            }
            @Override public long[] neighbors(long p) {
                long[] out = new long[6];
                for (int i = 0; i < 6; i++) out[i] = pack(x(p)+DIRS[i][0], y(p)+DIRS[i][1], z(p)+DIRS[i][2]);
                return out;
            }
        };
        for (int warm = 0; warm < 8; warm++) for (Room r : rooms) ThermalRegionScan.scan(r.anchor, 4096, access);
        long worstScan = 0;
        for (int i = 0; i < 64; i++) {
            long start = System.nanoTime();
            ThermalRegionScan.scan(rooms.get(i).anchor, 4096, access);
            worstScan = Math.max(worstScan, System.nanoTime() - start);
        }
        System.out.printf(Locale.ROOT, "cap: %d rooms, %d faces, %d nodes, %d links; read worst %.2f ms, mean %.2f ms; 64-face slice worst %.3f ms; solve step worst %.3f ms; room scan worst %.2f ms%n",
                rooms.size(), totalFaces, net.nodes.size(), net.links.size(), worstRead/1e6, sumRead/64/1e6, worstSlice/1e6, worstStep/1e6, worstScan/1e6);
        assertTrue(net.nodes.size() <= 64*9, "nodes " + net.nodes.size());
        assertTrue(totalFaces > 32768, "the scenario must be past the old per-block budget: " + totalFaces);
    }
}
