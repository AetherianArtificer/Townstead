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
                true
        ));

        assertEquals(SleepReason.SCHEDULED_REST, decision.reason());
        assertTrue(decision.shouldHoldGuardAtRest());
    }
}
