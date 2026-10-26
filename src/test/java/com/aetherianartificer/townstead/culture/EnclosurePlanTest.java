package com.aetherianartificer.townstead.culture;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnclosurePlanTest {
    private static final EnclosurePlan.Box CENTRE = new EnclosurePlan.Box(0, 0, 10, 10);
    private static final EnclosurePlan.Box NORTH_STREET = new EnclosurePlan.Box(3, -30, 7, -1);
    private static final EnclosurePlan.Box EAST_HOUSE = new EnclosurePlan.Box(25, 15, 35, 22);

    @Test
    void lineCellsShareAnEdge() {
        List<long[]> cells = EnclosurePlan.line(0, 0, 7, 3);
        for (int i = 1; i < cells.size(); i++) {
            long step = Math.abs(cells.get(i)[0] - cells.get(i - 1)[0]) + Math.abs(cells.get(i)[1] - cells.get(i - 1)[1]);
            assertEquals(1, step, "diagonal step at " + i);
        }
        assertEquals(7, cells.get(cells.size() - 1)[0]);
        assertEquals(3, cells.get(cells.size() - 1)[1]);
        assertEquals(11, cells.size());
    }

    @Test
    void wallIsAClosedLoopWithoutDiagonalGaps() {
        var plan = EnclosurePlan.plan(List.of(CENTRE, NORTH_STREET, EAST_HOUSE), List.of(NORTH_STREET), 3, 2, 3);
        Set<Long> wall = new HashSet<>();
        for (long[] c : plan.wall()) wall.add(EnclosurePlan.key(c[0], c[1]));
        for (long[] c : plan.wall()) {
            int neighbours = 0;
            for (long[] d : new long[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (wall.contains(EnclosurePlan.key(c[0] + d[0], c[1] + d[1]))) neighbours++;
            }
            assertTrue(neighbours >= 2, "wall cell " + c[0] + "," + c[1] + " is loose");
        }
    }

    @Test
    void wallClearsEveryBuildingByTheMargin() {
        var pieces = List.of(CENTRE, NORTH_STREET, EAST_HOUSE);
        var plan = EnclosurePlan.plan(pieces, List.of(NORTH_STREET), 3, 2, 3);
        for (long[] c : plan.wall()) {
            for (var box : pieces) {
                boolean inside = c[0] >= box.minX() - 2 && c[0] <= box.maxX() + 2 && c[1] >= box.minZ() - 2 && c[1] <= box.maxZ() + 2;
                assertFalse(inside, "wall at " + c[0] + "," + c[1] + " is within 3 of " + box);
            }
        }
    }

    @Test
    void firstGateFacesTheMainStreet() {
        var plan = EnclosurePlan.plan(List.of(CENTRE, NORTH_STREET, EAST_HOUSE), List.of(NORTH_STREET), 3, 2, 3);
        assertEquals(2, plan.gates().size());
        var gate = plan.gates().get(0);
        assertTrue(gate.alongZ(), "the north gate is passed through along z");
        assertEquals(3, gate.cells().size());
        for (long[] c : gate.cells()) assertTrue(c[1] < 0, "north gate cell " + c[0] + "," + c[1] + " is not north");
        for (long[] c : gate.cells()) assertTrue(plan.isGate(c[0], c[1]));
    }

    @Test
    void cornersAreOnTheOutline() {
        var plan = EnclosurePlan.plan(List.of(CENTRE, EAST_HOUSE), List.of(), 3, 0, 3);
        assertTrue(plan.corners().size() >= 3 && plan.corners().size() <= 4);
        assertTrue(plan.gates().isEmpty());
    }
}
