package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ThermometerClientTest {
    @Test
    void displaysConvertedValueAndMatchingUnit() {
        assertEquals("24.0 °C", ThermometerClient.format(24, false));
        assertEquals("75.2 °F", ThermometerClient.format(24, true));
        assertEquals("32.0 °F", ThermometerClient.format(0, true));
        assertEquals("-40.0 °F", ThermometerClient.format(-40, true));
    }
}
