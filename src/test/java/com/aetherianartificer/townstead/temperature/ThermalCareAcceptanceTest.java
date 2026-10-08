package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.needs.NeedEffectProjection;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Personal exposure and decision regressions for the reported clothing/fire/kitchen failures. */
class ThermalCareAcceptanceTest {
    private static final ThermalProfile HUMAN = ThermalProfile.DEFAULT;
    private static ThermalExposure at(float air, float wet, ThermalProtection protection) {
        return new ThermalExposure(air, wet, false, false, 0, protection, HUMAN, false);
    }

    @Test void aBetterButStillFreezingLocationDoesNotPromiseRecovery() {
        var better = at(0, 0, ThermalProtection.NONE).forecast(35.5f, 600);
        assertFalse(better.recovers());
        assertEquals(Double.NEGATIVE_INFINITY, ThermalReliefTargets.recoveryScore(35.5f, better, HUMAN, 2));
    }
    @Test void fireApproachChoosesRecoverableWarmthWithoutDangerousOvershoot() {
        var safe = at(28, 0, ThermalProtection.NONE).forecast(36, 600);
        var burning = at(55, 0, ThermalProtection.NONE).forecast(36, 600);
        assertTrue(Double.isFinite(ThermalReliefTargets.recoveryScore(36, safe, HUMAN, 3)));
        assertEquals(Double.NEGATIVE_INFINITY, ThermalReliefTargets.recoveryScore(36, burning, HUMAN, 1));
    }
    @Test void personalProtectionMakesTheSameShelterUsefulForOneVillager() {
        var bare = at(0, 0, ThermalProtection.NONE);
        var dressed = bare.withProtection(new ThermalProtection(1.5f, 0, 0, 0));
        assertFalse(bare.forecast(36, 600).recovers());
        assertTrue(dressed.forecast(36, 600).recovers());
        assertTrue(dressed.outfitCost() < bare.outfitCost());
    }
    @Test void enteringShelterDoesNotInstantlyDryClothing() {
        var coat = new ThermalProtection(.8f, 2, 0, 0);
        var wet = at(18, 1, coat);
        var dry = at(18, 0, coat);
        assertTrue(wet.target() < dry.target());
        assertTrue(wet.forecast(36, 30).body() < dry.forecast(36, 30).body());
        assertTrue(wet.forecast(36, 600).recovers());
    }
    @Test void winterCoatCanBeRemovedToImproveKitchenExposure() {
        var bare = at(32, 0, ThermalProtection.NONE);
        var coat = bare.withProtection(new ThermalProtection(1.5f, 0, 0, 0));
        assertTrue(coat.outfitCost() > bare.outfitCost());
        assertTrue(coat.forecast(37.5f, 120).body() > bare.forecast(37.5f, 120).body());
    }
    @Test void doorwayDoesNotCauseADiscontinuousCoatHeatPenalty() {
        var coat = new ThermalProtection(1.5f, 0, 0, 0);
        assertTrue(Math.abs(at(20.01f, 0, coat).load() - at(19.99f, 0, coat).load()) < .05f);
    }
    @Test void mildDiscomfortDoesNotForceABreakButColdAndHotDo() {
        for (float body : new float[]{35.5f, 36.1f, 36.4f, 37f, 37.6f, 38.5f})
            assertFalse(BodyHeat.needsBreak(body, HUMAN), "No forced break at " + body);
        for (float body : new float[]{34f, 35.4f, 38.6f, 40f})
            assertTrue(BodyHeat.needsBreak(body, HUMAN), "Relief needed at " + body);
    }
    @Test void ectothermsStillBenefitFromClothingAndTheirOwnNeutral() {
        var ecto = new ThermalProfile(30, .5f, 1, 1, 0, false, true, false);
        var bare = new ThermalExposure(5, 0, false, false, 0, ThermalProtection.NONE, ecto, false);
        assertTrue(bare.withProtection(new ThermalProtection(1.5f, 0, 0, 0)).target() > bare.target());
        assertEquals(30, BodyHeat.target(0, 6, ecto, false));
    }
    @Test void thermalServingMustHelpWithoutInstantlyOvershooting() {
        var cold = at(5, 0, ThermalProtection.NONE);
        assertTrue(new ThermalBenefit(.4f, 0, ThermalProtection.NONE, 0).score(cold, 36) > 0);
        assertEquals(0, new ThermalBenefit(3, 0, ThermalProtection.NONE, 0).score(cold, 36));
        assertEquals(0, new ThermalBenefit(-1, 0, ThermalProtection.NONE, 0).score(cold, 36));
    }
    @Test void shortInfluenceCannotClaimTheBenefitOfAPermanentHeater() {
        var cold = at(0, 0, ThermalProtection.NONE);
        float shortBenefit = new ThermalBenefit(0, 15, ThermalProtection.NONE, 20).score(cold, 36);
        float longBenefit = new ThermalBenefit(0, 15, ThermalProtection.NONE, 2400).score(cold, 36);
        assertTrue(shortBenefit < longBenefit * .1f);
        assertEquals(0, new ThermalBenefit(0, 15, ThermalProtection.NONE, 0).score(cold, 36));
    }
    @Test void phenoDurationProjectsAsInfluenceInsteadOfAnInstantCoreJump() {
        var timed = NeedEffectProjection.project(JsonParser.parseString("{\"type\":\"pheno:warm\",\"amount\":4,\"duration\":1200}"));
        assertEquals(0, timed.warmthTenths());
        assertEquals(40, timed.influenceTenths());
        assertEquals(1200, timed.influenceTicks());
        var instant = NeedEffectProjection.project(JsonParser.parseString("{\"type\":\"pheno:cool\",\"amount\":0.4}"));
        assertEquals(-4, instant.warmthTenths());
    }
    @Test void repeatedPhenoInfluenceIsReplacementNotStackedWarmth() {
        var both = NeedEffectProjection.project(JsonParser.parseString("[{\"type\":\"pheno:warm\",\"amount\":4,\"duration\":1200},{\"type\":\"pheno:warm\",\"amount\":2,\"duration\":600}]"));
        assertEquals(20, both.influenceTenths());
        assertEquals(600, both.influenceTicks());
    }
    @Test void denseKitchenRadiationHasDiminishingReturnsWithoutHidingHeat() {
        double single = RoomHeatBalance.localExposure(8, 1);
        double row = RoomHeatBalance.combinedExposure(single * 8, single);
        assertTrue(row > single);
        assertTrue(row <= single * 1.25);
        assertEquals(single, RoomHeatBalance.combinedExposure(single, single));
    }
    @Test void diagnosticFlagsPreserveTheExistingWetReliefAndBodyTiers() {
        int original = TemperatureSyncPayload.flags(true, true, 2) | (1 << 5);
        int flags = original | ThermalStatus.flags(36, 37, "no_effective_relief");
        assertEquals(original, flags & 255);
        assertEquals("warming", ThermalStatus.trend(flags));
        assertEquals("no_effective_relief", ThermalStatus.reason(flags));
    }
}
