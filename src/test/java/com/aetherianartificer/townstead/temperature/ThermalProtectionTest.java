package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThermalProtectionTest {
    @Test
    void lsoLeatherWithZeroFlatTemperatureStillProtectsAgainstCold() {
        ThermalProtection leather = new ThermalProtection(0, 1.5f, 0, 0);
        assertEquals(6.5f, leather.protectAmbient(5, 20));
        assertEquals(30f, leather.protectAmbient(30, 20));
        assertEquals(20f, leather.protectAmbient(20, 20));
        assertEquals(0f, leather.offset());
    }

    @Test
    void resistanceCannotOvershootNeutralOrBecomeUnconditionalWarmth() {
        ThermalProtection protection = new ThermalProtection(0, 10, 10, 5);
        assertEquals(20f, protection.protectAmbient(19, 20));
        assertEquals(20f, protection.protectAmbient(21, 20));
        assertEquals(25f, protection.protectAmbient(40, 20));
    }

    @Test
    void armorAndCuriosResistanceAccumulateBeforeApplyingToAmbient() {
        ThermalProtection gear = new ThermalProtection(0.5f, 1, 0, 0)
                .plus(new ThermalProtection(0, 2, 0, 1));
        assertEquals(9f, gear.protectAmbient(5, 20));
        assertEquals(29f, gear.protectAmbient(30, 20));
        assertEquals(0.5f, gear.offset());
    }
}
