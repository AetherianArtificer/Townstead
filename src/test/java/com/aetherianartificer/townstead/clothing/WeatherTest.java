package com.aetherianartificer.townstead.clothing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherTest {

    @Test
    void cutsMatchTheAmbientTiers() {
        assertEquals(Weather.Kind.COLD, Weather.kind(-5f));
        assertEquals(Weather.Kind.COLD, Weather.kind(Weather.COLD_CELSIUS));
        assertEquals(Weather.Kind.MILD, Weather.kind(Weather.COLD_CELSIUS + 0.5f));
        assertEquals(Weather.Kind.MILD, Weather.kind(20f));
        assertEquals(Weather.Kind.MILD, Weather.kind(Weather.HOT_CELSIUS - 0.5f));
        assertEquals(Weather.Kind.HOT, Weather.kind(Weather.HOT_CELSIUS));
        assertEquals(Weather.Kind.HOT, Weather.kind(40f));
    }
}
