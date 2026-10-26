package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.root.gene.types.HydrationGeneType;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerThirstRateTest {
    @Test
    void halfRateOwesBackHalfOfEachLoss() {
        double owed = PlayerThirstSuppression.owed(20, 19, 0.5f, 0);
        assertEquals(0.5, owed, 1e-9);
        assertEquals(1.0, PlayerThirstSuppression.owed(19, 18, 0.5f, owed), 1e-9);
    }

    @Test
    void drinkingOwesNothing() {
        assertEquals(0.25, PlayerThirstSuppression.owed(10, 14, 0.5f, 0.25), 1e-9);
    }

    @Test
    void rateAloneParsesAndZeroMeansNoThirst() {
        var half = (HydrationGeneType.Instance) new HydrationGeneType().parse(JsonParser.parseString(
                "{\"type\":\"townstead_roots:hydration\",\"rate\":0.5}").getAsJsonObject());
        assertNotNull(half);
        assertEquals(0.5f, half.rate());
        assertFalse(half.disablesThirst());
        var zero = (HydrationGeneType.Instance) new HydrationGeneType().parse(JsonParser.parseString(
                "{\"type\":\"townstead_roots:hydration\",\"rate\":0}").getAsJsonObject());
        assertTrue(zero.disablesThirst());
        var water = (HydrationGeneType.Instance) new HydrationGeneType().parse(JsonParser.parseString(
                "{\"type\":\"townstead_roots:hydration\",\"liquid\":\"water\"}").getAsJsonObject());
        assertEquals(1f, water.rate());
    }
}
