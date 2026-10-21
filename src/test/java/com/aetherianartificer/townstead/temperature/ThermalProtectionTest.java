package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalProtectionTest {
    @Test
    void lsoLeatherWithZeroFlatTemperatureStillProtectsAgainstCold() {
        ThermalProtection leather = new ThermalProtection(0, 4, 0, 0);
        assertEquals(1f, leather.coldClo(), 1e-6f);
        assertEquals(12.5f, leather.protectAmbient(5, 20), 1e-4f);
        assertEquals(30f, leather.protectAmbient(30, 20));
        assertEquals(20f, leather.protectAmbient(20, 20));
        assertEquals(0f, leather.offset());
    }

    @Test
    void insulationStopsAShareOfTheGapAndNeverOvershootsNeutral() {
        ThermalProtection coat = new ThermalProtection(0, 4, 0, 0);
        // The same coat helps more the colder it gets.
        assertEquals(5f, coat.protectAmbient(0, 10) - 0, 1e-4f);
        assertEquals(20f, coat.protectAmbient(-30, 10) + 30, 1e-4f);
        assertTrue(coat.protectAmbient(-40, 20) < 20);
        ThermalProtection immune = new ThermalProtection(0, Float.MAX_VALUE, 0, 0);
        assertEquals(20f, immune.protectAmbient(-40, 20), 1e-4f);
    }

    @Test
    void armorAndCuriosResistanceAccumulateBeforeApplyingToAmbient() {
        ThermalProtection gear = new ThermalProtection(0.5f, 1, 0, 0)
                .plus(new ThermalProtection(0, 2, 0, 1));
        assertEquals(1f, gear.coldClo(), 1e-6f);
        assertEquals(0.25f, gear.heatClo(), 1e-6f);
        assertEquals(12.5f, gear.protectAmbient(5, 20), 1e-4f);
        assertEquals(28f, gear.protectAmbient(30, 20), 1e-4f);
        assertEquals(0.5f, gear.offset());
    }
}
