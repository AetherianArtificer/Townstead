package com.aetherianartificer.townstead.temperature;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeelsLikeTest {
    private static final ThermalProfile HUMAN = ThermalProfile.DEFAULT;
    private static final ComfortRange RANGE = new ComfortRange(15, 27);

    private static float feels(float air, float humidity, float wetness, ThermalProtection clothing) {
        return ThermalComfort.feelsLike(air, humidity, wetness, false, air, 0, clothing, HUMAN, RANGE);
    }

    @Test void aWinterOutfitMakesFreezingAirComfortable() {
        var winter = new ThermalProtection(1.5f, 0, 0, 0);
        assertTrue(feels(0, 0.5f, 0, ThermalProtection.NONE) < RANGE.low(), "light clothes at 0 C are cold");
        assertTrue(feels(0, 0.5f, 0, winter) >= RANGE.low(), "a full winter outfit is comfortable at 0 C");
        assertTrue(feels(-20, 0.5f, 0, winter) < RANGE.low(), "and not at -20 C");
    }

    @Test void clothingDoesNotHeatColdAirPastTheMiddleOfTheRange() {
        var winter = new ThermalProtection(1.5f, 0, 0, 0);
        assertTrue(feels(18, 0.5f, 0, winter) <= RANGE.mid() + 1e-4);
    }

    @Test void humidHeatFeelsHotterThanDryHeat() {
        float jungle = feels(32, 0.85f, 0, ThermalProtection.NONE);
        float desert = feels(32, 0.2f, 0, ThermalProtection.NONE);
        assertTrue(jungle > desert + 6, "jungle " + jungle + ", desert " + desert);
        assertEquals(10, feels(10, 0.9f, 0, ThermalProtection.NONE), 1e-4, "humidity does not change cool air");
    }

    @Test void pastTheWetBulbLimitNoClothingHelps() {
        var cooling = new ThermalProtection(-1.5f, 0, 0, 0);
        assertTrue(feels(36, 0.95f, 0, cooling) > RANGE.high() + 5);
    }

    @Test void wetSkinChillsMoreInDryAir() {
        float dry = feels(20, 0.2f, 1, ThermalProtection.NONE);
        float humid = feels(20, 0.9f, 1, ThermalProtection.NONE);
        assertTrue(dry < humid, "dry " + dry + ", humid " + humid);
        assertTrue(feels(20, 0.5f, 1, ThermalProtection.NONE) < feels(20, 0.5f, 0, ThermalProtection.NONE));
    }

    @Test void aCoolLakeFeelsFarColderThanTheSameAir() {
        float lake = ThermalComfort.feelsLike(25, 0.5f, 1, true, 15, 0, ThermalProtection.NONE, HUMAN, RANGE);
        assertEquals(-7, lake, 0.01);
        float warm = ThermalComfort.feelsLike(25, 0.5f, 1, true, 33, 0, ThermalProtection.NONE, HUMAN, RANGE);
        assertEquals(20, warm, 0.01);
    }

    @Test void aDesertDriesFasterThanAJungle() {
        assertTrue(ThermalComfort.drying(35, 0.2f) > ThermalComfort.drying(30, 0.9f) * 3);
    }

    @Test void aTundraRootIsComfortableLower() {
        var tundra = new ThermalProfile(37, 0.5f, 1, 1, 0, false, false, false, false, -8, null);
        assertEquals(0, ThermalComfort.load(9, 0, false, 0, ThermalProtection.NONE, tundra));
        assertTrue(ThermalComfort.load(9, 0, false, 0, ThermalProtection.NONE, HUMAN) < 0);
    }

    @Test void theRangeJudgesOnlyWhatLiesPastItsEnds() {
        assertEquals(0, RANGE.excess(20));
        assertEquals(-5, RANGE.excess(10));
        assertEquals(3, RANGE.excess(30));
        assertEquals(new ComfortRange(7, 19), RANGE.offset(-8));
    }

    @Test void acclimationMovesBothEnds() {
        var limits = new ThermalAcclimation.Limits(-4, -2, 2, 4, 1);
        assertEquals(new ComfortRange(11, 25), ThermalAcclimation.shift(RANGE, -1, limits));
        assertEquals(new ComfortRange(17, 31), ThermalAcclimation.shift(RANGE, 1, limits));
        assertEquals(RANGE, ThermalAcclimation.shift(RANGE, 1, ThermalAcclimation.Limits.OFF));
    }

    @Test void rootAcclimationLimitsParse() {
        assertTrue(ThermalAcclimation.Limits.parse(JsonParser.parseString("false")).off());
        var limits = ThermalAcclimation.Limits.parse(JsonParser.parseString(
                "{\"cold_shift_c\":[-14,-8],\"speed\":1.5}"));
        assertEquals(-14, limits.coldMin());
        assertEquals(-8, limits.coldMax());
        assertTrue(Float.isNaN(limits.hotMin()));
        var merged = limits.over(new ThermalAcclimation.Limits(-4, -2, 2, 4, 1));
        assertEquals(2, merged.hotMin());
        assertEquals(1.5f, merged.speed());
    }

    @Test void aSnowBornVillagerStartsColdAdaptedAndAPlainsOneDoesNot() {
        assertEquals(-1, ThermalAcclimation.seed(-10), 1e-6);
        assertEquals(0, ThermalAcclimation.seed(13), 1e-6);
        assertEquals(1, ThermalAcclimation.seed(30), 1e-6);
        var limits = new ThermalAcclimation.Limits(-4, -2, 2, 4, 1);
        var snowy = ThermalAcclimation.shift(RANGE, ThermalAcclimation.seed(-10), limits);
        assertEquals(RANGE.low() - 4, snowy.low(), 1e-6, "comfortable 4 C lower at the cold end");
    }

    @Test void acclimationDriftsAtItsRateAndStopsAtTheTarget() {
        var limits = new ThermalAcclimation.Limits(-4, -2, 2, 4, 1);
        assertEquals(-1, ThermalAcclimation.target(0, RANGE), 1e-6);
        assertEquals(0, ThermalAcclimation.target(RANGE.mid(), RANGE), 1e-6);
        float a = 0;
        for (int minute = 0; minute < 30; minute++) a = ThermalAcclimation.step(a, -1, 1f / 60f, limits, 1);
        assertEquals(-0.5f, a, 1e-4, "half way after half the full time");
        for (int minute = 0; minute < 60; minute++) a = ThermalAcclimation.step(a, -1, 1f / 60f, limits, 1);
        assertEquals(-1, a, 1e-6);
        assertEquals(0.3f, ThermalAcclimation.step(0.3f, -1, 1, ThermalAcclimation.Limits.OFF, 10), 1e-6, "off stays put");
    }

    @Test void standingOnACampfireFeelsHotWhateverTheHumidity() {
        // 21.3 C air at 45 % with a campfire underfoot: 53.3 C of air and radiant heat.
        float humidity = ThermalAir.relative(0.45f, 21.3f, 53.3f);
        float feels = feels(53.3f, humidity, 0, ThermalProtection.NONE);
        assertTrue(feels > 48, "on a campfire: " + feels);
        float wet = feels(53.3f, humidity, 1, ThermalProtection.NONE);
        assertTrue(wet > 38, "even wet: " + wet);
    }
}
