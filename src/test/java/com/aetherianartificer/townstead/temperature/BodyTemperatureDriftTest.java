package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyTemperatureDriftTest {
    @Test
    void frequentUpdatesPreserveExistingLongTermRate() {
        BodyTemperatureDrift drift = new BodyTemperatureDrift();
        int body = 370;
        for (int ticks = 0; ticks < 500; ticks += TemperatureData.ACCUMULATION_INTERVAL) {
            body = drift.step(body, 35, TemperatureData.RATE);
        }
        assertEquals(365, body);
    }

    @Test
    void smallChangesAccumulateInsteadOfRoundingAwayForever() {
        BodyTemperatureDrift drift = new BodyTemperatureDrift();
        int body = 370;
        for (int i = 0; i < 20; i++) body = drift.step(body, 36.9, 0.25);
        assertEquals(369, body);
    }

    @Test
    void prolongedColdAndWetExposureConvergesAndWarmthAllowsRecovery() {
        BodyTemperatureDrift dry = new BodyTemperatureDrift(), wet = new BodyTemperatureDrift();
        int dryBody = 370, wetBody = 370;
        // Default idle human at 12 C: 37 - 0.8 ambient - 0.2 idle; wet adds -0.8.
        for (int i = 0; i < 30; i++) {
            dryBody = dry.step(dryBody, 36, 0.25);
            wetBody = wet.step(wetBody, 35.2, 0.25);
        }
        assertEquals(360, dryBody);
        assertEquals(352, wetBody);
        for (int i = 0; i < 30; i++) wetBody = wet.step(wetBody, 37, 0.25);
        assertEquals(370, wetBody);
    }

    @Test
    void externalBodyEditsReplaceAccumulatedState() {
        BodyTemperatureDrift drift = new BodyTemperatureDrift();
        drift.step(370, 35, 0.25);
        assertEquals(385, drift.step(390, 37, 0.25));
    }
}
