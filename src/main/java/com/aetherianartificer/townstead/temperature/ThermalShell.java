package com.aetherianartificer.townstead.temperature;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * One room's walls, read along its face rays and lumped by wall class and by what lies beyond.
 * A lump's conductance reproduces its rays' steady flow exactly, so the node count follows the
 * room's neighbors and materials, never its surface area. Buried walls also lump by side, and each
 * lump keeps where its rays end, so rock warmed on one side warms only that side.
 */
public final class ThermalShell {
    public static final long NO_ROOM = Long.MIN_VALUE;
    public static final int INTERIOR = -1, EXTERIOR = -2, UNLOADED = -3;
    /** What a ray reaches: open sky, deep rock, nothing known, the unscanned air of the room's own space, another room. */
    public static final int TO_OUTSIDE = 0, TO_GROUND = 1, TO_NOTHING = 2, TO_SPACE = 3, TO_ROOM = 4;
    private static final short UNREAD = Short.MIN_VALUE;

    public interface Cells {
        /** The cell's class from {@link ThermalWall}, or {@link #INTERIOR}, {@link #EXTERIOR}, {@link #UNLOADED}. */
        int read(long cell);
        long beyond(long from, long to);
        /** The anchor of the room that owns this interior cell, or {@link #NO_ROOM}. */
        long roomAt(long cell);
        /** The cell's coordinates, in the packing {@link #beyond} uses. */
        int x(long cell);
        int y(long cell);
        int z(long cell);
    }

    public int groups;
    public int[] endpoint = new int[8];
    public long[] endpointRoom = new long[8], key = new long[8];
    public double[] capacity = new double[8], toRoom = new double[8], toEnd = new double[8], temperature;
    /** Where a lump's rays end, weighted by their flow; only buried lumps use it. */
    public double[] endX = new double[8], endY = new double[8], endZ = new double[8];
    public double directOutside, directGround, directSpace;
    public double directGroundX, directGroundY, directGroundZ;
    public final Long2DoubleOpenHashMap directRooms = new Long2DoubleOpenHashMap();
    /** Rooms any ray reached; a removed neighbor makes those rays stale. */
    public final LongOpenHashSet neighbors = new LongOpenHashSet();
    /** Interior cells a ray reached that no room owned. */
    public final LongOpenHashSet unclaimed = new LongOpenHashSet();
    /** Unowned cells a ray stopped at, which a new room can claim: unclaimed cells and a partial room's frontier. */
    public final LongOpenHashSet openEnds = new LongOpenHashSet();
    public int unresolved;
    public long builtAt;
    private final Long2ShortOpenHashMap watched = new Long2ShortOpenHashMap();
    /** Cells read as anything but a wall: only these can change without a block update, when a roof rises or a chunk loads. */
    private final LongArrayList spaces = new LongArrayList();
    private long self;
    private int cursor;
    private List<ThermalRegionScan.Face> faces;
    private Set<Long> frontier;
    private boolean trackUnclaimed;
    private Cells cells;
    private ThermalWall.Table table;
    private Long2IntOpenHashMap slots, endpoints;
    private double[] series;
    private LongOpenHashSet massSeen;

    private ThermalShell() { watched.defaultReturnValue(UNREAD); }

    /** Whether a block change moves this shell's heat flow: the cell's class differs from the one read. */
    public boolean changedBy(long cell, int code) {
        short before = watched.get(cell);
        return before != UNREAD && before != code;
    }

    /** Whether a space on a ray now reads differently, which no block change would report. */
    public boolean spacesChanged(Cells cells) {
        for (int i = 0; i < spaces.size(); i++) {
            long cell = spaces.getLong(i);
            if (cells.read(cell) != watched.get(cell)) return true;
        }
        return false;
    }

    /** Whether any ray ends in open sky. */
    public boolean reachesSky() {
        if (directOutside > 0) return true;
        for (int i = 0; i < groups; i++) if (endpoint[i] == TO_OUTSIDE) return true;
        return false;
    }

    /** Capacity-weighted mean wall temperature, or {@code fallback} without walls. */
    public double meanTemperature(double fallback) {
        double heat = 0, total = 0;
        for (int i = 0; i < groups; i++) { heat += capacity[i] * temperature[i]; total += capacity[i]; }
        return total > 0 ? heat / total : fallback;
    }

    /** The lump's temperature, or NaN if this shell has none under that key. */
    public double temperatureOf(long groupKey) {
        for (int i = 0; i < groups; i++) if (key[i] == groupKey) return temperature[i];
        return Double.NaN;
    }

    /** Starts reading a room's walls. {@code faces} are the room's; {@code trackUnclaimed} is off for a room discovery must not spread from. */
    public static ThermalShell begin(long self, List<ThermalRegionScan.Face> faces, Set<Long> frontier,
                                     boolean trackUnclaimed, Cells cells, ThermalWall.Table table) {
        var shell = new ThermalShell();
        shell.self = self; shell.faces = faces; shell.frontier = frontier;
        shell.trackUnclaimed = trackUnclaimed; shell.cells = cells; shell.table = table;
        shell.slots = new Long2IntOpenHashMap();
        shell.slots.defaultReturnValue(-1);
        shell.endpoints = new Long2IntOpenHashMap();
        shell.series = new double[8];
        shell.massSeen = new LongOpenHashSet();
        return shell;
    }

    public static ThermalShell build(long self, List<ThermalRegionScan.Face> faces, Set<Long> frontier,
                                     boolean trackUnclaimed, Cells cells, ThermalWall.Table table) {
        var shell = begin(self, faces, frontier, trackUnclaimed, cells, table);
        shell.advance(Integer.MAX_VALUE);
        return shell;
    }

    /** Reads up to {@code count} more faces; true once the shell is complete. Blocks read so far are watched, so a change mid-read is seen. */
    public boolean advance(int count) {
        int end = (int) Math.min((long) cursor + count, faces.size());
        while (cursor < end) readRay(faces.get(cursor++));
        if (cursor < faces.size()) return false;
        finish(series);
        // A finished shell keeps no route back to the world it read.
        cells = null; table = null; faces = null; frontier = null; slots = null; endpoints = null; series = null; massSeen = null;
        return true;
    }

    private void readRay(ThermalRegionScan.Face face) {
        long previous = face.inside(), pos = face.outside();
        int firstWall = -1;
        long massCell = 0;
        double firstHalf = 0, tail = 0;
        int kind = TO_NOTHING;
        long endRoom = NO_ROOM;
        boolean opening = false;
        for (int depth = 0; ; depth++) {
            int code = read(cells, pos);
            if (code == UNLOADED) { unresolved++; kind = TO_GROUND; break; }
            if (code == EXTERIOR) { kind = TO_OUTSIDE; break; }
            if (code == INTERIOR) {
                long room = cells.roomAt(pos);
                if (room != NO_ROOM) { kind = TO_ROOM; endRoom = room; }
                else {
                    openEnds.add(pos);
                    if (frontier.contains(pos)) { kind = TO_SPACE; opening = true; }
                    else { unresolved++; if (trackUnclaimed) unclaimed.add(pos); }
                }
                break;
            }
            if (depth == ThermalBoundary.MAX_DEPTH) { unresolved++; kind = TO_GROUND; break; }
            double half = .5 / table.conductance[code];
            if (depth == 0) { firstWall = code; massCell = pos; firstHalf = half; }
            else tail += 2 * half;
            long next = cells.beyond(previous, pos);
            previous = pos; pos = next;
        }
        // A ray through a pillar back into this room is the same ray as the one from the far side.
        if (kind == TO_ROOM && endRoom == self && pos < face.inside()) return;
        if (kind == TO_ROOM) neighbors.add(endRoom);
        if (firstWall < 0) {
            if (kind == TO_OUTSIDE) directOutside += table.opening;
            else if (kind == TO_SPACE) directSpace += table.opening;
            else if (kind == TO_ROOM) directRooms.addTo(endRoom, table.opening);
            return;
        }
        int wallGroup = wallGroup(firstWall) | (kind == TO_GROUND ? side(face) << 5 : 0);
        int endpointId = kind == TO_ROOM ? TO_ROOM + endpoints.computeIfAbsent(endRoom, r -> endpoints.size()) : kind;
        long slotKey = (long) endpointId << 8 | wallGroup;
        int slot = slots.get(slotKey);
        if (slot < 0) {
            slot = addGroup(kind, endRoom, wallGroup);
            slots.put(slotKey, slot);
            if (slot >= series.length) series = Arrays.copyOf(series, series.length * 2);
        }
        if (massSeen.add(massCell)) capacity[slot] += table.capacity[firstWall];
        double in = 1 / firstHalf;
        toRoom[slot] += in;
        if (kind != TO_NOTHING) {
            double out = opening ? table.opening : 1 / (firstHalf + tail);
            double flow = in * out / (in + out);
            series[slot] += flow;
            endX[slot] += flow * cells.x(pos); endY[slot] += flow * cells.y(pos); endZ[slot] += flow * cells.z(pos);
        }
    }

    /** 0 below, 1 above, then north, south, west, east. */
    private int side(ThermalRegionScan.Face face) {
        long in = face.inside(), out = face.outside();
        int dy = cells.y(out) - cells.y(in), dz = cells.z(out) - cells.z(in);
        if (dy != 0) return dy < 0 ? 0 : 1;
        if (dz != 0) return dz < 0 ? 2 : 3;
        return cells.x(out) < cells.x(in) ? 4 : 5;
    }

    /** Open apertures hold air, not wall mass, so they lump apart from their material's walls. */
    private static int wallGroup(int wall) {
        return ThermalWall.material(wall).ordinal() | (ThermalWall.openAperture(wall) ? 16 : 0);
    }

    private int read(Cells cells, long cell) {
        short known = watched.get(cell);
        if (known != UNREAD) return known;
        int code = cells.read(cell);
        watched.put(cell, (short) code);
        if (code < 0) spaces.add(cell);
        return code;
    }

    private int addGroup(int kind, long room, int wallGroup) {
        if (groups == endpoint.length) {
            int size = groups * 2;
            endpoint = Arrays.copyOf(endpoint, size); endpointRoom = Arrays.copyOf(endpointRoom, size);
            key = Arrays.copyOf(key, size); capacity = Arrays.copyOf(capacity, size);
            toRoom = Arrays.copyOf(toRoom, size); toEnd = Arrays.copyOf(toEnd, size);
            endX = Arrays.copyOf(endX, size); endY = Arrays.copyOf(endY, size); endZ = Arrays.copyOf(endZ, size);
        }
        endpoint[groups] = kind; endpointRoom[groups] = room;
        key[groups] = (kind == TO_ROOM ? room * 0x9E3779B97F4A7C15L : 0) ^ (long) kind << 8 ^ wallGroup;
        return groups++;
    }

    /** Sets each lump's outward conductance so its series flow equals its rays'; a lump with no mass of its own becomes a direct link. */
    private void finish(double[] series) {
        int kept = 0;
        for (int i = 0; i < groups; i++) {
            if (series[i] > 0) { endX[i] /= series[i]; endY[i] /= series[i]; endZ[i] /= series[i]; }
            if (capacity[i] <= 0) {
                if (endpoint[i] == TO_OUTSIDE) directOutside += series[i];
                else if (endpoint[i] == TO_GROUND) {
                    double total = directGround + series[i];
                    directGroundX = (directGroundX * directGround + endX[i] * series[i]) / total;
                    directGroundY = (directGroundY * directGround + endY[i] * series[i]) / total;
                    directGroundZ = (directGroundZ * directGround + endZ[i] * series[i]) / total;
                    directGround = total;
                }
                else if (endpoint[i] == TO_SPACE) directSpace += series[i];
                else if (endpoint[i] == TO_ROOM) directRooms.addTo(endpointRoom[i], series[i]);
                continue;
            }
            endpoint[kept] = endpoint[i]; endpointRoom[kept] = endpointRoom[i]; key[kept] = key[i];
            capacity[kept] = capacity[i]; toRoom[kept] = toRoom[i];
            endX[kept] = endX[i]; endY[kept] = endY[i]; endZ[kept] = endZ[i];
            toEnd[kept] = endpoint[i] == TO_NOTHING ? 0 : 1 / Math.max(1e-12, 1 / series[i] - 1 / toRoom[i]);
            kept++;
        }
        groups = kept;
        temperature = new double[kept];
    }
}
