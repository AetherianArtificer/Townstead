package com.aetherianartificer.townstead.climate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClimateModelTest {
    @Test
    void seasonsPeakAMonthAfterTheSolstices() {
        assertEquals(8f, ClimateModel.seasonal(8f, 1f / 12f, 0.375 + 1.0 / 12), 1e-4);
        assertEquals(-8f, ClimateModel.seasonal(8f, 1f / 12f, 0.875 + 1.0 / 12), 1e-4);
        assertTrue(ClimateModel.seasonal(8f, 1f / 12f, 0.375) < 8f, "the solstice itself is not the warmest day");
    }

    @Test
    void vanillaDayIsCoolestAtSunriseAndWarmestMidAfternoon() {
        assertEquals(-1f, ClimateModel.diurnalShape(0, 12000), 1e-5);
        assertEquals(1f, ClimateModel.diurnalShape(8000, 12000), 1e-5);
        assertEquals(-1f, ClimateModel.diurnalShape(24000, 12000), 1e-5);
        assertTrue(ClimateModel.diurnalShape(18000, 12000) < 0, "midnight is below the mean");
        assertTrue(ClimateModel.diurnalShape(6000, 12000) > 0, "noon is above the mean");
    }

    @Test
    void longDaysPeakLaterAndShortDaysEarlier() {
        int longPeak = argMax(15000), shortPeak = argMax(9000);
        assertEquals(8500, longPeak, 50);
        assertEquals(7500, shortPeak, 50);
        assertEquals(-1f, ClimateModel.diurnalShape(24000 - 1500, 15000), 1e-5);
    }

    private static int argMax(int dayLength) {
        int best = 0;
        float max = -2;
        for (int t = 0; t < 24000; t += 10) {
            float v = ClimateModel.diurnalShape(t, dayLength);
            if (v > max) { max = v; best = t; }
        }
        return best;
    }

    @Test
    void fallingSnowCapsTheAirBelowFreezingButKeepsColderAir() {
        var falling = new ClimateModel.Snow(true, false, true, false);
        assertEquals(-3f, ClimateModel.applySnow(8f, falling, -3f, 1f, -5f));
        assertEquals(-15f, ClimateModel.applySnow(-15f, falling, -3f, 1f, -5f));
    }

    @Test
    void lyingSnowHoldsTheDayNearFreezingAndChillsClearNights() {
        assertEquals(1f, ClimateModel.applySnow(9f, new ClimateModel.Snow(false, true, true, true), -3f, 1f, -5f));
        assertEquals(-4f, ClimateModel.applySnow(-4f, new ClimateModel.Snow(false, true, true, true), -3f, 1f, -5f));
        assertEquals(-7f, ClimateModel.applySnow(-2f, new ClimateModel.Snow(false, true, false, true), -3f, 1f, -5f));
        assertEquals(-2f, ClimateModel.applySnow(-2f, new ClimateModel.Snow(false, true, false, false), -3f, 1f, -5f));
    }

    @Test
    void noSnowLeavesTheAirAlone() {
        assertEquals(12f, ClimateModel.applySnow(12f, new ClimateModel.Snow(false, false, true, true), -3f, 1f, -5f));
    }

    @Test
    void wetDryZonesAddTheirOffsetOnlyWithAWetDryProvider() {
        var zone = new ClimateZone(net.minecraft.resources.ResourceLocation.tryParse("townstead:semi_arid"),
                ClimateZone.SeasonKind.WET_DRY, 0f, 1f / 12f, 11f, 0.3f, 0f, 3f, -1f);
        var serene = new com.aetherianartificer.townstead.calendar.SeasonClock.State("serene", 0, 96, null);
        var tfc = new com.aetherianartificer.townstead.calendar.SeasonClock.State("tfc", 0, 96, null);
        assertEquals(3f, ClimateModel.zoneSeasonal(zone, serene, 0.5), 1e-4, "mid dry season");
        assertEquals(-1f, ClimateModel.zoneSeasonal(zone, serene, 0.0), 1e-4, "mid wet season");
        assertEquals(0f, ClimateModel.zoneSeasonal(zone, tfc, 0.5), "no wet/dry phase, no season");
    }

    @Test
    void worldsWithoutSeasonsSitOnAMildGrowingSeasonDay() {
        var temperate = new ClimateZone(net.minecraft.resources.ResourceLocation.tryParse("townstead:temperate"),
                ClimateZone.SeasonKind.FOUR_SEASONS, 10f, 1f / 12f, 6f, 0.6f, 0f, 0f, 0f);
        var arid = new ClimateZone(net.minecraft.resources.ResourceLocation.tryParse("townstead:arid"),
                ClimateZone.SeasonKind.NONE, 6f, 1f / 12f, 14f, 0.1f, 0f, 0f, 0f);
        assertEquals(6.5f, ClimateModel.timeless(temperate), 1e-4);
        assertEquals(0f, ClimateModel.timeless(arid));
        assertEquals(0f, ClimateModel.timeless(null));
        var polar = new ClimateZone(net.minecraft.resources.ResourceLocation.tryParse("townstead:polar"),
                ClimateZone.SeasonKind.FOUR_SEASONS, 15f, 1f / 12f, 4f, 0.5f, 0f, 0f, 0f, 0f);
        assertEquals(0f, ClimateModel.timeless(polar), "ice stays at its cold mean");
    }
}
