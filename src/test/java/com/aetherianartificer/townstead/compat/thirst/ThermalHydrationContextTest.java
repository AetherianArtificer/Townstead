package com.aetherianartificer.townstead.compat.thirst;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalHydrationContextTest {
    @Test void bodyAdapterIsSignedStressNotCelsius() {
        assertEquals(0, ThermalHydrationContext.signedStress(37, 37, 0.5f));
        assertEquals(-100, ThermalHydrationContext.signedStress(34, 37, 0.5f));
        assertEquals(100, ThermalHydrationContext.signedStress(40, 37, 0.5f));
        assertEquals(100, ThermalHydrationContext.signedStress(50, 37, 0.5f));
    }
    @Test void stressRespectsTheVillagersOwnNeutralAndTolerance() {
        assertEquals(0, ThermalHydrationContext.signedStress(32, 32, 1));
        assertEquals(50, ThermalHydrationContext.signedStress(35, 32, 1));
    }
    @Test void takenAndReclaimedUseOneTemperatureInputAndTheirHarshnessRule() {
        assertEquals(0.5f, ThermalHydrationContext.thirstModifier(0, 1, 2, 0.5f));
        assertEquals(2, ThermalHydrationContext.thirstModifier(1, 1, 2, 0.5f));
        assertEquals(0, ThermalHydrationContext.thirstModifier(-1, 1, 2, 0.5f));
    }
    @Test void lsoHeatThirstOnlyAppliesAtHeatStrokeWhenEnabled() {
        assertEquals(0, ThermalHydrationContext.heatExhaustion(false, true, 5));
        assertEquals(0, ThermalHydrationContext.heatExhaustion(true, false, 5));
        assertEquals(5, ThermalHydrationContext.heatExhaustion(true, true, 5) * 50, 0.0001f);
        assertEquals(0, ThermalHydrationContext.heatExhaustion(true, true, Double.NaN));
    }
}
