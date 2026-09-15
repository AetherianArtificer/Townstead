package com.aetherianartificer.townstead.block;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ThermometerBandTest {
    @Test
    void ambientBoundariesAndExtremes() {
        assertEquals(ThermometerBand.FREEZING, ThermometerBand.at(-50));
        assertEquals(ThermometerBand.COLD, ThermometerBand.at(0));
        assertEquals(ThermometerBand.COLD, ThermometerBand.at(14.99f));
        assertEquals(ThermometerBand.MILD, ThermometerBand.at(15));
        assertEquals(ThermometerBand.WARM, ThermometerBand.at(25));
        assertEquals(ThermometerBand.HOT, ThermometerBand.at(35));
        assertEquals(ThermometerBand.HOT, ThermometerBand.at(100));
    }

    @Test
    void smallFluctuationsDoNotFlickerButLargeChangesCatchUpImmediately() {
        assertEquals(ThermometerBand.MILD, ThermometerBand.MILD.update(25.4f));
        assertEquals(ThermometerBand.WARM, ThermometerBand.MILD.update(25.5f));
        assertEquals(ThermometerBand.WARM, ThermometerBand.WARM.update(24.5f));
        assertEquals(ThermometerBand.MILD, ThermometerBand.WARM.update(24.49f));
        assertEquals(ThermometerBand.HOT, ThermometerBand.FREEZING.update(40));
        assertEquals(ThermometerBand.FREEZING, ThermometerBand.HOT.update(-10));
        assertEquals(ThermometerBand.COLD, ThermometerBand.COLD.update(Float.NaN));
    }
}
