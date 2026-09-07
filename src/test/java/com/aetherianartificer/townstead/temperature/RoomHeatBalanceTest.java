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
    @Test void standingBesideAStoveFeelsHeatBeforeTheRoomWarms() {
        assertEquals(5.5,RoomHeatBalance.localExposure(8.25,1),1e-10);
        assertEquals(0,RoomHeatBalance.localExposure(8.25,36));
        assertTrue(RoomHeatBalance.localExposure(8.25,1)>RoomHeatBalance.localExposure(8.25,4));
        assertTrue(RoomHeatBalance.localExposure(-8,1)<0);
        assertEquals(-1,RoomHeatBalance.advance(-1,84,24.75,0.6,-1,0));
    }
    @Test void stoveWarmthReachesAcrossRoomWithoutIncreasingBesideStoveExposure() {
        assertEquals(8.25, RoomHeatBalance.localExposure(8.25,0), 1e-10);
        assertEquals(5.5, RoomHeatBalance.localExposure(8.25,1), 1e-10);
        assertTrue(RoomHeatBalance.localExposure(8.25,9) > 3);
        assertTrue(RoomHeatBalance.localExposure(8.25,25) > 1);
        double previous = Double.POSITIVE_INFINITY;
        for (double distance = 0; distance <= 7; distance += 0.05) {
            double effect = RoomHeatBalance.localExposure(8.25,distance * distance);
            assertTrue(effect >= 0 && effect <= previous);
            previous = effect;
        }
        assertEquals(0, RoomHeatBalance.localExposure(8.25,49));
        assertEquals(0, RoomHeatBalance.localExposure(-8,9), 1e-10);
    }
    @Test void coldRoomKeepsImmediateStoveReliefWhileWarmKitchenGetsLessExtraHeat() {
        assertEquals(11, RoomHeatBalance.warmExposure(-1,11));
        assertEquals(11, RoomHeatBalance.warmExposure(20,11));
        assertTrue(30 + RoomHeatBalance.warmExposure(30,11) < 36);
        assertTrue(RoomHeatBalance.warmExposure(40,11) > 0);
        assertEquals(0, RoomHeatBalance.warmExposure(30,0));
    }
    @Test void hotterRoomStillFeelsHotterEvenAtMaximumLocalExposure() {
        double previous = Double.NEGATIVE_INFINITY;
        for (double air = 0; air < 60; air += 0.1) {
            double feels = air + RoomHeatBalance.warmExposure(air,18);
            assertTrue(feels > previous);
            previous = feels;
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
