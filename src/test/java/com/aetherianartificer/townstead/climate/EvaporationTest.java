package com.aetherianartificer.townstead.climate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EvaporationTest {
    private static final String[] ZONES = {"alpine", "arid", "continental", "maritime", "mediterranean", "polar",
            "semi_arid", "subarctic", "subterranean", "temperate", "tropical_humid"};

    private static ClimateZone zone(String name) {
        var stream = EvaporationTest.class.getResourceAsStream("/data/townstead/climate/zone/" + name + ".json");
        assertNotNull(stream, name);
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        return ClimateZone.parse(ResourceLocation.tryParse("townstead:" + name), json);
    }

    @Test
    void wetBulbMatchesReferenceValues() {
        assertEquals(13.7, Evaporation.wetBulb(20, 0.5), 0.1);
        assertEquals(19.3, Evaporation.wetBulb(35, 0.2), 0.2);
        assertEquals(20, Evaporation.wetBulb(20, 0.99), 0.3, "saturated air cannot cool by evaporation");
    }

    @Test
    void evaporationNeverWarmsAndSaturatedAirIsNotThirsty() {
        assertEquals(0, Evaporation.deficit(25, 1), 1e-9);
        assertTrue(Evaporation.deficit(25, 0.2) > Evaporation.deficit(25, 0.8));
        assertTrue(Evaporation.cooling(-10f, 0.5f, 1f) >= 0);
        assertEquals(0f, Evaporation.cooling(20, 1f, 0.4f), 0.15f);
    }

    @Test
    void daylightIsZeroAtNightAndFullAtNoon() {
        assertEquals(1f, ClimateModel.daylight(6000, 12000), 1e-5);
        assertEquals(0f, ClimateModel.daylight(18000, 12000), 1e-5);
        assertEquals(0f, ClimateModel.daylight(0, 12000), 1e-5);
        assertTrue(ClimateModel.daylight(6000, 16000) > ClimateModel.daylight(2000, 16000));
    }

    @Test
    void desertRainCoolsMoreThanJungleRain() {
        float desert = ClimateModel.rainCooling(35f, 0.2f, 1f, 0.4f, 0.2f);
        float jungle = ClimateModel.rainCooling(30f, 0.9f, 1f, 0.4f, 0.2f);
        assertTrue(desert > 5f, "a desert storm chills hard: " + desert);
        assertTrue(jungle < 1f, "jungle rain barely cools: " + jungle);
    }

    @Test
    void rainCoolsMoreByDayThanByNight() {
        assertTrue(ClimateModel.rainCooling(20f, 0.6f, 1f, 0.4f, 0.2f)
                > ClimateModel.rainCooling(20f, 0.6f, 0f, 0.4f, 0.2f));
    }

    @Test
    void aRainyNoonIsCoolerThanAClearNoon() {
        ClimateZone temperate = zone("temperate");
        float mean = 20f, half = temperate.diurnalRange();
        float clear = mean + half;
        float cloudy = mean + half * 0.4f;
        float rainy = cloudy - ClimateModel.rainCooling(cloudy, ClimateModel.humidity(temperate.humidity(), 1f), 1f, 0.4f, 0.2f);
        assertTrue(rainy < clear - 3f, "clear " + clear + ", rainy " + rainy);
    }

    @Test
    void aRainyNightIsNeverColderThanAClearNightInAnyShippedZone() {
        for (String name : ZONES) {
            ClimateZone zone = zone(name);
            if (zone.diurnalRange() <= 0) continue;
            for (float mean : new float[]{13 - zone.seasonalSwing(), 13, 13 + zone.seasonalSwing()}) {
                float clear = mean - zone.diurnalRange();
                float cloudy = mean - zone.diurnalRange() * 0.4f;
                float rainy = cloudy - ClimateModel.rainCooling(cloudy, ClimateModel.humidity(zone.humidity(), -1f), 0f, 0.4f, 0.2f);
                assertTrue(rainy >= clear, name + " at " + mean + ": clear " + clear + ", rainy " + rainy);
            }
        }
    }
}
