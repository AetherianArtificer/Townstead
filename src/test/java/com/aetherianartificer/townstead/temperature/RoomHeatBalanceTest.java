package com.aetherianartificer.townstead.temperature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomHeatBalanceTest {
    @Test void stoveSurroundedByCabinetsStillDeliversItsOutputToTheRoom() {
        assertEquals(1, RoomHeatBalance.sourceShare(1, 1));
        assertEquals(1, RoomHeatBalance.sourceShare(4, 4));
        double power = 2 * 8.25 * 1.5 * RoomHeatBalance.sourceShare(1, 1);
        assertTrue(RoomHeatBalance.advance(-1, 84, power, 0.6, -1, 60) > 10,
                "Two lit stoves in the small winter kitchen should visibly warm it within a minute");
    }
    @Test void sourceSharedBetweenRoomsOrOutsideIsNotDuplicated() {
        assertEquals(1, RoomHeatBalance.sourceShare(1, 3) + RoomHeatBalance.sourceShare(2, 3), 1e-10);
        assertEquals(0.5, RoomHeatBalance.sourceShare(1, 2));
        assertEquals(0, RoomHeatBalance.sourceShare(0, 2));
    }
    @Test void radiantExposureFallsWithDistanceForHeatingAndCooling() {
        assertEquals(10, RoomHeatBalance.localExposure(10, 0));
        assertTrue(RoomHeatBalance.localExposure(10, 1) > RoomHeatBalance.localExposure(10, 4));
        assertEquals(0, RoomHeatBalance.localExposure(10, 36));
        for (double distance = 0; distance <= 7; distance += 0.05) {
            assertEquals(-RoomHeatBalance.localExposure(10, distance * distance),
                    RoomHeatBalance.localExposure(-10, distance * distance), 1e-10);
        }
    }
    @Test void heaterStabilizesWhenLossEqualsInput() {
        double t = 10;
        for (int i=0; i<10000; i++) t = RoomHeatBalance.advance(t, 128, 12, 1, 10, 1);
        assertEquals(22, t, 0.0001);
    }
    @Test void moreHeatersRaiseTheEquilibrium() {
        double one = RoomHeatBalance.advance(10, 128, 12, 1, 10, 10000);
        double two = RoomHeatBalance.advance(10, 128, 24, 1, 10, 10000);
        assertEquals(2*(one-10), two-10, 0.0001);
    }
    @Test void insulationSlowsCoolingButDoesNotCreateWarmth() {
        assertTrue(RoomHeatBalance.advance(30,128,0,0.2,10,60) > RoomHeatBalance.advance(30,128,0,1,10,60));
        assertEquals(10, RoomHeatBalance.advance(10,128,0,0.2,10,600), 0.0001);
        assertEquals(10, RoomHeatBalance.advance(30,128,0,0.2,10,100000), 0.0001);
    }
    @Test void openDoorSpeedsExchangeInBothDirections() {
        assertTrue(RoomHeatBalance.advance(30,128,0,4,10,60) < RoomHeatBalance.advance(30,128,0,0.2,10,60));
        assertTrue(RoomHeatBalance.advance(10,128,0,4,30,60) > RoomHeatBalance.advance(10,128,0,0.2,30,60));
    }
    @Test void largerVolumeRespondsMoreSlowly() {
        assertTrue(RoomHeatBalance.advance(10,256,12,1,10,30) < RoomHeatBalance.advance(10,64,12,1,10,30));
    }
    @Test void coolingSourcesAndWarmSummerAirBalance() {
        assertEquals(20, RoomHeatBalance.advance(30,128,-10,1,30,10000), 0.0001);
    }
    @Test void updateFrequencyDoesNotChangeTheResultOrOvershoot() {
        double t=10;
        for (int i=0;i<100;i++) t=RoomHeatBalance.advance(t,128,12,1,10,1);
        assertEquals(t,RoomHeatBalance.advance(10,128,12,1,10,100),1e-10);
        assertTrue(RoomHeatBalance.advance(10,128,12,1,10,10000)<=22);
    }
    @Test void sealedRegionAccumulatesHeatWithoutInventingLeakage() {
        assertEquals(22,RoomHeatBalance.advance(10,100,20,0,0,60),1e-10);
        assertThrows(IllegalArgumentException.class, () -> RoomHeatBalance.advance(10,0,1,1,10,1));
        assertThrows(IllegalArgumentException.class, () -> RoomHeatBalance.advance(10,100,Double.NaN,1,10,1));
    }
}
