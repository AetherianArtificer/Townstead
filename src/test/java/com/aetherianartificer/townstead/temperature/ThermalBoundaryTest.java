package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalBoundaryTest {
    private ThermalBoundary.Layer solid(double g) { return new ThermalBoundary.Layer(true, true, false, g, 0); }
    private ThermalBoundary.Layer air(double t, boolean same) { return new ThermalBoundary.Layer(true, false, same, 0, t); }

    @Test void wallThicknessAddsResistanceInSeries() {
        var one = ThermalBoundary.trace(i -> i < 1 ? solid(1.4) : air(0, false), 30, 0);
        var two = ThermalBoundary.trace(i -> i < 2 ? solid(1.4) : air(0, false), 30, 0);
        assertEquals(one.conductance() / 2, two.conductance(), 1e-10);
    }
    @Test void insulationWithinAWallStillMatters() {
        var wall = ThermalBoundary.trace(i -> i == 0 ? solid(1.4) : i == 1 ? solid(0.08) : air(0, false), 30, 0);
        assertTrue(wall.conductance() < 0.08);
    }
    @Test void internalFurnitureIsNotAnOutdoorHeatSink() {
        var wall = ThermalBoundary.trace(i -> i < 2 ? solid(1.4) : air(20, true), 30, -10);
        assertEquals(0, wall.conductance());
    }
    @Test void otherVolumesAndOpeningsKeepTheirOwnReservoirs() {
        var wall = ThermalBoundary.trace(i -> i == 0 ? solid(1.4) : air(22, false), 30, -10);
        assertEquals(22, wall.reservoir());
        var opening = ThermalBoundary.trace(i -> air(-10, false), 30, -10);
        assertEquals(30, opening.conductance());
    }
    @Test void unloadedAndDeepBoundariesDoNotTriggerUnboundedReads() {
        int[] reads = {0};
        var unknown = ThermalBoundary.trace(i -> { reads[0]++; return new ThermalBoundary.Layer(false, false, false, 0, 0); }, 30, 0);
        assertEquals(0, unknown.conductance());
        assertEquals(1, reads[0]);
        reads[0] = 0;
        var deep = ThermalBoundary.trace(i -> { reads[0]++; return solid(1.4); }, 30, 0);
        assertEquals(ThermalBoundary.MAX_DEPTH, reads[0]);
        assertEquals(1.4 / ThermalBoundary.MAX_DEPTH, deep.conductance(), 1e-10);
    }
}
