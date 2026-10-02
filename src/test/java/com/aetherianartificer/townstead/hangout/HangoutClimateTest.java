package com.aetherianartificer.townstead.hangout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HangoutClimateTest {
    @Test void coldVisitorsPreferWarmerSeatsButNotAnOverheatedRoom() {
        assertTrue(HangoutPreferences.thermalWeight(-8, 0) > HangoutPreferences.thermalWeight(-8, -8));
        assertTrue(HangoutPreferences.thermalWeight(-8, 0) > HangoutPreferences.thermalWeight(-8, 15));
    }
    @Test void hotVisitorsPreferCoolerSeatsWithoutTreatingFreezingAsComfortable() {
        assertTrue(HangoutPreferences.thermalWeight(12, 2) > HangoutPreferences.thermalWeight(12, 12));
        assertTrue(HangoutPreferences.thermalWeight(12, 2) > HangoutPreferences.thermalWeight(12, -15));
    }
    @Test void climatePreferencesRemainBoundedAndNeutralWhenUnknown() {
        assertEquals(1, HangoutPreferences.thermalWeight(0, 0));
        assertEquals(1, HangoutPreferences.thermalWeight(-8, Float.NaN));
        assertEquals(0.15, HangoutPreferences.thermalWeight(0, 100));
        assertEquals(5, HangoutPreferences.thermalWeight(-100, 0));
    }
    @Test void partialPathsMustReachTheRightFloorAndMakeProgress() {
        assertFalse(HangoutPreferences.usefulPartialApproach(400, 16, -4));
        assertFalse(HangoutPreferences.usefulPartialApproach(400, 350, 0));
        assertTrue(HangoutPreferences.usefulPartialApproach(400, 16, 0));
    }
}
