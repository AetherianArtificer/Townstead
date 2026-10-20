package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColdSweatEntityCompatTest {

    @Test
    void resistanceEffectsBlockOneSideEach() {
        ThermalProtection ice = ColdSweatEntityCompat.effect("cold_sweat", "ice_resistance", true, true);
        assertEquals(10000f, ice.coldResistance());
        assertEquals(0f, ice.heatResistance());

        ThermalProtection fire = ColdSweatEntityCompat.effect("minecraft", "fire_resistance", true, true);
        assertEquals(0f, fire.coldResistance());
        assertEquals(10000f, fire.heatResistance());
    }

    @Test
    void coldSweatConfigCanTurnEitherOff() {
        assertEquals(ThermalProtection.NONE, ColdSweatEntityCompat.effect("cold_sweat", "ice_resistance", false, true));
        assertEquals(ThermalProtection.NONE, ColdSweatEntityCompat.effect("minecraft", "fire_resistance", true, false));
    }

    @Test
    void unrelatedEffectsGiveNothing() {
        assertEquals(ThermalProtection.NONE, ColdSweatEntityCompat.effect("toughasnails", "ice_resistance", true, true));
        assertEquals(ThermalProtection.NONE, ColdSweatEntityCompat.effect("minecraft", "poison", true, true));
    }
}
