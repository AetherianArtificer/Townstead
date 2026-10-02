package com.aetherianartificer.townstead.compat.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EclipticClimateTest {
    @Test
    void warmSnowClimateIsFreezingEvenWhenTheAuthoredTemperatureCurveIsWarm() {
        float ceiling = EclipticClimate.snowCeiling(19f);
        assertEquals(0f, EclipticClimate.reconcile(19f, ceiling));
    }

    @Test
    void colderClimateAndNightTemperaturesArePreserved() {
        assertEquals(-8f, EclipticClimate.reconcile(19f, EclipticClimate.snowCeiling(-8f)));
        assertEquals(-15f, EclipticClimate.reconcile(-15f, EclipticClimate.snowCeiling(-8f)));
    }

    @Test
    void unavailableClimateLeavesTheBackendAlone() {
        assertEquals(19f, EclipticClimate.reconcile(19f, Float.NaN));
        assertTrue(Float.isNaN(EclipticClimate.reconcile(Float.NaN, 0f)));
        assertEquals(0f, EclipticClimate.snowCeiling(Float.NaN));
    }
}

