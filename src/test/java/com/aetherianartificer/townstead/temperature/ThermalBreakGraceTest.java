package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aetherianartificer.townstead.temperature.TemperatureData.Tier.*;

class ThermalBreakGraceTest {
    @Test void coldAndHotEachGetAFullMinuteAfterEntry() {
        for (var tier : new TemperatureData.Tier[]{COLD, HOT}) {
            var grace = new ThermalBreakGrace();
            grace.update(tier, 1);
            assertFalse(grace.allowsBreak(tier));
            for (int tick = 0; tick < 1199; tick++) grace.update(tier, .05);
            assertFalse(grace.allowsBreak(tier));
            // Match the ticker's float timestep as well as whole seconds.
            grace.update(tier, .05f);
            assertTrue(grace.allowsBreak(tier));
        }
    }

    @Test void earlierChillyOrWarmExposureDoesNotUseUpGrace() {
        var grace = new ThermalBreakGrace();
        for (int second = 0; second < 600; second++) grace.update(CHILLY, 1);
        grace.update(COLD, 1);
        assertFalse(grace.allowsBreak(COLD));
        for (int second = 0; second < 600; second++) grace.update(WARM, 1);
        grace.update(HOT, 1);
        assertFalse(grace.allowsBreak(HOT));
    }

    @Test void recoveringOrSwitchingSidesStartsANewGracePeriod() {
        for (var interruption : new TemperatureData.Tier[]{COMFORTABLE, CHILLY, HOT}) {
            var grace = new ThermalBreakGrace();
            grace.update(COLD, 0);
            grace.update(COLD, 60);
            assertTrue(grace.allowsBreak(COLD));
            grace.update(interruption, 1);
            grace.update(COLD, 1);
            assertFalse(grace.allowsBreak(COLD));
            grace.update(COLD, 59);
            assertFalse(grace.allowsBreak(COLD));
            grace.update(COLD, 1);
            assertTrue(grace.allowsBreak(COLD));
        }
    }

    @Test void crisesBypassGraceAndMildTiersNeverStartBreaks() {
        var grace = new ThermalBreakGrace();
        assertTrue(grace.allowsBreak(FREEZING));
        assertTrue(grace.allowsBreak(SWELTERING));
        for (var tier : new TemperatureData.Tier[]{CHILLY, COMFORTABLE, WARM}) {
            grace.update(tier, 600);
            assertFalse(grace.allowsBreak(tier));
        }
    }

    @Test void speciesBandsDetermineWhenGraceStarts() {
        var ecto = new ThermalProfile(30, .5f, 1, 1, 0, false, true, false);
        var tier = TemperatureData.tier(284, ecto);
        assertEquals(COLD, tier);
        var grace = new ThermalBreakGrace();
        grace.update(tier, 0);
        assertFalse(grace.allowsBreak(tier));
        grace.update(tier, 60);
        assertTrue(grace.allowsBreak(tier));
        assertTrue(grace.allowsBreak(TemperatureData.tier(269, ecto)));
    }
}
