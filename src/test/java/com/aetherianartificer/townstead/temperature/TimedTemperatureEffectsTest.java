package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TimedTemperatureEffectsTest {
    @Test
    void expiryRestoresTheWeakerFoodRatherThanErasingIt() {
        var entries = TimedTemperatureEffects.add(List.of(), "soup", 0.2f, 200, 1, 0);
        entries = TimedTemperatureEffects.add(entries, "hot_soup", 0.4f, 40, 1, 0);
        assertEquals(0.4f, TimedTemperatureEffects.offset(entries, 39));
        assertEquals(0.2f, TimedTemperatureEffects.offset(entries, 40));
        assertEquals(0f, TimedTemperatureEffects.offset(entries, 200));
    }

    @Test
    void duplicatesStopAtTheConfiguredLimitAndRefreshOneServing() {
        var entries = TimedTemperatureEffects.add(List.of(), "tea", 0.3f, 100, 2, 0);
        entries = TimedTemperatureEffects.add(entries, "tea", 0.3f, 100, 2, 10);
        entries = TimedTemperatureEffects.add(entries, "tea", 0.3f, 100, 2, 20);
        assertEquals(2, entries.size());
        assertEquals(0.6f, TimedTemperatureEffects.offset(entries, 100), 0.0001f);
        assertEquals(0.3f, TimedTemperatureEffects.offset(entries, 110));
        assertEquals(0f, TimedTemperatureEffects.offset(entries, 120));
    }

    @Test
    void oppositeSignsCoexistAndExpiredEffectsDoNotStack() {
        var entries = TimedTemperatureEffects.add(List.of(), "tea", 0.3f, 20, 1, 0);
        entries = TimedTemperatureEffects.add(entries, "juice", -0.2f, 40, 1, 0);
        assertEquals(0.1f, TimedTemperatureEffects.offset(entries, 0), 0.0001f);
        entries = TimedTemperatureEffects.add(entries, "tea", 0.3f, 20, 1, 20);
        assertEquals(2, entries.size());
    }

    @Test
    void coldSweatBodyPointsAreNotWorldCelsiusUnits() {
        assertEquals(-0.3f, TimedTemperatureEffects.coldSweatBodyDegrees(-20), 0.0001f);
        assertEquals(1.5f, TimedTemperatureEffects.coldSweatBodyDegrees(100));
        assertEquals(0f, TimedTemperatureEffects.coldSweatBodyDegrees(Double.NaN));
        assertEquals(0f, TimedTemperatureEffects.coldSweatBodyDegrees(Double.MAX_VALUE));
    }

    @Test
    void invalidEffectsDoNotLeavePersistentEntries() {
        assertTrue(TimedTemperatureEffects.add(List.of(), "bad", Float.NaN, 100, 1, 0).isEmpty());
        assertTrue(TimedTemperatureEffects.add(List.of(), "instant", 1, 0, 1, 0).isEmpty());
    }

    @Test
    void thermalPotionsIncludeResistanceButNotUnrelatedOrThrowableEffects() {
        assertTrue(ThermalConsumables.thermalPotion("legendarysurvivaloverhaul", "temperature_immunity"));
        assertTrue(ThermalConsumables.thermalPotion("toughasnails", "internal_chill"));
        assertFalse(ThermalConsumables.thermalPotion("minecraft", "poison"));
        assertFalse(ThermalConsumables.thermalPotion("unrelated", "hot_food"));
    }
}
