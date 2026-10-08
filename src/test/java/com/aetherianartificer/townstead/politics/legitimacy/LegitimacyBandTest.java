package com.aetherianartificer.townstead.politics.legitimacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegitimacyBandTest {
    @Test
    void bandsSplitTheScaleIntoFifths() {
        assertEquals("resented", LegitimacyService.band(0));
        assertEquals("resented", LegitimacyService.band(19.9));
        assertEquals("uneasy", LegitimacyService.band(20));
        assertEquals("tolerated", LegitimacyService.band(50));
        assertEquals("accepted", LegitimacyService.band(79.9));
        assertEquals("beloved", LegitimacyService.band(80));
        assertEquals("beloved", LegitimacyService.band(100));
    }
}
