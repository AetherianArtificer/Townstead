package com.aetherianartificer.townstead.temperature;

import java.util.*;

/** Bounded topology discovery. Doors are boundaries even while open, avoiding room churn. */
public final class ThermalRegionScan {
    public enum Kind { INTERIOR, EXTERIOR, BARRIER, UNLOADED }
    public interface Access {
        Kind kind(long position);
        long[] neighbors(long position);
    }
    public record Face(long inside, long outside) {}
    public record Region(Set<Long> cells, List<Face> faces) {}
    private ThermalRegionScan() {}
    public static Optional<Region> scan(long seed, int limit, Access access) {
        Map<Long, Kind> kinds = new HashMap<>();
        if (kinds.computeIfAbsent(seed, access::kind) != Kind.INTERIOR) return Optional.empty();
        Set<Long> cells = new HashSet<>();
        List<Face> faces = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        cells.add(seed); queue.add(seed);
        while (!queue.isEmpty()) {
            long cell = queue.removeFirst();
            for (long neighbor : access.neighbors(cell)) {
                Kind kind = kinds.computeIfAbsent(neighbor, access::kind);
                if (kind == Kind.UNLOADED) return Optional.empty();
                if (kind != Kind.INTERIOR) faces.add(new Face(cell, neighbor));
                else if (cells.add(neighbor)) {
                    if (cells.size() > limit) return Optional.empty();
                    queue.addLast(neighbor);
                }
            }
        }
        return Optional.of(new Region(Set.copyOf(cells), List.copyOf(faces)));
    }
}
