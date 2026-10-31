package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerFeelsLikeTest {
    private static final ComfortRange COLD_SWEAT = new ComfortRange(10, 38);

    @Test void aHumanBodyKeepsItsReadingWhenItsRangeIsTheMods() {
        var human = ThermalProfile.DEFAULT;
        for (float feels : new float[]{-20, 5, 10, 24, 38, 45})
            assertEquals(feels, PlayerFeelsLike.toModScale(feels, human, COLD_SWEAT, COLD_SWEAT), 1e-4);
    }

    @Test void aTundraRootReachesTheModsColdEdgeEightDegreesLower() {
        var tundra = new ThermalProfile(37, 0.5f, 1, 1, 0, false, false, false, false, -8, null);
        var body = COLD_SWEAT.offset(-8);
        assertEquals(10, PlayerFeelsLike.toModScale(2, tundra, COLD_SWEAT, body), 1e-4);
        assertEquals(5, PlayerFeelsLike.toModScale(-3, tundra, COLD_SWEAT, body), 1e-4);
    }

    @Test void sensitivityScalesOnlyWhatLiesPastTheRange() {
        var hardy = new ThermalProfile(37, 0.5f, 0.5f, 2, 0, false, false, false);
        assertEquals(7.5f, PlayerFeelsLike.toModScale(5, hardy, COLD_SWEAT, COLD_SWEAT), 1e-4);
        assertEquals(42, PlayerFeelsLike.toModScale(40, hardy, COLD_SWEAT, COLD_SWEAT), 1e-4);
        assertEquals(20, PlayerFeelsLike.toModScale(20, hardy, COLD_SWEAT, COLD_SWEAT), 1e-4);
    }
}
