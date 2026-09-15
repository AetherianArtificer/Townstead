package com.aetherianartificer.townstead.compat.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TanTemperaturePolicyTest {
    @Test void noCoveragePreservesContinuousRoomTemperature() {
        assertEquals(23.7f, TanTemperaturePolicy.regulate(23.7f, 0, 0, 0));
    }
    @Test void twoFuelsNeutralizeExtremes() {
        assertEquals(20f, TanTemperaturePolicy.regulate(-30, 0, 0, 1));
        assertEquals(20f, TanTemperaturePolicy.regulate(60, 0, 0, 2));
    }
    @Test void oneModeMovesTwoLevelsAndClamps() {
        assertEquals(20f, TanTemperaturePolicy.regulate(-10, 1, 0, 0));
        assertEquals(20f, TanTemperaturePolicy.regulate(40, 0, 1, 0));
        assertEquals(40f, TanTemperaturePolicy.regulate(30, 1, 0, 0));
    }
    @Test void competingRegulatorsUseCountsNotMagnitude() {
        assertEquals(21.3f, TanTemperaturePolicy.regulate(21.3f, 1, 1, 3));
        assertEquals(40f, TanTemperaturePolicy.regulate(20, 2, 1, 3));
    }
    @Test void internalEffectsCannotCreateExtremes() {
        assertEquals(30f, TanTemperaturePolicy.internal(30, true, false));
        assertEquals(5f, TanTemperaturePolicy.internal(5, false, true));
        assertEquals(30f, TanTemperaturePolicy.internal(40, false, true));
        assertEquals(18.4f, TanTemperaturePolicy.internal(18.4f, true, true));
    }
}
