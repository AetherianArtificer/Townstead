package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalComfortTest {
    private static float load(float ambient, float wet, boolean immersed, float activity, ThermalProtection gear) {
        return ThermalComfort.load(ambient, wet, immersed, activity, gear, ThermalProfile.DEFAULT);
    }
    @Test void chillyAirDoesNotMeanDangerousCoreTemperature() {
        assertEquals(TemperatureData.Tier.CHILLY, ThermalComfort.tier(load(15, 0, false, 0, ThermalProtection.NONE)));
        assertEquals(TemperatureData.Tier.COMFORTABLE, TemperatureData.tier(370, ThermalProfile.DEFAULT));
        assertFalse(ThermalComfort.needsBreak(-5, 30, 30));
    }
    @Test void movementHelpsButDoesNotEraseColdWaterExposure() {
        assertTrue(load(15, 0, false, 2, ThermalProtection.NONE) > load(15, 0, false, 0, ThermalProtection.NONE));
        float swimming = load(12, 1, true, 2, ThermalProtection.NONE);
        assertTrue(ThermalComfort.needsBreak(swimming, 0, 30));
    }
    @Test void wetClothingLosesProtectionAndStaysWetOnLand() {
        var coat = new ThermalProtection(0.5f, 3, 0, 0);
        assertTrue(load(12, 1, false, 0, coat) < load(12, 0, false, 0, coat));
        var dry = ThermalComfort.update(new ThermalComfort.State(1, -10, 30), -10, false, false, 20, 1, 90, 30);
        assertTrue(dry.wetness() > 0.98f);
    }
    @Test void immersionActsImmediatelyAndRainSoaksGradually() {
        var initial = new ThermalComfort.State(0, 0, 0);
        var water = ThermalComfort.update(initial, -20, true, false, 12, 0.05f, 90, 30);
        assertEquals(1, water.wetness());
        assertEquals(-20, water.load());
        var rain = ThermalComfort.update(initial, -5, false, true, 15, 1, 90, 30);
        assertTrue(rain.wetness() > 0 && rain.wetness() < 1);
    }
    @Test void sustainedColdBuildsABreakAndShelterClearsStrain() {
        var state = new ThermalComfort.State(0, -8, 0);
        for (int second = 0; second < 30; second++) state = ThermalComfort.update(state, -8, false, false, 12, 1, 90, 30);
        assertTrue(ThermalComfort.needsBreak(state.load(), state.strainSeconds(), 30));
        for (int second = 0; second < 40; second++) state = ThermalComfort.update(state, 0, false, false, 20, 1, 90, 30);
        assertEquals(0, state.strainSeconds());
        assertEquals(TemperatureData.Tier.COMFORTABLE, ThermalComfort.tier(state.load()));
    }
    @Test void dryingIsGradualAndSlowerInColdAir() {
        var wet = new ThermalComfort.State(1, 0, 0);
        var warm = ThermalComfort.update(wet, 0, false, false, 20, 45, 90, 30);
        var cold = ThermalComfort.update(wet, 0, false, false, 5, 45, 90, 30);
        assertEquals(0.5f, warm.wetness(), 0.001f);
        assertTrue(cold.wetness() > warm.wetness());
        assertEquals(0, ThermalComfort.update(warm, 0, false, false, 20, 45, 90, 30).wetness());
    }
    @Test void coldImmunityPreventsWetColdDiscomfort() {
        var immune = new ThermalProfile(37, 0.5f, 0, 1, 0, false, false, false);
        assertEquals(0, ThermalComfort.load(0, 1, true, 0, ThermalProtection.NONE, immune));
    }
    @Test void insulationDoesNotHeatColdAirPastComfortButCanRetainHeat() {
        var coat = new ThermalProtection(1, 0, 0, 0);
        assertEquals(0, load(15, 0, false, 0, coat));
        assertTrue(load(30, 0, false, 0, coat) > load(30, 0, false, 0, ThermalProtection.NONE));
    }
}
