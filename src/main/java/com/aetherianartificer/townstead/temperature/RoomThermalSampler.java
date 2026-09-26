package com.aetherianartificer.townstead.temperature;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Samples actual room cells and their exposed boundary, independent of MCA's POI inventory. */
public final class RoomThermalSampler {
    private RoomThermalSampler() {}

    public record Point(int x, int y, int z) {}
    public record Cell(boolean insulating, boolean leaky, boolean open, int source) {}
    public record Counts(int insulating, int leaky, int heat, int cool) {}

    public static Counts scan(Point a, Point b, Function<Point, Cell> read) {
        int x0 = Math.min(a.x, b.x), x1 = Math.max(a.x, b.x);
        int y0 = Math.min(a.y, b.y), y1 = Math.max(a.y, b.y);
        int z0 = Math.min(a.z, b.z), z1 = Math.max(a.z, b.z);
        int heat = 0, cool = 0;
        Set<Point> shell = new HashSet<>();
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Point p = new Point(x, y, z);
                    Cell cell = read.apply(p);
                    if (cell == null) continue; // Never load chunks to measure a room.
                    if (cell.source > 0) heat++;
                    else if (cell.source < 0) cool++;
                    boolean boundary = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    if (!boundary) continue;
                    if (!cell.open) shell.add(p);
                    else {
                        // Newer MCA room bounds end at the interior; older bounds include the wall.
                        if (x == x0) shell.add(new Point(x - 1, y, z));
                        if (x == x1) shell.add(new Point(x + 1, y, z));
                        if (y == y0) shell.add(new Point(x, y - 1, z));
                        if (y == y1) shell.add(new Point(x, y + 1, z));
                        if (z == z0) shell.add(new Point(x, y, z - 1));
                        if (z == z1) shell.add(new Point(x, y, z + 1));
                    }
                }
            }
        }
        int insulating = 0, leaky = 0;
        for (Point p : shell) {
            Cell cell = read.apply(p);
            if (cell == null) continue;
            if (cell.insulating) insulating++;
            else if (cell.leaky || cell.open) leaky++;
            if (p.x < x0 || p.x > x1 || p.y < y0 || p.y > y1 || p.z < z0 || p.z > z1) {
                if (cell.source > 0) heat++;
                else if (cell.source < 0) cool++;
            }
        }
        return new Counts(insulating, leaky, heat, cool);
    }
}
