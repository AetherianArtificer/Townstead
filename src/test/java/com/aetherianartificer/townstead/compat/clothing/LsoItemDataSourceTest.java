package com.aetherianartificer.townstead.compat.clothing;

import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.aetherianartificer.townstead.temperature.ThermalProtection;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LsoItemDataSourceTest {

    @Test
    void readsLsoFieldsOnTheBridgeScale() {
        ThermalProtection sweater = LsoItemDataSource.parse(JsonParser.parseString(
                "{ \"cold_resistance\": 0.5, \"heat_resistance\": 0.0, \"temperature\": 2.0, \"thermal_resistance\": 0.0 }")
                .getAsJsonObject());
        assertEquals(new ThermalProtection(2f * TemperatureData.AMBIENT_PULL_PER_DEGREE, 0.5f, 0f, 0f), sweater);
    }

    @Test
    void allZeroIsNoOpinion() {
        assertNull(LsoItemDataSource.parse(JsonParser.parseString(
                "{ \"cold_resistance\": 0, \"heat_resistance\": 0, \"temperature\": 0, \"thermal_resistance\": 0 }")
                .getAsJsonObject()));
        assertNull(LsoItemDataSource.parse(null));
    }
}
