package com.aetherianartificer.townstead.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WetDryTest {
    private static final String[] SERENE = {
            "MID_WET", "LATE_WET", "LATE_WET", "EARLY_DRY", "EARLY_DRY", "MID_DRY",
            "MID_DRY", "LATE_DRY", "LATE_DRY", "EARLY_WET", "EARLY_WET", "MID_WET"};

    @Test
    void sereneWetMatchesItsTropicalSeasonPerSubSeason() {
        for (int sub = 0; sub < 12; sub++) {
            var phase = WetDry.serene((sub + 0.5) / 12.0);
            assertEquals(SERENE[sub].endsWith("WET"), phase.wet(), "sub-season " + sub);
        }
    }

    @Test
    void sereneIsWettestAtTheTurnOfTheYearAndDriestAtMidYear() {
        assertEquals(1f, WetDry.serene(0).wetness(), 1e-5);
        assertEquals(0f, WetDry.serene(0.5).wetness(), 1e-5);
        assertEquals(1f, WetDry.serene(1.0).wetness(), 1e-5);
    }

    @Test
    void monsoonIsWetFromEarlySummerToFirstFrost() {
        float[] rain = new float[24];
        float[] chances = {0.3f, 0.5f, 0.7f, 0.8f, 0.95f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.25f};
        System.arraycopy(chances, 0, rain, 6, chances.length);
        assertFalse(WetDry.monsoon(5.5 / 24, rain).wet());
        assertTrue(WetDry.monsoon(6.5 / 24, rain).wet());
        assertTrue(WetDry.monsoon(17.5 / 24, rain).wet());
        assertFalse(WetDry.monsoon(18.5 / 24, rain).wet());
        assertEquals(1f, WetDry.monsoon(10.0 / 24, rain).wetness(), 1e-5);
        assertEquals(0f, WetDry.monsoon(0.0, rain).wetness(), 1e-5);
        float rising = WetDry.monsoon(9.5 / 24, rain).wetness();
        assertTrue(rising > 0.8f / 0.95f && rising < 1f, "wetness blends between terms");
    }

    @Test
    void monsoonWithNoRainIsDry() {
        var phase = WetDry.monsoon(0.4, new float[24]);
        assertFalse(phase.wet());
        assertEquals(0f, phase.wetness());
    }

    @Test
    void offsetRunsFromDryToWet() {
        assertEquals(3f, WetDry.offset(3f, -1f, 0f), 1e-6);
        assertEquals(-1f, WetDry.offset(3f, -1f, 1f), 1e-6);
        assertEquals(1f, WetDry.offset(3f, -1f, 0.5f), 1e-6);
    }
}
