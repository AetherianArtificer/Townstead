package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Gameplay acceptance for the villager body, at the shipped defaults, one step per second. */
class BodyHeatTest {
    private static final ThermalProfile HUMAN = ThermalProfile.DEFAULT;
    private static final float ZONE = 6f, DRIFT = 480f, RECOVERY = 120f;

    private static float load(float ambient) {
        return ThermalComfort.load(ambient, 0, false, 0, ThermalProtection.NONE, HUMAN);
    }

    /** Seconds until the tier leaves {@code from}, or -1 within the limit. */
    private static int secondsUntilTierChanges(int bodyTenths, float ambient, int limit) {
        BodyTemperatureDrift drift = new BodyTemperatureDrift();
        TemperatureData.Tier start = TemperatureData.tier(bodyTenths, HUMAN);
        int body = bodyTenths;
        for (int second = 1; second <= limit; second++) {
            body = step(drift, body, ambient);
            if (TemperatureData.tier(body, HUMAN) != start) return second;
        }
        return -1;
    }

    private static int step(BodyTemperatureDrift drift, int body, float ambient) {
        float target = BodyHeat.target(load(ambient), ZONE, HUMAN, false);
        return drift.step(body, target, BodyHeat.rate(TemperatureData.celsius(body), target, HUMAN.neutral(), 1, DRIFT, RECOVERY));
    }

    private static int secondsToRecover(int bodyTenths, float ambient, int limit) {
        BodyTemperatureDrift drift = new BodyTemperatureDrift();
        int body = bodyTenths;
        for (int second = 1; second <= limit; second++) {
            body = step(drift, body, ambient);
            if (BodyHeat.recovered(body, HUMAN)) return second;
        }
        return -1;
    }

    @Test void comfortZoneLeavesMildWeatherAlone() {
        assertEquals(0, BodyHeat.excess(load(14), ZONE));
        assertEquals(0, BodyHeat.excess(load(26), ZONE));
        assertEquals(-1, secondsUntilTierChanges(370, 10, 3600), "A cool day never chills an idle villager");
        assertEquals(-1, secondsUntilTierChanges(370, 30, 3600), "A warm kitchen never overheats one");
    }

    @Test void winterNightChillsOverMinutesNotSeconds() {
        int seconds = secondsUntilTierChanges(370, -5, 3600);
        assertTrue(seconds >= 90 && seconds <= 240, "Chilly after " + seconds + " s at -5 C");
    }

    @Test void extremeColdTakesTenMinutesToBecomeACrisis() {
        BodyTemperatureDrift drift = new BodyTemperatureDrift();
        int body = 370;
        int second = 0;
        while (!TemperatureData.tier(body, HUMAN).isCrisis() && second < 3600) {
            body = step(drift, body, -25);
            second++;
        }
        assertTrue(second >= 600, "Freezing after " + second + " s at -25 C");
    }

    @Test void walkingPastAFireOrThroughAColdDoorwayChangesNothing() {
        BodyTemperatureDrift drift = new BodyTemperatureDrift();
        int body = 370;
        for (int second = 0; second < 30; second++) body = step(drift, body, 45);
        assertEquals(TemperatureData.Tier.COMFORTABLE, TemperatureData.tier(body, HUMAN));
        for (int second = 0; second < 30; second++) body = step(drift, body, -15);
        assertEquals(TemperatureData.Tier.COMFORTABLE, TemperatureData.tier(body, HUMAN));
    }

    @Test void aFireRewarmsAColdVillagerInAboutAMinuteOrTwo() {
        int cold = 353;
        int atFire = secondsToRecover(cold, 30, 1200);
        int indoors = secondsToRecover(cold, 18, 1200);
        assertTrue(atFire >= 45 && atFire <= 150, "Recovered by a fire after " + atFire + " s");
        assertTrue(indoors > atFire, "A merely mild room is slower than a fire");
        assertTrue(indoors <= 300, "Recovered indoors after " + indoors + " s");
    }

    @Test void recoveryIsFasterThanDriftAndDirectionAware() {
        double away = BodyHeat.rate(37f, 36f, 37f, 1, DRIFT, RECOVERY);
        double back = BodyHeat.rate(36f, 37f, 37f, 1, DRIFT, RECOVERY);
        assertTrue(back > away);
        assertEquals(away, BodyHeat.rate(37f, 38f, 37f, 1, DRIFT, RECOVERY), 1e-12);
    }

    @Test void sleepHalvesColdOnly() {
        assertEquals(HUMAN.neutral() + (BodyHeat.target(-20, ZONE, HUMAN, false) - HUMAN.neutral()) / 2,
                BodyHeat.target(-20, ZONE, HUMAN, true), 1e-6);
        assertEquals(BodyHeat.target(20, ZONE, HUMAN, false), BodyHeat.target(20, ZONE, HUMAN, true));
    }

    @Test void comfortableBodiesDoNotWaitForPerfectRecovery() {
        assertTrue(BodyHeat.recovered(370, HUMAN));
        assertTrue(BodyHeat.recovered(365, HUMAN));
        assertTrue(BodyHeat.recovered(375, HUMAN));
        assertFalse(BodyHeat.recovered(364, HUMAN));
    }

    @Test void comfortableRecoveryYieldsToBedtimeAndTheDaytimeBreakEndsToo() {
        assertTrue(BodyHeat.recovered(365, HUMAN));
        assertTrue(BodyHeat.restTakesPriority(true, TemperatureData.tier(365, HUMAN), false));
        assertTrue(BodyHeat.restTakesPriority(true, TemperatureData.tier(375, HUMAN), false));
    }

    @Test void mildColdAndHeatAllowSleepButDangerousBodiesDoNot() {
        for (int body : new int[] {355, 364, 376, 385}) {
            assertTrue(BodyHeat.restTakesPriority(true, TemperatureData.tier(body, HUMAN), false));
        }
        for (int body : new int[] {339, 354, 386, 401}) {
            assertFalse(BodyHeat.restTakesPriority(true, TemperatureData.tier(body, HUMAN), false));
        }
    }

    @Test void freezingWaterMustBeEscapedEvenAtBedtimeAndWithAComfortableCore() {
        assertFalse(BodyHeat.restTakesPriority(true, TemperatureData.Tier.COMFORTABLE, true));
        assertFalse(BodyHeat.restTakesPriority(false, TemperatureData.Tier.CHILLY, false));
    }

    @Test void bedtimeSafetyUsesTheSpeciesThermalBand() {
        var ecto = new ThermalProfile(30, .5f, 1, 1, 0, false, true, false);
        assertTrue(BodyHeat.restTakesPriority(true, TemperatureData.tier(295, ecto), false));
        assertFalse(BodyHeat.restTakesPriority(true, TemperatureData.tier(280, ecto), false));
    }

    @Test void luceranCanResumeWorkWhileHisMildColdRecovers() {
        assertFalse(BodyHeat.needsBreak(36.1f, HUMAN));
        assertTrue(BodyHeat.reliefComplete(36.1f, 37f, HUMAN));
        assertTrue(BodyHeat.reliefComplete(37.9f, 37f, HUMAN));
        assertFalse(BodyHeat.reliefComplete(36.1f, 35f, HUMAN), "Still cooling in an unsafe place");
        assertFalse(BodyHeat.reliefComplete(36.1f, 38.5f, HUMAN), "Warming toward unsafe heat");
        assertFalse(BodyHeat.reliefComplete(35.9f, 37f, HUMAN), "Needs more recovery first");
    }

    @Test void finishingRecoveryLeavesAFullBandBeforeAnotherBreakCanStart() {
        assertTrue(BodyHeat.reliefComplete(36f, 37f, HUMAN));
        for (int body = 355; body <= 365; body++)
            assertFalse(BodyHeat.needsBreak(TemperatureData.celsius(body), HUMAN));
        assertTrue(BodyHeat.needsBreak(35.4f, HUMAN));
        var ecto = new ThermalProfile(30, .5f, 1, 1, 0, false, true, false);
        assertTrue(BodyHeat.reliefComplete(29.1f, 30, ecto));
        assertFalse(BodyHeat.needsBreak(29.1f, ecto));
        assertTrue(BodyHeat.needsBreak(28.4f, ecto));
    }

    @Test void forecastsUseTheSameReturnToWorkRuleAsTheLiveBreak() {
        var room = new ThermalExposure(20, 0, false, false, 0, ThermalProtection.NONE, HUMAN, false);
        assertEquals(0, room.forecast(36.1f, 600).recoverySeconds());
        assertTrue(room.forecast(35.4f, 600).recoverySeconds() > 0);
        var cold = new ThermalExposure(-5, 0, false, false, 0, ThermalProtection.NONE, HUMAN, false);
        assertFalse(cold.forecast(36.1f, 600).recovers());
    }
}
