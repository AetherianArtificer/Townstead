package com.aetherianartificer.townstead.root.locomotion.plan;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Lazy Theta* over one-block cells (Nash, Koenig &amp; Tovey, AAAI 2010): A* whose nodes
 * may take any visible ancestor as parent, so paths come out as straight any-angle legs
 * instead of grid zigzags. "Lazy" defers each line-of-sight check until a node is
 * expanded, about one check per expansion.
 *
 * <p>Resumable: {@link #step} spends a node budget and returns, so a long search spreads
 * over several ticks. Bounded by a box and a total expansion cap.</p>
 */
public final class VoxelPlanner {

    public enum Status { RUNNING, FOUND, FAILED }

    /** Extra cost for being in a cell (drift's pull toward the ground). */
    @FunctionalInterface
    public interface CellCost {
        double extra(int x, int y, int z);
    }

    @FunctionalInterface
    public interface Goal {
        boolean reached(int x, int y, int z);
    }

    private static final double HEURISTIC_WEIGHT = 1.2;

    private final AirSpace space;
    private final Goal goal;
    private final BlockPos aim;
    private final CellCost cellCost;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int maxExpansions;
    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
    private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
    private int expansions;
    private Status status = Status.RUNNING;
    @Nullable private Node found;

    private static final class Node {
        final int x, y, z;
        double g = Double.POSITIVE_INFINITY;
        double f;
        @Nullable Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 feet() {
            return new Vec3(x + 0.5, y, z + 0.5);
        }
    }

    /**
     * @param aim     where the heuristic points (the goal cell, or the best guess for a predicate goal)
     * @param margin  how far past the start and aim boxes the search may roam
     */
    public VoxelPlanner(AirSpace space, BlockPos start, BlockPos aim, Goal goal, @Nullable CellCost cellCost,
                        int margin, int maxExpansions) {
        this.space = space;
        this.goal = goal;
        this.aim = aim;
        this.cellCost = cellCost == null ? (x, y, z) -> 0.0 : cellCost;
        this.minX = Math.min(start.getX(), aim.getX()) - margin;
        this.minY = Math.max(space.minY(), Math.min(start.getY(), aim.getY()) - margin);
        this.minZ = Math.min(start.getZ(), aim.getZ()) - margin;
        this.maxX = Math.max(start.getX(), aim.getX()) + margin;
        this.maxY = Math.min(space.maxY(), Math.max(start.getY(), aim.getY()) + margin);
        this.maxZ = Math.max(start.getZ(), aim.getZ()) + margin;
        this.maxExpansions = maxExpansions;
        Node first = node(start.getX(), start.getY(), start.getZ());
        first.g = 0;
        first.parent = first;
        first.f = HEURISTIC_WEIGHT * heuristic(first);
        open.add(first);
    }

    public Status status() {
        return status;
    }

    public int expansions() {
        return expansions;
    }

    /** Expand up to {@code budget} nodes. */
    public Status step(int budget) {
        if (status != Status.RUNNING) return status;
        for (int i = 0; i < budget; i++) {
            Node s = open.poll();
            if (s == null || expansions >= maxExpansions) {
                status = Status.FAILED;
                return status;
            }
            if (s.closed) continue;
            s.closed = true;
            expansions++;
            setVertex(s);
            if (goal.reached(s.x, s.y, s.z)) {
                found = s;
                status = Status.FOUND;
                return status;
            }
            expand(s);
        }
        return status;
    }

    /** The found path as feet positions, start first; empty unless {@link Status#FOUND}. */
    public List<Vec3> path() {
        if (found == null) return List.of();
        List<Vec3> out = new ArrayList<>();
        Node n = found;
        while (n != null) {
            out.add(n.feet());
            if (n.parent == n) break;
            n = n.parent;
        }
        Collections.reverse(out);
        return out;
    }

    /** Lazy step: the optimistic parent assumed at push time may not be visible; fall back to the best closed neighbour. */
    private void setVertex(Node s) {
        Node parent = s.parent;
        if (parent == null || parent == s) return;
        if (space.segmentClear(parent.feet(), s.feet(), 0.0)) return;
        double bestG = Double.POSITIVE_INFINITY;
        Node best = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Node n = nodes.get(BlockPos.asLong(s.x + dx, s.y + dy, s.z + dz));
                    if (n == null || !n.closed || !moveAllowed(n.x, n.y, n.z, s.x - n.x, s.y - n.y, s.z - n.z)) continue;
                    double g = n.g + distance(n, s) + cellCost.extra(s.x, s.y, s.z);
                    if (g < bestG) {
                        bestG = g;
                        best = n;
                    }
                }
            }
        }
        if (best != null) {
            s.parent = best;
            s.g = bestG;
        }
    }

    private void expand(Node s) {
        Node parent = s.parent == null ? s : s.parent;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int x = s.x + dx;
                    int y = s.y + dy;
                    int z = s.z + dz;
                    if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) continue;
                    if (!moveAllowed(s.x, s.y, s.z, dx, dy, dz)) continue;
                    Node n = node(x, y, z);
                    if (n.closed) continue;
                    // Optimistically route through s's parent; setVertex repairs it on expansion.
                    double g = parent.g + distance(parent, n) + cellCost.extra(x, y, z);
                    if (g < n.g) {
                        n.g = g;
                        n.parent = parent;
                        n.f = g + HEURISTIC_WEIGHT * heuristic(n);
                        open.add(n);
                    }
                }
            }
        }
    }

    /** A step into a neighbour, with every cell it sweeps free so diagonals never clip a corner. */
    private boolean moveAllowed(int x, int y, int z, int dx, int dy, int dz) {
        for (int ix = 0; ix <= Math.abs(dx); ix++) {
            for (int iy = 0; iy <= Math.abs(dy); iy++) {
                for (int iz = 0; iz <= Math.abs(dz); iz++) {
                    if (ix == 0 && iy == 0 && iz == 0) continue;
                    if (!space.passable(x + ix * dx, y + iy * dy, z + iz * dz)) return false;
                }
            }
        }
        return true;
    }

    private Node node(int x, int y, int z) {
        return nodes.computeIfAbsent(BlockPos.asLong(x, y, z), k -> new Node(x, y, z));
    }

    private double heuristic(Node n) {
        double dx = n.x - aim.getX();
        double dy = n.y - aim.getY();
        double dz = n.z - aim.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distance(Node a, Node b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
