package com.aetherianartificer.townstead.snow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnowCoatingPolicyTest {
    private static final SnowWeather SNOW = new SnowWeather(true, true, true);
    private static final SnowWeather COLD_CLEAR = new SnowWeather(true, true, false);
    private static final SnowWeather THAW = new SnowWeather(true, false, false);

    @Test void snowfallAccumulatesAndClearColdWeatherRetainsButDoesNotCreateCoating() {
        assertTrue(SnowCoatingPolicy.next(false, SNOW, true, false, 0));
        assertTrue(SnowCoatingPolicy.next(true, COLD_CLEAR, true, false, 0));
        assertFalse(SnowCoatingPolicy.next(false, COLD_CLEAR, true, false, 0));
        assertFalse(SnowCoatingPolicy.next(true, THAW, true, false, 0));
    }

    @Test void shelterWaterAndLightPreventAccumulationAndClearExistingSnow() {
        for (boolean coated : new boolean[]{false, true}) {
            assertFalse(SnowCoatingPolicy.next(coated, SNOW, false, false, 0));
            assertFalse(SnowCoatingPolicy.next(coated, SNOW, true, true, 0));
            assertFalse(SnowCoatingPolicy.next(coated, SNOW, true, false, 10));
            assertFalse(SnowCoatingPolicy.next(coated, SnowWeather.DISABLED, true, false, 0));
            assertTrue(SnowCoatingPolicy.next(coated, SNOW, true, false, 9));
        }
    }

    @Test void eclipticAlwaysOwnsRenderingWhenPresent() {
        assertFalse(SnowCoatingPolicy.usesTownsteadCoating(false, false));
        assertTrue(SnowCoatingPolicy.usesTownsteadCoating(true, false));
        assertFalse(SnowCoatingPolicy.usesTownsteadCoating(false, true));
        assertFalse(SnowCoatingPolicy.usesTownsteadCoating(true, true));
    }
}
