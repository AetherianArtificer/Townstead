package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.TestBootstrap;
import com.aetherianartificer.townstead.calendar.Season;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroundClimateTest {
    private static final float[] QUARTERS = {0f, 6f, -2f, -12f};

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.ensure();
    }

    @Test
    void geothermalStartsBelowItsHeightAndSteepensInDeepslate() {
        assertEquals(0f, GroundClimate.geothermal(70, 48, 0.15f, 0, 0.2f));
        assertEquals(0f, GroundClimate.geothermal(48, 48, 0.15f, 0, 0.2f));
        assertEquals(7.2f, GroundClimate.geothermal(0, 48, 0.15f, 0, 0.2f), 1e-4);
        assertEquals(20f, GroundClimate.geothermal(-64, 48, 0.15f, 0, 0.2f), 1e-4);
    }

    @Test
    void openAirFadesAcrossTheSurfaceLayer() {
        assertEquals(1f, GroundClimate.surfaceWeight(0, 3f));
        assertEquals(0.5f, GroundClimate.surfaceWeight(1.5, 3f), 1e-6);
        assertEquals(0f, GroundClimate.surfaceWeight(3, 3f));
        assertEquals(0f, GroundClimate.surfaceWeight(12, 3f));
        assertEquals(0f, GroundClimate.surfaceWeight(1, 0f));
    }

    @Test
    void deepGroundHoldsTheYearlyMean() {
        assertEquals(-2.0, GroundClimate.mean(QUARTERS), 1e-9);
        for (double year = 0; year < 1; year += 0.05)
            assertEquals(0.0, GroundClimate.anomaly(QUARTERS, year, 20, 3), 0.05);
    }

    @Test
    void surfaceSeriesFollowsTheSeasonSteps() {
        // Mid-quarter, away from the step edges, the truncated series sits near each offset.
        for (int q = 0; q < 4; q++) {
            double surface = GroundClimate.mean(QUARTERS) + GroundClimate.anomaly(QUARTERS, q / 4.0 + 0.125, 0, 3);
            assertEquals(QUARTERS[q], surface, 1.5);
        }
    }

    @Test
    void swingShrinksAndArrivesLaterWithDepth() {
        double shallowPeak = Double.NEGATIVE_INFINITY, deepPeak = Double.NEGATIVE_INFINITY;
        double shallowAt = 0, deepAt = 0;
        for (double year = 0; year < 1; year += 0.001) {
            double shallow = GroundClimate.anomaly(QUARTERS, year, 1, 3);
            double deep = GroundClimate.anomaly(QUARTERS, year, 6, 3);
            if (shallow > shallowPeak) { shallowPeak = shallow; shallowAt = year; }
            if (deep > deepPeak) { deepPeak = deep; deepAt = year; }
        }
        assertTrue(deepPeak > 0 && deepPeak < shallowPeak * 0.5, "deep swing is damped");
        assertTrue(Math.floorMod((int) Math.round((deepAt - shallowAt) * 1000), 1000) > 100, "deep peak is delayed");
    }

    @Test
    void quartersAnchorOnTodaysSeason() {
        var settings = TemperatureSettings.parse(com.google.gson.JsonParser.parseString(
                "{\"season_offsets\":{\"spring\":0,\"summer\":6,\"autumn\":-2,\"winter\":-12}}").getAsJsonObject());
        float[] quarters = GroundClimate.quarterOffsets(settings, Season.SUMMER, 2);
        assertArrayEquals(new float[]{-12f, 0f, 6f, -2f}, quarters);
    }

    @Test
    void zoneWaveMatchesTheSurfaceCurveAndFadesWithDepth() {
        for (double year = 0; year < 1; year += 0.1) {
            assertEquals(com.aetherianartificer.townstead.climate.ClimateModelTestAccess.seasonal(8f, 0.083f, year),
                    GroundClimate.zoneWave(8f, 0.083f, year, 0, 3), 1e-4);
            assertEquals(0.0, GroundClimate.zoneWave(8f, 0.083f, year, 30, 3), 1e-3);
        }
        assertEquals(8 * Math.exp(-1), GroundClimate.zoneWave(8f, 0.083f, 0.458 + 1 / (2 * Math.PI), 3, 3), 1e-3);
    }
}
