package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColdSweatInsulatorsTest {

    @Test
    void unitsMapOntoTheSharedResistanceScale() {
        ThermalProtection sweater = ColdSweatInsulators.fromUnits(1.5, 0);
        assertEquals(0.5f, sweater.coldResistance(), 1e-6f);
        assertEquals(0f, sweater.heatResistance());
        assertEquals(0f, sweater.offset());

        ThermalProtection shirt = ColdSweatInsulators.fromUnits(1, 1);
        assertEquals(shirt.coldResistance(), shirt.heatResistance());
    }
}
