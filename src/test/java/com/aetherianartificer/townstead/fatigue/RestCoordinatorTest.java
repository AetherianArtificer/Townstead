package com.aetherianartificer.townstead.fatigue;

import net.minecraft.world.entity.schedule.Activity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestCoordinatorTest {

    @Test
    void scheduledRestDefersBedSeekingToMca() {
        RestDecision decision = RestCoordinator.decide(new RestContext(
                true,
                Activity.REST,
                0,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false
        ));

        assertEquals(SleepReason.SCHEDULED_REST, decision.reason());
        assertEquals(SleepBlockReason.NONE, decision.blockReason());
        assertFalse(decision.shouldSeekBed());
        assertFalse(decision.shouldOverrideScheduleToRest());
    }

    @Test
    void drowsyVillagerOverridesScheduleOutsideRest() {
        RestDecision decision = RestCoordinator.decide(new RestContext(
                true,
                Activity.WORK,
                FatigueData.DROWSY_THRESHOLD,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                false
        ));

        assertEquals(SleepReason.FATIGUE_REST, decision.reason());
        assertTrue(decision.shouldSeekBed());
        assertTrue(decision.shouldOverrideScheduleToRest());
    }

    @Test
    void combatThreatBlocksBedSeeking() {
        RestDecision decision = RestCoordinator.decide(new RestContext(
                true,
                Activity.REST,
                FatigueData.DROWSY_THRESHOLD,
                false,
                false,
                false,
                true,
                true,
                true,
                false,
                false
        ));

        assertEquals(SleepBlockReason.COMBAT_THREAT, decision.blockReason());
        assertFalse(decision.shouldSeekBed());
        assertFalse(decision.shouldOverrideScheduleToRest());
    }

    @Test
    void sleepingVillagerWithInvalidBedShouldWake() {
        RestDecision decision = RestCoordinator.decide(new RestContext(
                true,
                Activity.REST,
                0,
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                false
        ));

        assertEquals(SleepBlockReason.INVALID_SLEEPING_BED, decision.blockReason());
        assertTrue(decision.shouldWake());
    }

    @Test
    void fatigueDisabledStillAllowsScheduledRest() {
        RestDecision decision = RestCoordinator.decide(new RestContext(
                false,
                Activity.REST,
                0,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false
        ));

        assertEquals(SleepReason.SCHEDULED_REST, decision.reason());
        assertFalse(decision.shouldSeekBed());
    }

    @Test
    void guardWithoutHomeHoldsPositionDuringScheduledRest() {
        RestDecision decision = RestCoordinator.decide(new RestContext(
                true,
                Activity.REST,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false
        ));

        assertEquals(SleepReason.SCHEDULED_REST, decision.reason());
        assertTrue(decision.shouldHoldGuardAtRest());
    }

    @Test
    void fatigueOverrideSleepingStaysInBedUntilFullyRecovered() {
        // Fatigue at 1 — should NOT wake during override (not fully recovered)
        RestDecision stillFatigued = RestCoordinator.decide(new RestContext(
                true,
                Activity.WORK,
                1,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                true  // restOverrideActive
        ));
        assertFalse(stillFatigued.shouldWake(), "should stay in bed until fully recovered during fatigue override");

        // Fatigue at 0 — should wake during override
        RestDecision rested = RestCoordinator.decide(new RestContext(
                true,
                Activity.WORK,
                0,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                true  // restOverrideActive
        ));
        assertTrue(rested.shouldWake(), "should wake once fully recovered during fatigue override");
    }

    @Test
    void normalScheduledSleepWakesAtDrowsyThreshold() {
        // Fatigue just below DROWSY (11) during normal scheduled rest on WORK schedule — should wake
        RestDecision decision = RestCoordinator.decide(new RestContext(
                true,
                Activity.WORK,
                FatigueData.DROWSY_THRESHOLD - 1,
                false,
                true,
                false,
                false,
                true,
                true,
                false,
                false  // NOT override
        ));
        assertTrue(decision.shouldWake(), "should wake once below drowsy during normal sleep");
    }
}
