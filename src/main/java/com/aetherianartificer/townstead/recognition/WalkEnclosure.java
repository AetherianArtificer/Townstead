package com.aetherianartificer.townstead.recognition;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Enclosure as a grazing animal meets it: the ground it can reach from where it stands. Like
 * {@link SiteGeometry}, it works over packed cells with no world types, so the rules are tested
 * directly.
 *
 * <p>Movement follows a mob's own limits. It walks level, auto-steps anything up to a slab high,
 * jumps one block onto natural ground, and drops any height. It never crosses a fence, wall or
 * gate, walks through doors, and never climbs a placed block, so a row of hay is a wall while a hillside is a slope.
 * Moves are straight only: vanilla pathing cuts a corner only when both sides of it are open, and
 * then the straight route exists too, so fences that touch at a corner still close a ring.</p>
 */
public final class WalkEnclosure {
    /** Nothing to collide with: air, grass, flowers. Needs ground beneath to stand in. */
    public static final int OPEN = 0;
    /** Swum through, and holds an animal up. */
    public static final int WATER = 1;
    /** Stepped onto without a jump: a slab, a carpet, a bed. */
    public static final int LOW = 2;
    /** Natural ground, climbed one block at a time. */
    public static final int GROUND = 3;
    /** Any other solid block: a wall at any height, but something to stand on. */
    public static final int SOLID = 4;
    /** Fences, walls and gates: never crossed and never stood on. */
    public static final int BARRIER = 5;
    public static final int UNLOADED = 6;

    public static final long NONE = Long.MIN_VALUE;
    private static final int MAX_DROP = 32;
    private static final int MIN_HARD_CAP = 8192;
    private static final long BLOCKED = Long.MIN_VALUE + 1;
    private static final long FELL = Long.MIN_VALUE + 2;
    private static final long NOT_LOADED = Long.MIN_VALUE + 3;
    private static final int[] DX = {0, 0, 1, -1};
    private static final int[] DZ = {-1, 1, 0, 0};
    /** Ground first: a seed is usually part of the ring, and the cell beside it is the inside. */
    private static final int[] ROOT_HEIGHTS = {0, -1, 1, 2};
    private static final int[] ROOT_DX = {0, 0, 0, 1, -1};
    private static final int[] ROOT_DZ = {0, -1, 1, 0, 0};

    /** A mob's step height: anything this low is walked onto without a jump. */
    private static final double STEP_HEIGHT = 0.6;

    /**
     * What a block is to a grazing animal, from the few facts about it that matter. The world
     * adapter and the save fixtures both read blocks through this, so the order of the tests is
     * itself under test. {@code collisionTop} is negative for a block with nothing to collide with.
     */
    public static int classify(boolean barrier, boolean door, boolean lava, boolean water,
                               double collisionTop, boolean terrain) {
        // A gate left open still walls a paddock in, so the ring is read from tags.
        if (barrier) return BARRIER;
        // A door is a building's way in, not a pen's wall: a room is never a paddock.
        if (door) return OPEN;
        if (lava) return BARRIER;
        if (collisionTop < 0) return water ? WATER : OPEN;
        if (collisionTop > 1.0) return BARRIER;
        if (collisionTop <= STEP_HEIGHT) return LOW;
        return terrain ? GROUND : SOLID;
    }

    /** How the world reads to a grazing animal, one cell at a time. */
    @FunctionalInterface
    public interface World {
        int kind(int x, int y, int z);
    }

    public enum Status {
        /** The walk ran out of ground without getting out. */
        FOUND,
        /** The walk got out; {@link Region#gap()} says where it left. */
        ESCAPED,
        /** Closed, or close to it, but bigger than the building allows. */
        TOO_LARGE,
        /** The walk reached ground that is not loaded; nothing can be concluded. */
        UNLOADED
    }

    /** Horizontal box a known site may not leave. Leaving it is getting out. */
    public record Bounds(int minX, int minZ, int maxX, int maxZ) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    /**
     * Interior is where an animal can stand; ring is what stopped it, at the height it stood.
     * Both come back on FOUND, because a pen's fences are its own ingredients.
     */
    public record Region(Status status, LongSet interior, LongSet ring, long gap) {
        static final Region NOT_LOADED_REGION = new Region(Status.UNLOADED, new LongOpenHashSet(),
                new LongOpenHashSet(), NONE);

        public LongSet cells() {
            LongOpenHashSet all = new LongOpenHashSet(interior);
            all.addAll(ring);
            return all;
        }
    }

    private WalkEnclosure() {}

    public static boolean standable(World world, int x, int y, int z) {
        int here = world.kind(x, y, z);
        if (here == WATER || here == LOW) return true;
        if (here != OPEN) return false;
        int below = world.kind(x, y - 1, z);
        return below == GROUND || below == SOLID;
    }

    /**
     * Dry standing room for a villager: firm footing and headroom, never in water. What a
     * plaza's ground is made of, where {@link #standable} also lets an animal swim.
     */
    public static boolean walkable(World world, int x, int y, int z) {
        int here = world.kind(x, y, z);
        if (here != OPEN && here != LOW) return false;
        if (here == OPEN) {
            int below = world.kind(x, y - 1, z);
            if (below != GROUND && below != SOLID) return false;
        }
        return world.kind(x, y + 1, z) == OPEN;
    }

    /**
     * On how many of the four sides a fence, wall or gate stands within {@code reach}, looking
     * straight out from the cell: what tells a pen being built from a field with a fence beside it.
     * A solid wall ends the look on that side, so the inside of a house is surrounded by nothing.
     */
    public static int fencedSides(World world, int x, int y, int z, int reach) {
        int sides = 0;
        for (int i = 0; i < DX.length; i++) {
            look:
            for (int d = 1; d <= reach; d++) {
                int lx = x + DX[i] * d;
                int lz = z + DZ[i] * d;
                for (int dy = -1; dy <= 1; dy++) {
                    if (world.kind(lx, y + dy, lz) == BARRIER) {
                        sides++;
                        break look;
                    }
                }
                int feet = world.kind(lx, y, lz);
                int head = world.kind(lx, y + 1, lz);
                if ((feet == SOLID || feet == GROUND || feet == UNLOADED)
                        && (head == SOLID || head == GROUND || head == UNLOADED)) break;
            }
        }
        return sides;
    }

    /** Standing room at or beside each seed, best candidates first. */
    public static LongList roots(World world, LongCollection seeds) {
        LongList roots = new LongArrayList();
        LongOpenHashSet seen = new LongOpenHashSet();
        var it = seeds.iterator();
        while (it.hasNext()) {
            long seed = it.nextLong();
            int sx = SiteGeometry.x(seed);
            int sy = SiteGeometry.y(seed);
            int sz = SiteGeometry.z(seed);
            for (int dy : ROOT_HEIGHTS) {
                for (int i = 0; i < ROOT_DX.length; i++) {
                    int px = sx + ROOT_DX[i];
                    int py = sy + dy;
                    int pz = sz + ROOT_DZ[i];
                    long cell = SiteGeometry.pack(px, py, pz);
                    if (seen.add(cell) && standable(world, px, py, pz)) roots.add(cell);
                }
            }
        }
        return roots;
    }

    /**
     * Everything an animal standing at {@code root} can reach. Getting farther than {@code radius}
     * from the root, out of {@code bounds}, or off a drop with nothing under it is getting out.
     */
    public static Region enclose(World world, long root, int radius, int maxInterior, Bounds bounds) {
        Walker walker = new Walker(world);
        int rootX = SiteGeometry.x(root);
        int rootZ = SiteGeometry.z(root);
        int hardCap = Math.max(MIN_HARD_CAP, maxInterior * 2);
        LongOpenHashSet interior = new LongOpenHashSet();
        LongOpenHashSet ring = new LongOpenHashSet();
        Long2LongOpenHashMap parent = new Long2LongOpenHashMap();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        interior.add(root);
        queue.enqueue(root);
        while (!queue.isEmpty()) {
            if (interior.size() > hardCap) return new Region(Status.TOO_LARGE, interior, ring, NONE);
            long current = queue.dequeueLong();
            int cx = SiteGeometry.x(current);
            int cy = SiteGeometry.y(current);
            int cz = SiteGeometry.z(current);
            for (int i = 0; i < DX.length; i++) {
                int nx = cx + DX[i];
                int nz = cz + DZ[i];
                boolean out = Math.abs(nx - rootX) > radius || Math.abs(nz - rootZ) > radius
                        || (bounds != null && !bounds.contains(nx, nz));
                long next = out ? FELL : walker.step(cx, cy, cz, nx, nz);
                if (next == NOT_LOADED) return Region.NOT_LOADED_REGION;
                if (next == BLOCKED) {
                    ring.add(SiteGeometry.pack(nx, walker.blockedY, nz));
                } else if (next == FELL) {
                    return new Region(Status.ESCAPED, interior, ring,
                            gapOf(pathTo(parent, root, current), ring));
                } else if (interior.add(next)) {
                    parent.put(next, current);
                    queue.enqueue(next);
                }
            }
        }
        Status status = interior.size() > maxInterior ? Status.TOO_LARGE : Status.FOUND;
        return new Region(status, interior, ring, NONE);
    }

    /**
     * The sides a change may have parted: standing cells around a column, grouped by whether they
     * still reach one another without leaving the 3x3 columns around it. One group means nothing
     * can have closed, whatever the change was. Several give one cell each, for
     * {@link #closedSides}.
     */
    public static LongList localSides(World world, int x, int y, int z) {
        LongArrayList cells = new LongArrayList();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (standable(world, x + dx, y + dy, z + dz)) cells.add(SiteGeometry.pack(x + dx, y + dy, z + dz));
                }
            }
        }
        if (cells.size() < 2) return cells;
        Long2IntOpenHashMap index = new Long2IntOpenHashMap();
        index.defaultReturnValue(-1);
        for (int i = 0; i < cells.size(); i++) index.put(cells.getLong(i), i);
        int[] parent = new int[cells.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        Walker walker = new Walker(world);
        for (int i = 0; i < cells.size(); i++) {
            long cell = cells.getLong(i);
            int cx = SiteGeometry.x(cell);
            int cy = SiteGeometry.y(cell);
            int cz = SiteGeometry.z(cell);
            for (int d = 0; d < DX.length; d++) {
                int nx = cx + DX[d];
                int nz = cz + DZ[d];
                if (Math.abs(nx - x) > 1 || Math.abs(nz - z) > 1) continue;
                long next = walker.step(cx, cy, cz, nx, nz);
                if (next == BLOCKED || next == FELL || next == NOT_LOADED) continue;
                int j = index.get(next);
                if (j >= 0) union(parent, i, j);
            }
        }
        LongArrayList sides = new LongArrayList();
        boolean[] taken = new boolean[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            int group = find(parent, i);
            if (taken[group]) continue;
            taken[group] = true;
            sides.add(cells.getLong(i));
        }
        return sides;
    }

    /**
     * Walks every side at once, a step each in turn, and stops each side as soon as it is
     * settled: two sides meeting become one, a side that gets out is open, and a side that runs
     * out of ground is closed. Taking turns is what keeps this cheap (the trick from Even and
     * Shiloach's on-line edge deletion): a closed side costs about its own size however large the
     * open side beyond it is. Once one undecided side is left, the walk stops, since that side was
     * already connected to the others before the change.
     */
    public static List<Region> closedSides(World world, LongList sides, int radius, int maxInterior) {
        int n = sides.size();
        List<Region> closed = new ArrayList<>();
        if (n < 2) return closed;
        int hardCap = Math.max(MIN_HARD_CAP, maxInterior * 2);
        Walker walker = new Walker(world);
        int[] group = new int[n];
        LongArrayFIFOQueue[] queues = new LongArrayFIFOQueue[n];
        LongOpenHashSet[] interiors = new LongOpenHashSet[n];
        LongOpenHashSet[] rings = new LongOpenHashSet[n];
        boolean[] settled = new boolean[n];
        Long2IntOpenHashMap owner = new Long2IntOpenHashMap();
        owner.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            group[i] = i;
            queues[i] = new LongArrayFIFOQueue();
            interiors[i] = new LongOpenHashSet();
            rings[i] = new LongOpenHashSet();
            long side = sides.getLong(i);
            int previous = owner.putIfAbsent(side, i);
            if (previous >= 0) {
                union(group, previous, i);
                continue;
            }
            interiors[i].add(side);
            queues[i].enqueue(side);
        }

        while (true) {
            IntArrayList open = undecided(group, settled);
            if (open.size() <= 1) break;
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                int g = find(group, i);
                if (settled[g] || queues[i].isEmpty()) continue;
                moved = true;
                long current = queues[i].dequeueLong();
                int cx = SiteGeometry.x(current);
                int cy = SiteGeometry.y(current);
                int cz = SiteGeometry.z(current);
                long origin = sides.getLong(i);
                for (int d = 0; d < DX.length && !settled[g]; d++) {
                    int nx = cx + DX[d];
                    int nz = cz + DZ[d];
                    boolean out = Math.abs(nx - SiteGeometry.x(origin)) > radius
                            || Math.abs(nz - SiteGeometry.z(origin)) > radius;
                    long next = out ? FELL : walker.step(cx, cy, cz, nx, nz);
                    if (next == FELL || next == NOT_LOADED) {
                        settled[g] = true;
                    } else if (next == BLOCKED) {
                        rings[i].add(SiteGeometry.pack(nx, walker.blockedY, nz));
                    } else {
                        int other = owner.putIfAbsent(next, i);
                        if (other < 0) {
                            interiors[i].add(next);
                            queues[i].enqueue(next);
                        } else {
                            int og = find(group, other);
                            if (og != g) {
                                g = union(group, g, og);
                                if (settled[og]) settled[g] = true;
                            }
                        }
                    }
                }
                if (!settled[g] && sizeOf(group, interiors, g) > hardCap) settled[g] = true;
            }
            for (int g : undecided(group, settled)) {
                if (!drained(group, queues, g)) continue;
                settled[g] = true;
                closed.add(regionOf(group, interiors, rings, g, maxInterior));
            }
            if (!moved) break;
        }
        return closed;
    }

    private static Region regionOf(int[] group, LongSet[] interiors, LongSet[] rings, int g, int maxInterior) {
        LongOpenHashSet interior = new LongOpenHashSet();
        LongOpenHashSet ring = new LongOpenHashSet();
        for (int i = 0; i < group.length; i++) {
            if (find(group, i) != g) continue;
            interior.addAll(interiors[i]);
            ring.addAll(rings[i]);
        }
        Status status = interior.size() > maxInterior ? Status.TOO_LARGE : Status.FOUND;
        return new Region(status, interior, ring, NONE);
    }

    private static IntArrayList undecided(int[] group, boolean[] settled) {
        IntArrayList out = new IntArrayList();
        for (int i = 0; i < group.length; i++) {
            int g = find(group, i);
            if (g == i && !settled[g]) out.add(g);
        }
        return out;
    }

    private static boolean drained(int[] group, LongArrayFIFOQueue[] queues, int g) {
        for (int i = 0; i < group.length; i++) {
            if (find(group, i) == g && !queues[i].isEmpty()) return false;
        }
        return true;
    }

    private static int sizeOf(int[] group, LongSet[] interiors, int g) {
        int size = 0;
        for (int i = 0; i < group.length; i++) {
            if (find(group, i) == g) size += interiors[i].size();
        }
        return size;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static int union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) return ra;
        int root = Math.min(ra, rb);
        parent[Math.max(ra, rb)] = root;
        return root;
    }

    private static LongList pathTo(Long2LongOpenHashMap parent, long root, long end) {
        LongArrayList path = new LongArrayList();
        long cell = end;
        path.add(cell);
        while (cell != root && parent.containsKey(cell)) {
            cell = parent.get(cell);
            path.add(cell);
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /**
     * Where a walk left its pen. The pen is the run of ring around the start: of the connected
     * runs of ring whose box holds the start, the tightest; failing that, the nearest. The gap is
     * the last cell of the path out that is still inside that run's box.
     */
    static long gapOf(LongList path, LongSet ring) {
        long start = path.getLong(0);
        long end = path.getLong(path.size() - 1);
        if (ring.isEmpty()) return end;
        int sx = SiteGeometry.x(start);
        int sz = SiteGeometry.z(start);

        LongOpenHashSet columns = new LongOpenHashSet();
        var it = ring.iterator();
        while (it.hasNext()) {
            long cell = it.nextLong();
            columns.add(SiteGeometry.column(SiteGeometry.x(cell), SiteGeometry.z(cell)));
        }
        LongOpenHashSet seen = new LongOpenHashSet();
        int[] best = null;
        long bestArea = Long.MAX_VALUE;
        int[] nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        var columnIt = columns.iterator();
        while (columnIt.hasNext()) {
            long column = columnIt.nextLong();
            if (seen.contains(column)) continue;
            int[] box = runBox(column, columns, seen);
            boolean holdsStart = sx >= box[0] && sx <= box[2] && sz >= box[1] && sz <= box[3];
            long area = (long) (box[2] - box[0] + 1) * (box[3] - box[1] + 1);
            if (holdsStart && area < bestArea) {
                best = box;
                bestArea = area;
            }
            long distance = boxDistanceSqr(box, sx, sz);
            if (distance < nearestDistance) {
                nearest = box;
                nearestDistance = distance;
            }
        }
        int[] box = best != null ? best : nearest;
        int last = -1;
        for (int i = 0; i < path.size(); i++) {
            long cell = path.getLong(i);
            int x = SiteGeometry.x(cell);
            int z = SiteGeometry.z(cell);
            if (x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3]) last = i;
        }
        return last < 0 ? end : path.getLong(last);
    }

    /** Box {minX, minZ, maxX, maxZ} of the ring run a column belongs to, corners joining. */
    private static int[] runBox(long first, LongSet columns, LongSet seen) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(first);
        queue.enqueue(first);
        while (!queue.isEmpty()) {
            long column = queue.dequeueLong();
            int x = (int) (column >> 32);
            int z = (int) column;
            box[0] = Math.min(box[0], x);
            box[1] = Math.min(box[1], z);
            box[2] = Math.max(box[2], x);
            box[3] = Math.max(box[3], z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long next = SiteGeometry.column(x + dx, z + dz);
                    if (columns.contains(next) && seen.add(next)) queue.enqueue(next);
                }
            }
        }
        return box;
    }

    private static long boxDistanceSqr(int[] box, int x, int z) {
        long dx = x < box[0] ? box[0] - x : Math.max(0, x - box[2]);
        long dz = z < box[1] ? box[1] - z : Math.max(0, z - box[3]);
        return dx * dx + dz * dz;
    }

    /** One step of an animal into the next column, with the height of whatever stopped it. */
    private static final class Walker {
        private final World world;
        int blockedY;

        Walker(World world) {
            this.world = world;
        }

        long step(int x, int y, int z, int nx, int nz) {
            int kind = world.kind(nx, y, nz);
            switch (kind) {
                case UNLOADED:
                    return NOT_LOADED;
                case BARRIER:
                case SOLID:
                    blockedY = y;
                    return BLOCKED;
                case GROUND: {
                    int above = world.kind(nx, y + 1, nz);
                    if (above == UNLOADED) return NOT_LOADED;
                    if (standable(world, nx, y + 1, nz)) return SiteGeometry.pack(nx, y + 1, nz);
                    blockedY = above == BARRIER ? y + 1 : y;
                    return BLOCKED;
                }
                default: {
                    if (standable(world, nx, y, nz)) return SiteGeometry.pack(nx, y, nz);
                    for (int drop = 1; drop <= MAX_DROP; drop++) {
                        int below = world.kind(nx, y - drop, nz);
                        if (below == UNLOADED) return NOT_LOADED;
                        if (below == BARRIER) {
                            blockedY = y - drop;
                            return BLOCKED;
                        }
                        if (standable(world, nx, y - drop, nz)) return SiteGeometry.pack(nx, y - drop, nz);
                    }
                    return FELL;
                }
            }
        }
    }
}
