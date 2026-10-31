package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.root.gene.types.ThermalToleranceGeneType;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalSensitivityGeneTest {
    private static ThermalToleranceGeneType.Instance parse(String json) {
        return (ThermalToleranceGeneType.Instance) new ThermalToleranceGeneType()
                .parse(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void sensitivityOnlyGeneLeavesTheRestUnset() {
        var gene = parse("{\"type\":\"pheno:thermal_tolerance\",\"cold\":0.5,\"heat\":0.5}");
        assertTrue(Float.isNaN(gene.neutral()));
        assertTrue(Float.isNaN(gene.band()));
        assertEquals(0.5f, gene.cold());
    }

    @Test
    void eitherRestFieldSetsBoth() {
        var gene = parse("{\"type\":\"pheno:thermal_tolerance\",\"neutral\":30}");
        assertEquals(30f, gene.neutral());
        assertEquals(TemperatureData.DEFAULT_BAND, gene.band());
    }
}
