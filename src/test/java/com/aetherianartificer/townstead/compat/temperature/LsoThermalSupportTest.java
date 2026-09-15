package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.temperature.ThermalProtection;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LsoThermalSupportTest {
    enum Group { FOOD, DRINK, UNKNOWN }
    public static class Consumable {
        public Group group;
        public int temperatureLevel, duration;
        Consumable(Group group, int level, int duration) { this.group = group; this.temperatureLevel = level; this.duration = duration; }
    }

    @Test
    void liveConsumableDataPreservesGroupSignStrengthAndDuration() throws Exception {
        var effects = LsoConsumables.decode(List.of(new Consumable(Group.DRINK, -2, 3600), new Consumable(Group.FOOD, 3, 1200)));
        assertEquals("legendarysurvivaloverhaul:cold_drink", effects.get(0).effect());
        assertEquals("legendarysurvivaloverhaul:hot_drink", effects.get(0).opposite());
        assertEquals(1, effects.get(0).amplifier());
        assertEquals(3600, effects.get(0).duration());
        assertEquals("legendarysurvivaloverhaul:hot_food", effects.get(1).effect());
        assertEquals(2, effects.get(1).amplifier());
    }

    @Test
    void zeroLevelAndInvalidDataDoNotDereferenceMissingNativeEffects() throws Exception {
        assertTrue(LsoConsumables.decode(List.of(new Consumable(Group.FOOD, 0, 1200),
                new Consumable(Group.DRINK, 1, 0), new Consumable(Group.UNKNOWN, 1, 1200),
                new Consumable(Group.FOOD, Integer.MIN_VALUE, 1200))).isEmpty());
    }

    @Test
    void effectsChangeInternalTemperatureWithoutChangingTheRoom() {
        assertEquals(3f, LsoEntityCompat.effect("hot_food", 2).ambientOffset());
        assertEquals(-2f, LsoEntityCompat.effect("cold_drink", 1).ambientOffset());
        assertEquals(0f, LsoEntityCompat.effect("heat_resistance", 1).ambientOffset());
        assertEquals(28f, LsoEntityCompat.effect("heat_resistance", 1).protection().protectAmbient(30, 20));
        assertEquals(20f, LsoEntityCompat.effect("cold_immunity", 0).protection().protectAmbient(-10, 20));
    }

    @Test
    void coatOnlyEquipmentIsRecognizedAndOrdinaryUnknownItemsAllowFallback() {
        var coat = new ThermalProtection(0, 2, 0, 0);
        assertEquals(coat, LsoTemperatureBridge.combineProtection(false, ThermalProtection.NONE, coat));
        assertNull(LsoTemperatureBridge.combineProtection(false, ThermalProtection.NONE, ThermalProtection.NONE));
        assertEquals(ThermalProtection.NONE, LsoTemperatureBridge.combineProtection(true, ThermalProtection.NONE, ThermalProtection.NONE));
    }

    @Test
    void coatAndGarmentAreAddedOnceWithDirectionalResistance() {
        var total = LsoTemperatureBridge.combineProtection(true, new ThermalProtection(0.2f, 3, 0, 0),
                new ThermalProtection(0, 2, 0, 1));
        assertEquals(new ThermalProtection(0.2f, 5, 0, 1), total);
        assertEquals(6f, total.protectAmbient(0, 20));
        assertEquals(29f, total.protectAmbient(30, 20));
    }
}
