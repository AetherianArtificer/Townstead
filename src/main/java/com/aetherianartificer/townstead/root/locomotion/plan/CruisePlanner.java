package com.aetherianartificer.townstead.root.locomotion.plan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The open-air layer of a flight route: Theta* over two-block terrain columns, the
 * "fly over the height map" idea from Guerrilla's Horizon Zero Dawn flight navigation
 * (Josemans, "Putting the AI back into Air", Game AI North 2017). Height comes later
 * from {@link AltitudeProfile}; this layer only picks the ground track, trading
 * distance against how much terrain the track climbs over.
 */
final class CruisePlanner {

    static final int CELL = 2;
    private static final double CLIMB_WEIGHT = 0.5;
    private static final int MARGIN_CELLS = 12;
    private static final int MAX_EXPANSIONS = 4000;

    private final AirSpace space;
    private final LongSet blocked;
    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
    private int minCx, minCz, maxCx, maxCz;

    private static final class Node {
        final int cx, cz;
        double g = Double.POSITIVE_INFINITY;
        double f;
        @Nullable Node parent;
        boolean closed;

        Node(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }

    /** @param blocked columns (packed cell coords) earlier attempts found unflyable */
    CruisePlanner(AirSpace space, LongSet blocked) {
        this.space = space;
        this.blocked = blocked;
    }

    static long key(int cx, int cz) {
        return ChunkPos.asLong(cx, cz);
    }

    static int cellOf(double block) {
        return Mth.floor(block / CELL);
    }

    /** Ground track from a to b in block coordinates, start and end exact; null when none fits. */
    @Nullable
    List<Vec2> track(Vec2 from, Vec2 to) {
        int sx = cellOf(from.x), sz = cellOf(from.y), gx = cellOf(to.x), gz = cellOf(to.y);
        minCx = Math.min(sx, gx) - MARGIN_CELLS;
        maxCx = Math.max(sx, gx) + MARGIN_CELLS;
        minCz = Math.min(sz, gz) - MARGIN_CELLS;
        maxCz = Math.max(sz, gz) + MARGIN_CELLS;
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Node start = node(sx, sz);
        start.g = 0;
        start.parent = start;
        start.f = h(start, gx, gz);
        open.add(start);
        int expansions = 0;
        Node goal = null;
        while (!open.isEmpty() && expansions < MAX_EXPANSIONS) {
            Node s = open.poll();
            if (s.closed) continue;
            s.closed = true;
            expansions++;
            if (s.cx == gx && s.cz == gz) {
                goal = s;
                break;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = s.cx + dx, nz = s.cz + dz;
                    if (nx < minCx || nx > maxCx || nz < minCz || nz > maxCz) continue;
                    // Endpoints stay usable even if marked: the route must start and end there.
                    boolean endpoint = (nx == gx && nz == gz);
                    if (!endpoint && (blocked.contains(key(nx, nz)) || columnTop(nx, nz) == Integer.MAX_VALUE)) continue;
                    Node n = node(nx, nz);
                    if (n.closed) continue;
                    Node p = s.parent;
                    double g;
                    Node via;
                    if (p != null && p != s && lineOpen(p, n)) {
                        g = p.g + lineCost(p, n);
                        via = p;
                    } else {
                        g = s.g + lineCost(s, n);
                        via = s;
                    }
                    if (g < n.g) {
                        n.g = g;
                        n.parent = via;
                        n.f = g + h(n, gx, gz);
                        open.add(n);
                    }
                }
            }
        }
        if (goal == null) return null;
        List<Vec2> out = new ArrayList<>();
        out.add(to);
        Node n = goal.parent;
        while (n != null && n != start && n != n.parent) {
            out.add(center(n));
            n = n.parent;
        }
        out.add(from);
        Collections.reverse(out);
        return out;
    }

    /** Highest terrain in a column (feet height above it). */
    int columnTop(int cx, int cz) {
        int best = Integer.MIN_VALUE;
        for (int x = cx * CELL; x < cx * CELL + CELL; x++) {
            for (int z = cz * CELL; z < cz * CELL + CELL; z++) {
                int t = space.top(x, z);
                if (t == Integer.MAX_VALUE) return Integer.MAX_VALUE;
                best = Math.max(best, t);
            }
        }
        return best;
    }

    private boolean lineOpen(Node a, Node b) {
        boolean[] ok = {true};
        walk(a, b, (cx, cz) -> {
            if (blocked.contains(key(cx, cz)) || columnTop(cx, cz) == Integer.MAX_VALUE) ok[0] = false;
            return ok[0];
        });
        return ok[0];
    }

    /** Distance plus a charge for every block the track climbs over the terrain. */
    private double lineCost(Node a, Node b) {
        double[] climb = {0};
        int[] last = {columnTop(a.cx, a.cz)};
        walk(a, b, (cx, cz) -> {
            int t = columnTop(cx, cz);
            if (t != Integer.MAX_VALUE) {
                if (t > last[0]) climb[0] += t - last[0];
                last[0] = t;
            }
            return true;
        });
        double dx = (a.cx - b.cx) * CELL, dz = (a.cz - b.cz) * CELL;
        return Math.sqrt(dx * dx + dz * dz) + CLIMB_WEIGHT * climb[0];
    }

    @FunctionalInterface
    private interface CellVisitor {
        boolean visit(int cx, int cz);
    }

    /** Every column the straight line between two column centres touches. */
    private static void walk(Node a, Node b, CellVisitor visitor) {
        int dx = b.cx - a.cx, dz = b.cz - a.cz;
        int steps = Math.max(Math.abs(dx), Math.abs(dz)) * 2;
        if (steps == 0) {
            visitor.visit(a.cx, a.cz);
            return;
        }
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int cx = Mth.floor(a.cx + 0.5 + dx * t);
            int cz = Mth.floor(a.cz + 0.5 + dz * t);
            if (cx == lastX && cz == lastZ) continue;
            lastX = cx;
            lastZ = cz;
            if (!visitor.visit(cx, cz)) return;
        }
    }

    private Node node(int cx, int cz) {
        return nodes.computeIfAbsent(key(cx, cz), k -> new Node(cx, cz));
    }

    private static double h(Node n, int gx, int gz) {
        double dx = (n.cx - gx) * CELL, dz = (n.cz - gz) * CELL;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec2 center(Node n) {
        return new Vec2(n.cx * CELL + CELL / 2f, n.cz * CELL + CELL / 2f);
    }

    static LongSet newBlockedSet() {
        return new LongOpenHashSet();
    }
}
