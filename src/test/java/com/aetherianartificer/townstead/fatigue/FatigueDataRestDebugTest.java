package com.aetherianartificer.townstead.fatigue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FatigueDataRestDebugTest {

    @Test
    void restOverrideRoundTripsReason() {
        CompoundTag tag = new CompoundTag();

        RestDebugData.setRestOverride(tag, true, SleepReason.FATIGUE_REST);

        assertTrue(RestDebugData.isRestOverrideActive(tag));
        assertEquals(SleepReason.FATIGUE_REST, RestDebugData.getRestOverrideReason(tag));
    }

    @Test
    void disablingRestOverrideClearsReason() {
        CompoundTag tag = new CompoundTag();

        RestDebugData.setRestOverride(tag, false, SleepReason.NONE);

        assertFalse(RestDebugData.isRestOverrideActive(tag));
        assertEquals(SleepReason.NONE, RestDebugData.getRestOverrideReason(tag));
    }

    @Test
    void restDebugDefaultsAreStableWhenUnset() {
        CompoundTag tag = new CompoundTag();

        assertEquals(SleepReason.NONE.id(), RestDebugData.getRestDebugReasonId(tag));
        assertEquals(SleepBlockReason.NONE.id(), RestDebugData.getRestDebugBlockId(tag));
        assertEquals(Long.MIN_VALUE, RestDebugData.getRestDebugTargetBed(tag));
    }

    @Test
    void restDebugDecisionWritesReasonAndBlock() {
        CompoundTag tag = new CompoundTag();

        RestDebugData.setRestDebugDecision(tag, SleepReason.SCHEDULED_REST, SleepBlockReason.NO_BED_FOUND, null);

        assertEquals(SleepReason.SCHEDULED_REST.id(), RestDebugData.getRestDebugReasonId(tag));
        assertEquals(SleepBlockReason.NO_BED_FOUND.id(), RestDebugData.getRestDebugBlockId(tag));
    }
}
