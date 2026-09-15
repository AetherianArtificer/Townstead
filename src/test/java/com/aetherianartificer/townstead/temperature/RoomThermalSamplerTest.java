package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aetherianartificer.townstead.temperature.RoomThermalSampler.*;

class RoomThermalSamplerTest {
    private static final Cell AIR = new Cell(false, false, true, 0);
    private static final Cell WALL = new Cell(true, false, false, 0);

    @Test
    void actualShellAndUnregisteredSourcesAreMeasuredWithoutAPoiInventory() {
        Counts counts = scan(new Point(0, 0, 0), new Point(4, 4, 4), p -> {
            if (p.equals(new Point(1, 1, 1))) return new Cell(false, false, false, 1);
            if (p.equals(new Point(3, 1, 1))) return new Cell(false, false, false, -1);
            if (p.equals(new Point(2, 2, 2))) return WALL; // Insulating furniture is not shell.
            if (p.equals(new Point(0, 2, 2))) return new Cell(false, true, false, 0);
            return p.x() == 0 || p.x() == 4 || p.y() == 0 || p.y() == 4 || p.z() == 0 || p.z() == 4
                    ? WALL : AIR;
        });
        assertEquals(97, counts.insulating());
        assertEquals(1, counts.leaky());
        assertEquals(1, counts.heat());
        assertEquals(1, counts.cool());
    }

    @Test
    void interiorBoundsIncludeAdjacentWallsFloorAndRoofWithoutScanningPastThem() {
        Counts counts = scan(new Point(1, 1, 1), new Point(3, 3, 3), p -> {
            assertTrue(p.x() >= 0 && p.x() <= 4 && p.y() >= 0 && p.y() <= 4 && p.z() >= 0 && p.z() <= 4);
            return p.x() == 0 || p.x() == 4 || p.y() == 0 || p.y() == 4 || p.z() == 0 || p.z() == 4
                    ? WALL : AIR;
        });
        assertEquals(54, counts.insulating());
        assertEquals(0, counts.leaky());
    }

    @Test
    void sourceOnBoundaryCountsOnceAndUnloadedCellsAreSkipped() {
        Counts counts = scan(new Point(2, 2, 2), new Point(0, 0, 0), p -> {
            if (p.equals(new Point(0, 0, 0))) return new Cell(false, false, false, -1);
            return null;
        });
        assertEquals(1, counts.cool());
        assertEquals(0, counts.heat());
    }
}
